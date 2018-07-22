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
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.MemoryUsageHelper;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.fs.configuration.AgentConfiguration;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

    private final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);
    private String FSA_FILE = "**/*whitesource-fs-agent-*.*jar";

    /* --- Members --- */

    private final boolean isSeparateProjects;
    private final AgentConfiguration agent;
    private final boolean showProgressBar;
    private boolean enableImpactAnalysis;
    private ViaLanguage iaLanguage;
    private DependencyResolutionService dependencyResolutionService;

    /* --- Constructors --- */

    public FileSystemScanner(ResolverConfiguration resolver, AgentConfiguration agentConfiguration, boolean enableImpactAnalysis) {
        this.dependencyResolutionService = new DependencyResolutionService(resolver);
        this.isSeparateProjects = dependencyResolutionService.isSeparateProjects();
        this.agent = agentConfiguration;
        this.showProgressBar = agentConfiguration.isShowProgressBar();
        this.enableImpactAnalysis = enableImpactAnalysis;
    }

    public FileSystemScanner(ResolverConfiguration resolver, AgentConfiguration agentConfiguration, boolean enableImpactAnalysis, ViaLanguage iaLanguage) {
        this(resolver, agentConfiguration, enableImpactAnalysis);
        this.iaLanguage = iaLanguage;
    }

    /* --- Public methods --- */

    /**
     * This method is usually called from outside by different other tools
     *
     * @param scannerBaseDirs        folders to scan
     * @param scmConnector           use scmConnector
     * @param includes               includes glob patterns
     * @param excludes               excludes glob patterns
     * @param globCaseSensitive      global case sensitive
     * @param archiveExtractionDepth depth of recursive extraction
     * @param archiveIncludes        includes glob patterns for extraction
     * @param archiveExcludes        exclude glob patterns for extraction
     * @param archiveFastUnpack      use fast extraction
     * @param followSymlinks         use followSymlinks
     * @param excludedCopyrights     use excludedCopyrights
     * @param partialSha1Match       use partialSha1Match
     * @return list of all the dependencies for project
     */

    public List<DependencyInfo> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean scmConnector,
                                               String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                               String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                               Collection<String> excludedCopyrights, boolean partialSha1Match, String[] pythonRequirementsFileIncludes) {
        Collection<AgentProjectInfo> projects = createProjects(scannerBaseDirs, appPathsToDependencyDirs, scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match,
                false, false, pythonRequirementsFileIncludes).keySet();
        return projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
    }

    public List<DependencyInfo> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                               String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                               String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                               Collection<String> excludedCopyrights, boolean partialSha1Match, String[] pythonRequirementsFileIncludes) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match, pythonRequirementsFileIncludes);
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, boolean hasScmConnector) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), hasScmConnector);
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean hasScmConnector) {
        return createProjects(scannerBaseDirs, appPathsToDependencyDirs, hasScmConnector, agent.getIncludes(), agent.getExcludes(), agent.getGlobCaseSensitive(), agent.getArchiveExtractionDepth(),
                agent.getArchiveIncludes(), agent.getArchiveExcludes(), agent.isArchiveFastUnpack(), agent.isFollowSymlinks(),
                agent.getExcludedCopyrights(), agent.isPartialSha1Match(), agent.isCalculateHints(), agent.isCalculateMd5(), agent.getPythonRequirementsFileIncludes());
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                                                           String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                                           String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                                           Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5, String[] pythonRequirementsFileIncludes) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth, archiveIncludes, archiveExcludes,
                archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match, calculateHints, calculateMd5, pythonRequirementsFileIncludes);
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean scmConnector,
                                                                           String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                                           String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                                           Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5, String[] pythonRequirementsFileIncludes) {

        MemoryUsageHelper.SystemStats systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());

        // get canonical paths
        Set<String> pathsToScan = getCanonicalPaths(scannerBaseDirs);

        for (String appPath : appPathsToDependencyDirs.keySet()) {
            appPathsToDependencyDirs.put(appPath, getCanonicalPaths(appPathsToDependencyDirs.get(appPath)));
        }

        // todo: consider adding exit since this can be called from other components
        //validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;

        String unpackDirectory;
        boolean archiveExtraction = false;
        // go over all base directories, look for archives
        Map<String, String> archiveToBaseDirMap = new HashMap<>();
        List<String> archiveDirectories = new ArrayList<>();
        if (archiveExtractionDepth > 0) {
            ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, excludes, archiveFastUnpack);
            logger.info("Starting Archive Extraction (may take a few minutes)");
            for (String scannerBaseDir : new LinkedHashSet<>(pathsToScan)) {
                unpackDirectory = archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth, archiveDirectories);
                if (unpackDirectory != null) {
                    archiveExtraction = true;
                    String parentFileUrl = new File(scannerBaseDir).getParent();
                    logger.debug("Unpack directory: {}, parent file: {}", unpackDirectory, parentFileUrl);
                    archiveToBaseDirMap.put(unpackDirectory, parentFileUrl);
                    pathsToScan.add(unpackDirectory);
                    if (!appPathsToDependencyDirs.containsKey(FSAConfiguration.DEFAULT_KEY)) {
                        appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, new HashSet<>());
                    }
                    appPathsToDependencyDirs.get(FSAConfiguration.DEFAULT_KEY).add(unpackDirectory);
                }
            }
        }

        // create dependencies from files - first project is always the default one
        logger.info("Starting analysis");
        Map<AgentProjectInfo, Path> allProjects = new HashMap<>();
        Map<AgentProjectInfo, LinkedList<ViaComponents>> allProjectsToViaComponents = new HashMap<>();
        AgentProjectInfo mainProject = new AgentProjectInfo();
        allProjects.put(mainProject, null);
        allProjectsToViaComponents.put(mainProject, new LinkedList<>());

        logger.info("Scanning directories {} for matching Files (may take a few minutes)", pathsToScan);
        logger.info("Included file types: {}", String.join(Constants.COMMA, includes));
        logger.info("Excluded file types: {}", String.join(Constants.COMMA, excludes));
        String[] resolversIncludesPattern = createResolversIncludesPattern(dependencyResolutionService.getDependencyResolvers());

        Map<File, Collection<String>> fileMapBeforeResolve = new FilesUtils().fillFilesMap(pathsToScan, resolversIncludesPattern, excludes, followSymlinks, globCaseSensitive);
        Set<String> allFiles = fileMapBeforeResolve.entrySet().stream().flatMap(folder -> folder.getValue().stream()).collect(Collectors.toSet());

        final int[] totalDependencies = {0};
        boolean isDependenciesOnly = false;
        if (enableImpactAnalysis && iaLanguage != null) {
            for (String appPath : appPathsToDependencyDirs.keySet()) {
                if (!appPath.equals(FSAConfiguration.DEFAULT_KEY)) {
                    if ((appPathsToDependencyDirs.get(appPath)).iterator().next() != null) {
                        String pojoAppPath = appPath;
                        allProjectsToViaComponents.get(allProjects.keySet().stream().findFirst().get()).add(new ViaComponents(pojoAppPath, iaLanguage));
                    }
                }
            }
        } else if (dependencyResolutionService != null && dependencyResolutionService.shouldResolveDependencies(allFiles)) {
            logger.info("Attempting to resolve dependencies");
            isDependenciesOnly = dependencyResolutionService.isDependenciesOnly();

            // get all resolution results
            Collection<ResolutionResult> resolutionResults = new ArrayList<>();
            for (String appPath : appPathsToDependencyDirs.keySet()) {
                ViaComponents viaComponents = null;
                ViaLanguage impactAnalysisLanguage = null;
                Collection<ResolutionResult> resolutionResult = new LinkedList<>();
                LinkedList<String> pathsList = new LinkedList<>();
                pathsList.addAll(appPathsToDependencyDirs.get(appPath));
                if (appPath.equals(FSAConfiguration.DEFAULT_KEY) && appPathsToDependencyDirs.keySet().size() == 1) {
                    resolutionResult = dependencyResolutionService.resolveDependencies(pathsList, excludes);
                } else if (!appPath.equals(FSAConfiguration.DEFAULT_KEY) && appPathsToDependencyDirs.keySet().size() > 1) {
                    resolutionResult = dependencyResolutionService.resolveDependencies(pathsList, excludes);
                }
                if (resolutionResult.size() == 1 && !appPath.equals(FSAConfiguration.DEFAULT_KEY)) {
                    DependencyType dependencyType = resolutionResult.stream().findFirst().get().getDependencyType();
                    if (dependencyType == null) {
                        break;
                    } else {
                        // validate scanned language and set the
                        switch (dependencyType) {
                            case NPM:
                            case BOWER:
                                impactAnalysisLanguage = ViaLanguage.JAVA_SCRIPT;
                                break;
                            case MAVEN:
                            case GRADLE:
                                impactAnalysisLanguage = ViaLanguage.JAVA;
                                break;
                            default:
                                break;
                        }
                    }
                } else if (resolutionResult.size() > 1 && enableImpactAnalysis) {
                    // logger.info("Impact analysis won't run, more than one language detected");
                    // TODO return message when needed WSE-342
                }
                if (impactAnalysisLanguage != null) {
                    viaComponents = new ViaComponents(appPath, impactAnalysisLanguage);
                }
                for (ResolutionResult result : resolutionResult) {
                    Map<AgentProjectInfo, Path> projects = result.getResolvedProjects();
                    Collection<DependencyInfo> dependenciesToVia = new ArrayList<>();
                    for (Map.Entry<AgentProjectInfo, Path> project : projects.entrySet()) {
                        Collection<DependencyInfo> dependencies = project.getKey().getDependencies();
                        dependenciesToVia.addAll(dependencies);
                        // do not add projects with no dependencies
                        if (!dependencies.isEmpty()) {
                            AgentProjectInfo currentProject;

                            // if it is single project threat it as the main
                            if (((DependencyType.MAVEN.equals(result.getDependencyType()) &&
                                    (!dependencyResolutionService.isMavenAggregateModules() || !dependencyResolutionService.isSbtAggregateModules()) ||
                                    DependencyType.GRADLE.equals(result.getDependencyType()) && !dependencyResolutionService.isGradleAggregateModules())) &&
                                    result.getResolvedProjects().size() > 1) {
                                allProjects.put(project.getKey(), project.getValue());
                                LinkedList<ViaComponents> listToNewProject = new LinkedList<>();
                                if (impactAnalysisLanguage != null) {
                                    listToNewProject.add(viaComponents);
                                }
                                allProjectsToViaComponents.put(project.getKey(), listToNewProject);
                            } else {
                                currentProject = allProjects.keySet().stream().findFirst().get();
                                currentProject.getDependencies().addAll(project.getKey().getDependencies());
                                if (impactAnalysisLanguage != null) {
                                    allProjectsToViaComponents.get(allProjects.keySet().stream().findFirst().get()).add(viaComponents);
                                }
                            }
                            impactAnalysisLanguage = null;
                            totalDependencies[0] += dependencies.size();
                            dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies));
                        }
                    }
                    if (viaComponents != null) {
                        viaComponents.getDependencies().addAll(dependenciesToVia);
                    }
                }
                resolutionResults.addAll(resolutionResult);
            }

            logger.info(MessageFormat.format("Total dependencies found: {0}", totalDependencies[0]));

            // merge additional excludes
            Set<String> allExcludes = resolutionResults.stream().flatMap(resolution -> resolution.getExcludes().stream()).collect(Collectors.toSet());
            allExcludes.addAll(Arrays.stream(excludes).collect(Collectors.toList()));

            // change the original excludes with the merged values
            excludes = new String[allExcludes.size()];
            excludes = allExcludes.toArray(excludes);
            dependencyResolutionService = null;
        }

        String[] excludesExtended = excludeFileSystemAgent(excludes);
        logger.info("Scanning directories {} for matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMap = new FilesUtils().fillFilesMap(pathsToScan, includes, excludesExtended, followSymlinks, globCaseSensitive);
        long filesCount = fileMap.entrySet().stream().flatMap(folder -> folder.getValue().stream()).count();
        totalFiles += filesCount;
        logger.info(MessageFormat.format("Total files found according to the includes/excludes pattern: {0}", totalFiles));
        DependencyCalculator dependencyCalculator = new DependencyCalculator(showProgressBar);
        final Collection<DependencyInfo> filesDependencies = new LinkedList<>();

        if (!isDependenciesOnly) {
            filesDependencies.addAll(dependencyCalculator.createDependencies(
                    scmConnector, totalFiles, fileMap, excludedCopyrights, partialSha1Match, calculateHints, calculateMd5));
        }

        if (allProjects.size() == 1) {
            AgentProjectInfo project = allProjects.keySet().stream().findFirst().get();
            project.getDependencies().addAll(filesDependencies);
        } else {
            // remove files from handled projects
            allProjects.entrySet().forEach(project -> {
                Collection<DependencyInfo> projectDependencies = filesDependencies.stream()
                        .filter(dependencyInfo -> project.getValue() != null && dependencyInfo.getSystemPath().contains(project.getValue().toString())).collect(Collectors.toList());
                project.getKey().getDependencies().addAll(projectDependencies);
                filesDependencies.removeAll(projectDependencies);
            });

            // create new projects if necessary
            if (!isDependenciesOnly && filesDependencies.size() > 0) {
                scannerBaseDirs.stream().forEach(directory -> {
                    List<Path> subDirectories;
                    // check all folders

                    String[] includesAll = {Constants.PATTERN};
                    subDirectories = new FilesUtils().getSubDirectories(directory, includesAll, null, followSymlinks, globCaseSensitive);
                    subDirectories.forEach(subFolder -> {
                        if (filesDependencies.size() > 0) {
                            List<DependencyInfo> projectDependencies = filesDependencies.stream().
                                    filter(dependencyInfo -> dependencyInfo.getSystemPath().contains(subFolder.toString())).collect(Collectors.toList());
                            if (!projectDependencies.isEmpty()) {
                                AgentProjectInfo subProject;
                                if (isSeparateProjects) {
                                    subProject = new AgentProjectInfo();
                                    allProjects.put(subProject, null);
                                    allProjectsToViaComponents.put(subProject, new LinkedList<>());
                                    subProject.setCoordinates(new Coordinates(null, subFolder.toFile().getName(), null));
                                } else {
                                    subProject = allProjects.entrySet().stream().findFirst().get().getKey();
                                }
                                subProject.setDependencies(projectDependencies);
                                filesDependencies.removeAll(projectDependencies);
                            }
                        }
                    });
                });
            }
        }

        for (AgentProjectInfo innerProject : allProjects.keySet()) {
            // replace temp folder name with base dir
            for (DependencyInfo dependencyInfo : innerProject.getDependencies()) {
                String systemPath = dependencyInfo.getSystemPath();
                if (systemPath == null) {
                    logger.debug("Dependency {} has no system path", dependencyInfo.getArtifactId());
                } else {
                    for (String key : archiveToBaseDirMap.keySet()) {
                        if (systemPath.contains(key) && archiveExtraction) {
                            String newSystemPath = systemPath.replace(key, archiveToBaseDirMap.get(key)).replaceAll(ArchiveExtractor.DEPTH_REGEX, Constants.EMPTY_STRING);
                            logger.debug("Original system path: {}, new system path: {}, key: {}", systemPath, newSystemPath, key);
                            dependencyInfo.setSystemPath(newSystemPath);
                            break;
                        }
                    }
                }
            }
        }

        // delete all archive temp folders
        if (!archiveDirectories.isEmpty()) {
            for (String archiveDirectory : archiveDirectories) {
                File directory = new File(archiveDirectory);
                if (directory.exists()) {
                    FileUtils.deleteQuietly(directory);
                }
            }
        }
        logger.info("Finished analyzing Files");
        systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());
        // add dependencies to project in case of pojo project
        if (enableImpactAnalysis && iaLanguage != null) {
            AgentProjectInfo agentProjectInfo = allProjectsToViaComponents.keySet().iterator().next();
            allProjectsToViaComponents.get(agentProjectInfo).getFirst().getDependencies().addAll(
                    agentProjectInfo.getDependencies());
        }
        return allProjectsToViaComponents;
    }

    private String[] createResolversIncludesPattern(Collection<AbstractDependencyResolver> dependencyResolvers) {
        Collection<String> resultIncludes = new ArrayList<>();
        for (AbstractDependencyResolver dependencyResolver : dependencyResolvers) {
            for (String extension : dependencyResolver.getSourceFileExtensions()) {
                resultIncludes.add(Constants.PATTERN + extension);
            }
        }
        String[] resultArray = new String[resultIncludes.size()];
        resultIncludes.toArray(resultArray);
        return resultArray;
    }

    /* --- Private methods --- */

    private Map<String, Set<String>> convertListDirsToMap(List<String> scannerBaseDirs) {
        Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
        appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, new HashSet<>());
        for (String dir : scannerBaseDirs) {
            appPathsToDependencyDirs.get(FSAConfiguration.DEFAULT_KEY).add(dir);
        }
        return appPathsToDependencyDirs;
    }

    private Set<String> getCanonicalPaths(Collection<String> scannerBaseDirs) {
        // use canonical paths to resolve '.' in path
        Set<String> pathsToScan = new HashSet<>();
        for (String path : scannerBaseDirs) {
            try {
                pathsToScan.add(new File(path).getCanonicalPath());
            } catch (IOException e) {
                // use the given path as-is
                logger.debug("Error finding the canonical path of {}", path);
                pathsToScan.add(path);
            }
        }
        return pathsToScan;
    }

    private void increaseCount(DependencyInfo dependency, int[] totalDependencies) {
        totalDependencies[0] += dependency.getChildren().size();
        dependency.getChildren().forEach(dependencyInfo -> increaseCount(dependencyInfo, totalDependencies));
    }

    private String[] excludeFileSystemAgent(String[] excludes) {
        String[] allExcludes = excludes == null ? new String[0] : excludes;
        String[] excludesFSA = new String[allExcludes.length + 1];
        System.arraycopy(allExcludes, 0, excludesFSA, 0, allExcludes.length);
        excludesFSA[allExcludes.length] = FSA_FILE;
        return excludesFSA;
    }
}