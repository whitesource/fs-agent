/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.ChecksumType;
import org.whitesource.agent.api.model.CopyrightInfo;
import org.whitesource.agent.api.model.DependencyHintsInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.hash.HashAlgorithm;
import org.whitesource.agent.hash.HashCalculator;
import org.whitesource.agent.hash.HintUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Factory class for {@link org.whitesource.agent.api.model.DependencyInfo}.
 *
 * @author tom.shapira
 */
public class DependencyInfoFactory {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(DependencyInfoFactory.class);

    private static final String COPYRIGHT = "copyright";
    private static final String COPYRIGHT_SYMBOL = "(c)";
    private static final String COPYRIGHT_ASCII_SYMBOL = "©";
    private static final List<String> COPYRIGHT_TEXTS = Arrays.asList("copyright (c)", "copyright(c)", "(c) copyright",
            "(c)copyright", COPYRIGHT, COPYRIGHT_SYMBOL, COPYRIGHT_ASCII_SYMBOL);
    private static final String ALL_RIGHTS_RESERVED = "all rights reserved";

    private static final String COPYRIGHT_ALPHA_CHAR_REGEX = ".*[a-zA-Z]+.*";
    private static final String CONTAINS_YEAR_REGEX = ".*(\\d\\d\\d\\d)+.*";

    private static final String JAVA_SCRIPT_REGEX = ".*\\.js";

    private static final List<Character> MATH_SYMBOLS = Arrays.asList('+', '-', '=', '<', '>', '*', '/', '%', '^');
    private static final int MAX_VALID_CHAR_VALUE = 127;
    private static final int MAX_INVALID_CHARS = 2;
    private static final String DEFINE = "define";
    private static final String TODO_PATTERN = "todo:.*|todo .*";
    private static final String CODE_LINE_SUFFIX = ".*:|.*;|.*\\{|.*}|.*\\[|.*]|.*>";

    private static final Map<String, String> commentStartEndMap;

    static {
        commentStartEndMap = new HashMap<>();
        commentStartEndMap.put("/*", "*/");
        commentStartEndMap.put("/**", "*/");
        commentStartEndMap.put("<!--", "-->");
        commentStartEndMap.put("\"\"\"", "\"\"\"");
        commentStartEndMap.put("=begin", "=end");
        commentStartEndMap.put("##", "##");
    }

    /* --- Members --- */

    private final Collection<String> excludedCopyrights;
    private final boolean partialSha1Match;
    private boolean calculateHints;
    private boolean calculateMd5;

    /* --- Constructors --- */

    public DependencyInfoFactory() {
        excludedCopyrights = new ArrayList<>();
        partialSha1Match = false;
    }

    public DependencyInfoFactory(Collection<String> excludedCopyrights, boolean partialSha1Match) {
        this.excludedCopyrights = excludedCopyrights;
        this.partialSha1Match = partialSha1Match;
    }

    public DependencyInfoFactory(Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5) {
        this(excludedCopyrights, partialSha1Match);
        this.calculateHints = calculateHints;
        this.calculateMd5 = calculateMd5;
    }

    /* --- Public methods --- */

