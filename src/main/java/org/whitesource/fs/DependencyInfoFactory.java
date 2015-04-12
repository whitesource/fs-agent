/**
 * Copyright (C) 2014 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs;

import org.apache.commons.io.FileUtils;
import org.apache.rat.Defaults;
import org.apache.rat.Report;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.analysis.util.HeaderMatcherMultiplexer;
import org.apache.rat.api.MetaData;
import org.apache.rat.api.RatException;
import org.apache.rat.report.claim.ClaimStatistic;
import org.apache.rat.walker.FileReportable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.model.CopyrightInfo;
import org.whitesource.agent.api.model.DependencyInfo;

import javax.xml.transform.TransformerConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Factory class for {@link org.whitesource.agent.api.model.DependencyInfo}.
 * @author tom.shapira
 */
public class DependencyInfoFactory {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(DependencyInfoFactory.class);

    private static final String LICENSE_PATTERN = ".*license.*|.*redistribution.*|.*licensing.*|.*redistribute.*";
    private static final String COPYRIGHT_PATTERN = ".*copyright.*|.*\\(c\\).*";

    private static final String PlATFORM_DEPENDENT_TMP_DIRECTORY = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-PlatformDependentFiles";
    private static final String COPYRIGHT = "copyright";
    private static final String COPYRIGHT_SYMBOL = "(c)";
    private static final List<String> COPYRIGHT_TEXTS = Arrays.asList("copyright (c)", "copyright(c)", "(c) copyright", "(c)copyright", COPYRIGHT, COPYRIGHT_SYMBOL);
    private static final String ALL_RIGHTS_RESERVED = "all rights reserved";

    private static final String COPYRIGHT_ALPHA_CHAR_REGEX = ".*[a-zA-Z]+.*";
    private static final String CONTAINS_YEAR_REGEX = ".*(\\d\\d\\d\\d)+.*";

    private static final List<Character> MATH_SYMBOLS = Arrays.asList('+', '-', '=', '<', '>', '*', '/', '%');
    private static final String DEFINE = "define";
    private static final char OPEN_BRACKET = '(';
    private static final char CLOSE_BRACKET = ')';
    private static final char WHITESPACE_CHAR = ' ';

    private static final String WHITESPACE = " ";
    private static final String EMPTY_STRING = "";

    public static final String CRLF = "\r\n";
    public static final String NEW_LINE = "\n";

    private static final int FIRST_LINES_TO_SCAN = 150;

    private static final Map<String, String> commentStartEndMap;
    static {
        commentStartEndMap = new HashMap<String, String>();
        commentStartEndMap.put("/*", "*/");
        commentStartEndMap.put("/**", "*/");
        commentStartEndMap.put("<!--", "-->");
        commentStartEndMap.put("\"\"\"", "\"\"\"");
        commentStartEndMap.put("=begin", "=end");
        commentStartEndMap.put("##", "##");
    }

    /* --- Public methods --- */

    public DependencyInfo createDependencyInfo(File basedir, String fileName) {
        DependencyInfo dependencyInfo = null;
        File dependencyFile = new File(basedir, fileName);
        try {
            String sha1 = ChecksumUtils.calculateSHA1(dependencyFile);
            dependencyInfo = new DependencyInfo(sha1);
            dependencyInfo.setArtifactId(dependencyFile.getName());
            dependencyInfo.setLastModified(new Date(dependencyFile.lastModified()));
            File otherPlatformFile = createOtherPlatformFile(dependencyFile);
            if (otherPlatformFile != null) {
                String otherPlatformSha1 = ChecksumUtils.calculateSHA1(otherPlatformFile);
                dependencyInfo.setOtherPlatformSha1(otherPlatformSha1);
            }
            try {
                dependencyInfo.setSystemPath(dependencyFile.getCanonicalPath());
            } catch (IOException e) {
                dependencyInfo.setSystemPath(dependencyFile.getAbsolutePath());
            }
            deleteFile(otherPlatformFile);

            // calculate sha1 for file header and footer (for partial matching)
            ChecksumUtils.calculateHeaderAndFooterSha1(dependencyFile, dependencyInfo);

            boolean containsLicense = false;
            boolean containsCopyright = false;
            try {
                // only check file headers
                List<String> lines = FileUtils.readLines(dependencyFile);
                for (int i = 0; i < lines.size() && i < FIRST_LINES_TO_SCAN; i++) {
                    String line = lines.get(i).toLowerCase();
                    if (line.matches(COPYRIGHT_PATTERN)) {
                        containsCopyright = true;
                        containsLicense = true;
                        break;
                    } else if (line.matches(LICENSE_PATTERN)) {
                        containsLicense = true;
                        // continue looking for copyrights
                    }
                }
            } catch (IOException e) {
                // do nothing
            }

            // check if file contains one the "license" words before scanning for licenses
            if (containsLicense) {
                try {
                    Set<String> licenses = scanLicenses(dependencyFile);
                    dependencyInfo.getLicenses().addAll(licenses);
                } catch (Exception e) {
                    logger.debug("Error scanning file for license", e);
                }
            }

            // check if file contains the word "copyright" before extracting copyright information
            if (containsCopyright) {
                dependencyInfo.getCopyrights().addAll(extractCopyrights(dependencyFile));
            }
        } catch (IOException e) {
            logger.warn("Failed to create dependency " + fileName + " to dependency list: ", e);
        }
        return dependencyInfo;
    }

