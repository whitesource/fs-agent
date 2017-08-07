package org.whitesource.agent;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.scm.ScmConnector;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

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
    public static final int MAX_EXTRACTION_DEPTH = 7;
    private static int animationIndex = 0;
    private static String BACK_SLASH = "\\";
    private static String FORWARD_SLASH = "/";
    private static String FSA_FILE = "**/*whitesource-fs-agent-*.*jar";

    private final DependencyResolutionService dependencyResolutionService;
    private final FilesScanner filesScanner;

    /* --- Members --- */

    private boolean showProgressBar;

    /* --- Constructors --- */

    public FileSystemScanner(boolean showProgressBar, DependencyResolutionService dependencyResolutionService) {
        this.showProgressBar = showProgressBar;
        this.dependencyResolutionService = dependencyResolutionService;
        filesScanner = new FilesScanner();
    }

    /* --- Public methods --- */

    public List<DependencyInfo> createDependencies(List<String> scannerBaseDirs, ScmConnector scmConnector,
                                                   String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                   String[] archiveIncludes, String[] archiveExcludes, boolean followSymlinks,
                                                   Collection<String> excludedCopyrights, boolean partialSha1Match) {
        Collection<String> pathsToScan = new ArrayList<>(scannerBaseDirs);

        // validate parameters
        validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;


        // go over all base directories, look for archives
        Map<String, String> archiveToBaseDirMap = new HashMap<>();
        ArchiveExtractor archiveExtractor = null;
        if (archiveExtractionDepth > 0) {
            archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes);
            logger.info("Starting Archive Extraction (may take a few minutes)");
            for (String scannerBaseDir : pathsToScan) {
                archiveToBaseDirMap.put(archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth), scannerBaseDir);
            }
            pathsToScan.addAll(archiveToBaseDirMap.keySet());
        }

        // create dependencies from files
        logger.info("Starting Analysis");
        List<DependencyInfo> allDependencies = new ArrayList<>();

        logger.info("Scanning Directory {} for Matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMapBeforeResolve = fillFilesMap(pathsToScan, includes, excludes, followSymlinks, globCaseSensitive);
        Set<String> allFiles = fileMapBeforeResolve.entrySet().stream().flatMap(folder -> folder.getValue().stream()).collect(Collectors.toSet());

        if (dependencyResolutionService != null && dependencyResolutionService.shouldResolveDependencies(allFiles)) {
            // get all resolution results
            Collection<ResolutionResult> resolutionResults = dependencyResolutionService.resolveDependencies(pathsToScan, excludes);

            // add all resolved dependencies
            final int[] totalDependencies = {0};
            resolutionResults.stream().map(dependency -> dependency.getResolvedDependencies()).forEach(dependencies -> {
                allDependencies.addAll(dependencies);
                totalDependencies[0] += dependencies.size();
                dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies));
            });
            logger.info(MessageFormat.format("Total dependencies Found: {0}", totalDependencies[0]));

            // merge additional excludes
            Set<String> allExcludes = resolutionResults.stream().flatMap(resolution -> resolution.getExcludes().stream()).collect(Collectors.toSet());
            allExcludes.addAll(Arrays.stream(excludes).collect(Collectors.toList()));

            // change the original excludes with the merged values
            excludes = new String[allExcludes.size()];
            excludes = allExcludes.toArray(excludes);
        }

        String[] excludesExtended = excludeFileSystemAgent(excludes);
        Map<File, Collection<String>> fileMap = fillFilesMap(pathsToScan, includes, excludesExtended, followSymlinks, globCaseSensitive);
        long filesCount = fileMap.entrySet().stream().flatMap(folder -> folder.getValue().stream()).count();
        totalFiles += filesCount;
        logger.info(MessageFormat.format("Total Files Found: {0}", totalFiles));

        DependencyInfoFactory factory = new DependencyInfoFactory(excludedCopyrights, partialSha1Match);
        Collection<DependencyInfo> filesDependencies = createDependencies(scmConnector, totalFiles, fileMap, factory);
        allDependencies.addAll(filesDependencies);

        // replace temp folder name with base dir
        for (DependencyInfo dependencyInfo : allDependencies) {
            String systemPath = dependencyInfo.getSystemPath();
            for (String key : archiveToBaseDirMap.keySet()) {
                if (dependencyInfo.getSystemPath().contains(key) && archiveExtractor != null) {
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
        return allDependencies;
    }

    private Collection<DependencyInfo> createDependencies(ScmConnector scmConnector, int totalFiles, Map<File, Collection<String>> fileMap, DependencyInfoFactory factory) {
        List<DependencyInfo> allDependencies = new ArrayList<>();
        if (showProgressBar) {
            displayProgress(0, totalFiles);
        }
        int index = 1;
        for (Map.Entry<File, Collection<String>> entry : fileMap.entrySet()) {
            for (String fileName : entry.getValue()) {
                DependencyInfo originalDependencyInfo = factory.createDependencyInfo(entry.getKey(), fileName);
                if (originalDependencyInfo != null) {
                    if (scmConnector != null) {
                        originalDependencyInfo.setSystemPath(fileName.replace(BACK_SLASH, FORWARD_SLASH));
                    }
                    allDependencies.add(originalDependencyInfo);
                }

                // print progress
                if (showProgressBar) {
                    displayProgress(index, totalFiles);
                }
                index++;
            }
        }
        return allDependencies;
    }

    private Map<File, Collection<String>> fillFilesMap(Collection<String> pathsToScan, String[] includes, String[] excludesExtended, boolean followSymlinks, boolean globCaseSensitive) {
        Map<File, Collection<String>> fileMap = new HashMap<>();
        for (String scannerBaseDir : pathsToScan) {
            File file = new File(scannerBaseDir);
            if (file.exists()) {
                if (file.isDirectory()) {
                    File basedir = new File(scannerBaseDir);
                    String[] fileNames = filesScanner.getFileNames(scannerBaseDir, includes, excludesExtended, followSymlinks, globCaseSensitive);
                    fileMap.put(basedir, Arrays.asList(fileNames));
                } else {
                    // handle file
                    Collection<String> files = fileMap.get(file.getParentFile());
                    if (files == null) {
                        files = new ArrayList<>();
                    }
                    files.add(file.getName());
                    fileMap.put(file.getParentFile(), files);
                }
            } else {
                logger.info(MessageFormat.format("File {0} doesn't exist", scannerBaseDir));
            }
        }
        return fileMap;
    }

    private void increaseCount(DependencyInfo dependency, int[] totalDependencies) {
        totalDependencies[0] += dependency.getChildren().size();
        dependency.getChildren().forEach(dependencyInfo -> increaseCount(dependencyInfo, totalDependencies));
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
        if (archiveExtractionDepth < 0 || archiveExtractionDepth > MAX_EXTRACTION_DEPTH) {
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

    private String[] excludeFileSystemAgent(String[] excludes) {
        String[] excludesFSA = new String[excludes.length + 1];
        System.arraycopy(excludes, 0, excludesFSA, 0, excludes.length);
        excludesFSA[excludes.length] = FSA_FILE;
        return excludesFSA;
    }
}