    public DependencyInfo createDependencyInfo(File basedir, String filename) {
        DependencyInfo dependency;
        try {
            File dependencyFile = new File(basedir, filename);
            String sha1 = ChecksumUtils.calculateSHA1(dependencyFile);
            dependency = new DependencyInfo(sha1);
            dependency.setArtifactId(dependencyFile.getName());
            dependency.setFilename(dependencyFile.getName());

            // system path
            try {
                dependency.setSystemPath(dependencyFile.getCanonicalPath());
            } catch (IOException e) {
                dependency.setSystemPath(dependencyFile.getAbsolutePath());
            }

            // populate hints
            if (calculateHints) {
                DependencyHintsInfo hints = HintUtils.getHints(dependencyFile.getPath());
                dependency.setHints(hints);
            }

            // additional sha1s
            // MD5
            if (calculateMd5) {
                String md5 = ChecksumUtils.calculateHash(dependencyFile, HashAlgorithm.MD5);
                dependency.addChecksum(ChecksumType.MD5, md5);
            }

            // handle JavaScript files
            if (filename.toLowerCase().matches(JAVA_SCRIPT_REGEX)) {
                Map<ChecksumType, String> javaScriptChecksums;
                try {
                    javaScriptChecksums = new HashCalculator().calculateJavaScriptHashes(dependencyFile);
                    if (javaScriptChecksums == null || javaScriptChecksums.isEmpty()) {
                        logger.debug("Failed to calculate javaScript hash: {}", dependencyFile.getPath());
                    }
                    for (Map.Entry<ChecksumType, String> entry : javaScriptChecksums.entrySet()) {
                        dependency.addChecksum(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to calculate javaScript hash for file: {}, error: {}", dependencyFile.getPath(), e.getMessage());
                    logger.debug("Failed to calculate javaScript hash for file: {}, error: {}", dependencyFile.getPath(), e.getStackTrace());
                }
            }
            // other platform SHA1
            ChecksumUtils.calculateOtherPlatformSha1(dependency, dependencyFile);
            // super hash
            ChecksumUtils.calculateSuperHash(dependency, dependencyFile);
        } catch (IOException e) {
            logger.warn("Failed to create dependency " + filename + " to dependency list: {}", e.getMessage());
            dependency = null;
        }
        return dependency;
    }

    /* --- Private methods --- */

    private Collection<CopyrightInfo> extractCopyrights(File file) {
        Collection<CopyrightInfo> copyrights = new ArrayList<>();
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
                                    line = Constants.EMPTY_STRING;
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
                if ((commentBlock || line.startsWith("//") || line.startsWith(Constants.POUND))
                        && (lowerCaseLine.contains(COPYRIGHT) || lowerCaseLine.contains(COPYRIGHT_SYMBOL) || lowerCaseLine.contains(COPYRIGHT_ASCII_SYMBOL))) {
                    // ignore lines that contain (c) and math signs (+, <, etc.) near it
                    // and ignore lines that contain © and have other invalid ascii symbols
                    if ((lowerCaseLine.contains(COPYRIGHT_SYMBOL) && isMathExpression(lowerCaseLine)) ||
                            lowerCaseLine.contains(COPYRIGHT_ASCII_SYMBOL) && hasInvalidAsciiChars(lowerCaseLine)) {
                        continue;
                    }
                    line = cleanLine(line);

                    if (line.toLowerCase().matches(TODO_PATTERN)) {
                        continue;
                    }

                    StringBuilder sb = new StringBuilder();
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
                                    sb.append(Constants.WHITESPACE);
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
                            copyright = copyright.substring(0, startIndex).trim() + Constants.WHITESPACE + copyright.substring(endIndex).trim();
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

        // remove duplicate copyrights
        Set<String> copyTexts = new HashSet<>();
        Iterator<CopyrightInfo> iterator = copyrights.iterator();
        while (iterator.hasNext()) {
            String lowerCaseCopyright = iterator.next().getCopyright().toLowerCase();
            if (copyTexts.contains(lowerCaseCopyright)) {
                iterator.remove();
            } else {
                copyTexts.add(lowerCaseCopyright);
            }
        }
    }

    private boolean containsExcludedCopyright(DependencyInfo dependencyInfo) {
        for (CopyrightInfo copyrightInfo : dependencyInfo.getCopyrights()) {
            String lowerCaseCopyright = copyrightInfo.getCopyright().toLowerCase();
            for (String excludedCopyright : excludedCopyrights) {
                if (lowerCaseCopyright.contains(excludedCopyright.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    // check if lines with (c) are actual copyright references of simple code lines
    private boolean isMathExpression(String line) {
        String cleanLine = cleanLine(line).trim();
        boolean mathExpression = false;
        if (cleanLine.startsWith(DEFINE)) {
            return true;
        } else if (cleanLine.matches(CODE_LINE_SUFFIX)) {
            return true;
        }

        // go forward
        int index = cleanLine.indexOf(COPYRIGHT_SYMBOL);
        for (int i = index + 1; i < cleanLine.length(); i++) {
            char c = cleanLine.charAt(i);
            if (c == Constants.OPEN_BRACKET || c == Constants.CLOSE_BRACKET || c == Constants.WHITESPACE_CHAR) {
                continue;
            } else if (MATH_SYMBOLS.contains(c)) {
                mathExpression = true;
                break;
            } else {
                break;
            }
        }

        // go backwards
        if (mathExpression) {
            for (int i = index - 1; i >= 0; i--) {
                char c = cleanLine.charAt(i);
                if (c == Constants.OPEN_BRACKET || c == Constants.CLOSE_BRACKET || c == Constants.WHITESPACE_CHAR) {
                    continue;
                } else if (MATH_SYMBOLS.contains(c)) {
                    mathExpression = true;
                    break;
                } else {
                    break;
                }
            }
        }
        return mathExpression;
    }

    private boolean hasInvalidAsciiChars(String line) {
        String cleanLine = cleanLine(line).trim();
        int invalidChars = 0;
        for (int i = 0; i < cleanLine.length(); i++) {
            char c = cleanLine.charAt(i);
            if (c > MAX_VALID_CHAR_VALUE || c == Constants.QUESTION_MARK) {
                invalidChars++;
            }
            if (invalidChars == MAX_INVALID_CHARS) {
                return true;
            }
        }
        return false;
    }

    private String cleanLine(String line) {
        return line.replace("/**", Constants.EMPTY_STRING).replace("/*", Constants.EMPTY_STRING)
                .replace("*", Constants.EMPTY_STRING).replace(Constants.POUND, Constants.EMPTY_STRING)
                .replace(Constants.FORWARD_SLASH, Constants.EMPTY_STRING).replace("\\t", Constants.EMPTY_STRING)
                .replace("\\n", Constants.EMPTY_STRING).trim();
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