    public Set<String> scanLicenses(File file) throws InterruptedException, TransformerConfigurationException, RatException, IOException {
        HeaderMatcherMultiplexer matcherMultiplexer = new HeaderMatcherMultiplexer(Defaults.DEFAULT_MATCHERS);
        ReportConfiguration configuration = new ReportConfiguration();
        configuration.setHeaderMatcher(matcherMultiplexer);

        ClaimStatistic report = Report.report(new StringWriter(), new FileReportable(file), Defaults.getPlainStyleSheet(), configuration);
        Set<String> licenses = report.getLicenseFileNameMap().keySet();
        if (licenses != null) {
            licenses.remove(MetaData.RAT_LICENSE_FAMILY_CATEGORY_VALUE_UNKNOWN);
        }
        return licenses;
    }

    /* --- Private methods --- */

    private Collection<CopyrightInfo> extractCopyrights(File file) {
        Collection<CopyrightInfo> copyrights = new ArrayList<CopyrightInfo>();
        try {
            boolean commentBlock = false;
            Iterator<String> iterator = FileUtils.readLines(file).iterator();
            int lineIndex = 1;
            while (iterator.hasNext()) {
                // trim (duh..)
                String line = iterator.next().trim();

                // check if comment block
                if (!commentBlock) {
                    for (Map.Entry<String, String> entry : commentStartEndMap.entrySet()) {
                        String commentStart = entry.getKey();
                        String commentEnd = entry.getValue();
                        if (line.startsWith(commentStart)) {
                            if (line.contains(commentEnd)) {
                                commentBlock = false;
                                int endIndex = line.indexOf(commentEnd);
                                int commentLength = commentStart.length();
                                if (endIndex >= commentLength) {
                                    line = line.substring(commentLength, endIndex);
                                } else {
                                    line = EMPTY_STRING;
                                }
                            } else {
                                commentBlock = true;
                            }
                            break;
                        } else if (line.contains(commentStart)) {
                            int startIndex = line.indexOf(commentStart);
                            if (line.contains(commentEnd)) {
                                int endIndex = line.indexOf(commentEnd);
                                if (startIndex < endIndex) {
                                    commentBlock = false;
                                    line = line.substring(startIndex, endIndex);
                                }
                            } else {
                                commentBlock = true;
                                line = line.substring(startIndex);
                            }
                            break;
                        }
                    }
                }

                // check for one-line comments
                String lowerCaseLine = line.toLowerCase();
                if ((commentBlock || line.startsWith("//") || line.startsWith("#"))
                        && (lowerCaseLine.contains(COPYRIGHT) || lowerCaseLine.contains(COPYRIGHT_SYMBOL))) {
                    // ignore lines that contain (c) and math signs (+, <, etc.) near it
                    if (lowerCaseLine.contains(COPYRIGHT_SYMBOL) && !isActualCopyrightLine(lowerCaseLine)) {
                        break;
                    }

                    StringBuilder sb = new StringBuilder();
                    line = cleanLine(line);
                    sb.append(line);

                    // check if copyright continues to next line
                    boolean continuedToNextLine = false;
                    if (iterator.hasNext()) {
                        String copyrightOwner = null;
                        lowerCaseLine = line.toLowerCase();
                        for (String copyrightText : COPYRIGHT_TEXTS) {
                            if (lowerCaseLine.startsWith(copyrightText)) {
                                copyrightOwner = line.substring(copyrightText.length()).trim();
                                break;
                            }
                        }

                        if (copyrightOwner != null) {
                            // check if copyright contains an alpha char (not just years)
                            if (!copyrightOwner.matches(COPYRIGHT_ALPHA_CHAR_REGEX)) {
                                // check if line has ending of comment block
                                for (String commentEnd : commentStartEndMap.values()) {
                                    if (line.contains(commentEnd)) {
                                        commentBlock = false;
                                        break;
                                    }
                                }

                                // if still in comment block, read next line
                                if (commentBlock) {
                                    String nextLine = cleanLine(iterator.next());
                                    sb.append(WHITESPACE);
                                    sb.append(nextLine);

                                    continuedToNextLine = true;
                                    line = nextLine;
                                }
                            }
                        }
                    }

                    // remove "all rights reserved" if exists
                    String copyright = sb.toString();
                    String lowercaseCopyright = copyright.toLowerCase();
                    if (lowercaseCopyright.contains(ALL_RIGHTS_RESERVED)) {
                        int startIndex = lowercaseCopyright.indexOf(ALL_RIGHTS_RESERVED);
                        int endIndex = startIndex + ALL_RIGHTS_RESERVED.length();
                        if (endIndex == copyright.length()) {
                            copyright = copyright.substring(0, startIndex).trim();
                        } else {
                            copyright = copyright.substring(0, startIndex).trim() + " " + copyright.substring(endIndex).trim();
                        }
                    }
                    copyrights.add(new CopyrightInfo(copyright, lineIndex));

                    if (continuedToNextLine) {
                        lineIndex++;
                    }
                }

                // check if line has ending of comment block
                for (String commentEnd : commentStartEndMap.values()) {
                    if (line.contains(commentEnd)) {
                        commentBlock = false;
                        break;
                    }
                }
                lineIndex++;
            }
        } catch (FileNotFoundException e) {
            logger.warn("File not found: " + file.getPath());
        } catch (IOException e) {
            logger.warn("Error reading file: " + file.getPath());
        }

        removeRedundantCopyrights(copyrights);

        return copyrights;
    }

