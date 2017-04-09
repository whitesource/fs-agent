package org.whitesource.agent;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.scm.ScmConnector;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

/**
 * This class does the actual directory scanning, creates {@link DependencyInfo}s.
 *
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemScanner {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);

    private static final List<String> progressAnimation = Arrays.asList("|", "/", "-", "\\");
    private static final int ANIMATION_FRAMES = progressAnimation.size();
    private static final String EMPTY_STRING = "";
    private static int animationIndex = 0;
    private static String BACK_SLASH = "\\";
    private static String FORWARD_SLASH = "/";
    private static String BOWER_JSON = "bower.json";
    private static String PACKAGE_JSON = "package.json";
    private static String BOWER_FOLDER = "\\bower_components\\";
    private static String NPM_FOLDER = "\\node_modules\\";

    /* --- Public methods --- */

    public List<DependencyInfo> createDependencyInfos(List<String> scannerBaseDirs, ScmConnector scmConnector,
                                                      String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                      String[] archiveIncludes, String[] archiveExcludes, boolean followSymlinks, Collection<String> excludedCopyrights,
                                                      boolean partialSha1Match) {
        Collection<String> pathsToScan = new ArrayList<String>(scannerBaseDirs);

        // validate parameters
        validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;
        Map<File, Collection<String>> fileMap = new HashMap<File, Collection<String>>();

        // go over all base directories, look for archives
        Map<String, String> archiveToBaseDirMap = new HashMap<String, String>();
        ArchiveExtractor archiveExtractor = null;
        if (archiveExtractionDepth > 0) {
            archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes);
            logger.info("Starting Archive Extraction (may take a few minutes)");
            for (String scannerBaseDir : pathsToScan) {
                archiveToBaseDirMap.put(archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth), scannerBaseDir);
            }
            pathsToScan.addAll(archiveToBaseDirMap.keySet());
        }
        for (String scannerBaseDir : pathsToScan) {
            File file = new File(scannerBaseDir);
            if (file.exists()) {
                if (file.isDirectory()) {
                    logger.info("Scanning Directory {} for Matching Files (may take a few minutes)", scannerBaseDir);
                    DirectoryScanner scanner = new DirectoryScanner();
                    scanner.setBasedir(scannerBaseDir);
                    scanner.setIncludes(includes);
                    scanner.setExcludes(excludes);
                    scanner.setFollowSymlinks(followSymlinks);
                    scanner.setCaseSensitive(globCaseSensitive);
                    scanner.scan();
                    File basedir = scanner.getBasedir();
                    String[] fileNames = scanner.getIncludedFiles();
                    checkUnsupportedFileTypes(fileNames);
                    fileMap.put(basedir, Arrays.asList(fileNames));
                    totalFiles += fileNames.length;
                } else {
                    // handle file
                    Collection<String> files = fileMap.get(file.getParentFile());
                    if (files == null) {
                        files = new ArrayList<String>();
                    }
                    files.add(file.getName());
                    fileMap.put(file.getParentFile(), files);
                    totalFiles++;
                }
            } else {
                logger.info(MessageFormat.format("File {0} doesn't exist", scannerBaseDir));
            }
        }
        logger.info(MessageFormat.format("Total Files Found: {0}", totalFiles));

        DependencyInfoFactory factory = new DependencyInfoFactory(excludedCopyrights, partialSha1Match);

        // create dependency infos from files
        logger.info("Starting Analysis");
        List<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        displayProgress(0, totalFiles);
        int index = 1;
        for (Map.Entry<File, Collection<String>> entry : fileMap.entrySet()) {
            for (String fileName : entry.getValue()) {
                DependencyInfo originalDependencyInfo = factory.createDependencyInfo(entry.getKey(), fileName);
                if (originalDependencyInfo != null) {
                    if (scmConnector != null) {
                        originalDependencyInfo.setSystemPath(fileName.replace(BACK_SLASH, FORWARD_SLASH));
                    }
                    dependencyInfos.add(originalDependencyInfo);
                }

                // print progress
                displayProgress(index, totalFiles);
                index++;
            }
        }

        // replace temp folder name with base dir
        for (DependencyInfo dependencyInfo : dependencyInfos) {
            String systemPath = dependencyInfo.getSystemPath();
            for (String key : archiveToBaseDirMap.keySet()) {
                if (dependencyInfo.getSystemPath().contains(key)) {
                    dependencyInfo.setSystemPath(systemPath.replace(key, archiveToBaseDirMap.get(key)).replaceAll(archiveExtractor.getRandomString(), EMPTY_STRING));
                    break;
                }
            }
        }

        // delete all archive temp folders
        if (archiveExtractor != null) {
            archiveExtractor.deleteArchiveDirectory();
        }

        // delete scm clone directory
        if (scmConnector != null) {
            scmConnector.deleteCloneDirectory();
        }
        logger.info("Finished Analyzing Files");
        return dependencyInfos;
    }

    private void displayProgress(int index, int totalFiles) {
        StringBuilder sb = new StringBuilder("[INFO] ");

        // draw each animation for 4 frames
        int actualAnimationIndex = animationIndex % (ANIMATION_FRAMES * 4);
        sb.append(progressAnimation.get((actualAnimationIndex / 4) % ANIMATION_FRAMES));
        animationIndex++;

        // draw progress bar
        sb.append(" [");
        double percentage = ((double) index / totalFiles) * 100;
        int progressionBlocks = (int) (percentage / 3);
        for (int i = 0; i < progressionBlocks; i++) {
            sb.append("#");
        }
        for (int i = progressionBlocks; i < 33; i++) {
            sb.append(" ");
        }
        sb.append("] {0}% - {1} of {2} files\r");
        System.out.print(MessageFormat.format(sb.toString(), (int) percentage, index, totalFiles));

        if (index == totalFiles) {
            // clear progress animation
            System.out.print("                                                                                  \r");
        }
    }

    private void validateParams(int archiveExtractionDepth, String[] includes) {
        boolean isShutDown = false;
        if (archiveExtractionDepth < 0 || archiveExtractionDepth > 5) {
            logger.warn("Error: archiveExtractionDepth value should be greater than 0 and less than 4");
            isShutDown = true;
        }
        if (includes.length < 1 || StringUtils.isBlank(includes[0])) {
            logger.warn("Error: includes parameter must have at list one scanning pattern");
            isShutDown = true;
        }
        if (isShutDown) {
            logger.warn("Exiting");
            System.exit(1);
        }
    }

    private void checkUnsupportedFileTypes(String[] fileNames) {
        boolean bowerPrintedOnce = false;
        boolean packagePrintedOnce = false;
        boolean nodePrintedOnce = false;
        boolean comPrintedOnce = false;
        for (String file : fileNames) {
            if (file.endsWith(BOWER_JSON) && !bowerPrintedOnce) {
                bowerPrintedOnce = true;
                logger.info("Found {} file, please consider using Bower-Plugin", BOWER_JSON);
            } else if (file.endsWith(PACKAGE_JSON) && !packagePrintedOnce) {
                packagePrintedOnce = true;
                logger.info("Found {} file, please consider using Npm-Plugin", PACKAGE_JSON);
            } else if (file.contains(NPM_FOLDER) && !nodePrintedOnce) {
                nodePrintedOnce = true;
                logger.info("Found {} folder, suspect presence of NPM packages. Please consider using NPM-Plugin", NPM_FOLDER);
            } else if (file.contains(BOWER_FOLDER) && !nodePrintedOnce) {
                comPrintedOnce = true;
                logger.info("Found {} folder, suspect presence of Bower packages. Please consider using Bower-Plugin", BOWER_FOLDER);
            }
            if (bowerPrintedOnce && packagePrintedOnce && nodePrintedOnce && comPrintedOnce) {
                return;
            }
        }
    }
}