    private void removeRedundantCopyrights(Collection<CopyrightInfo> copyrights) {
        if (copyrights.size() > 1) {
            // check if exists at least one copyright with year
            boolean hasCopyrightWithYear = false;
            for (CopyrightInfo copyright : copyrights) {
                if (copyright.getCopyright().matches(CONTAINS_YEAR_REGEX)) {
                    hasCopyrightWithYear = true;
                    break;
                }
            }

            if (hasCopyrightWithYear) {
                Iterator<CopyrightInfo> iterator = copyrights.iterator();
                while (iterator.hasNext()) {
                    CopyrightInfo copyrightInfo = iterator.next();
                    String copyright = copyrightInfo.getCopyright();

                    // remove regular lines found with the word 'copyright' but without year
                    // don't remove lines without year but have 'Copyright (C)' (probably an actual copyright reference)
                    if (!copyright.matches(CONTAINS_YEAR_REGEX) && !copyright.toLowerCase().contains(COPYRIGHT_SYMBOL)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    private File createOtherPlatformFile(File originalPlatform) {
        try {
//            if (originalPlatform.length() < Runtime.getRuntime().totalMemory()) {
            if (originalPlatform.length() < Runtime.getRuntime().freeMemory()) {
                byte[] byteArray = FileUtils.readFileToByteArray(originalPlatform);

                String fileText = new String(byteArray);
                File otherPlatFile = new File(PlATFORM_DEPENDENT_TMP_DIRECTORY, originalPlatform.getName());
                if (fileText.contains(CRLF)) {
                    FileUtils.write(otherPlatFile, fileText.replaceAll(CRLF, NEW_LINE));
                } else if (fileText.contains(NEW_LINE)) {
                    FileUtils.write(otherPlatFile, fileText.replaceAll(NEW_LINE, CRLF));
                }
                if (otherPlatFile.exists()) {
                    return otherPlatFile;
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            logger.warn("Failed to create other platform file " + originalPlatform.getName() + "can't be added to dependency list: ", e);
        }
        return null;
    }

    // check if lines with (c) are actual copyright references of simple code lines
    private boolean isActualCopyrightLine(String line) {
        String cleanLine = cleanLine(line).trim();
        boolean actualCopyrightLine = true;
        if (cleanLine.startsWith(DEFINE)) {
            return false;
        }

        // go forward
        int index = cleanLine.indexOf(COPYRIGHT_SYMBOL);
        for (int i = index + 1; i < cleanLine.length(); i++) {
            char c = cleanLine.charAt(i);
            if (c == OPEN_BRACKET || c == CLOSE_BRACKET || c == WHITESPACE_CHAR) {
                continue;
            } else if (MATH_SYMBOLS.contains(c)) {
                actualCopyrightLine = false;
                break;
            } else {
                break;
            }
        }

        // go backwards
        if (actualCopyrightLine) {
            for (int i = index - 1; i >= 0; i--) {
                char c = cleanLine.charAt(i);
                if (c == OPEN_BRACKET || c == CLOSE_BRACKET || c == WHITESPACE_CHAR) {
                    continue;
                } else if (MATH_SYMBOLS.contains(c)) {
                    actualCopyrightLine = false;
                    break;
                } else {
                    break;
                }
            }
        }
        return actualCopyrightLine;
    }

    private String cleanLine(String line) {
        return line.replace("/**", EMPTY_STRING).replace("/*", EMPTY_STRING)
                .replace("*", EMPTY_STRING).replace("#", EMPTY_STRING)
                .replace("/", EMPTY_STRING).replace("\\t", EMPTY_STRING)
                .replace("\\n", EMPTY_STRING).trim();
    }

    private void deleteFile(File file) {
        if (file != null) {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException e) {
                // do nothing
            }
        }
    }
}
