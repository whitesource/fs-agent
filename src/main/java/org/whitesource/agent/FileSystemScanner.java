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
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.MemoryUsageHelper;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.fs.Main;
import org.whitesource.fs.StatusCode;
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

    /* --- Private Members --- */

    private final boolean isSeparateProjects;
    private final AgentConfiguration agent;
    private final boolean showProgressBar;
    private boolean enableImpactAnalysis;
    private ViaLanguage iaLanguage;
    private DependencyResolutionService dependencyResolutionService;
    private String sha1;

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

    @Deprecated
    public List<DependencyInfo> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean scmConnector,
                                               String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                               String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                               Collection<String> excludedCopyrights, boolean partialSha1Match, String[] pythonRequirementsFileIncludes) {
        Collection<AgentProjectInfo> projects = createProjects(scannerBaseDirs, appPathsToDependencyDirs, scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match,
                false, false, pythonRequirementsFileIncludes).keySet();
        return projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
    }

    @Deprecated
    public List<DependencyInfo> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                               String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                               String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                               Collection<String> excludedCopyrights, boolean partialSha1Match, String[] pythonRequirementsFileIncludes) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match, pythonRequirementsFileIncludes);
    }

    @Deprecated
    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, boolean hasScmConnector) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), hasScmConnector);
    }

    @Deprecated
    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean hasScmConnector) {
        return createProjects(scannerBaseDirs, appPathsToDependencyDirs, hasScmConnector, agent.getIncludes(), agent.getExcludes(), agent.getGlobCaseSensitive(), agent.getArchiveExtractionDepth(),
                agent.getArchiveIncludes(), agent.getArchiveExcludes(), agent.isArchiveFastUnpack(), agent.isFollowSymlinks(),
                agent.getExcludedCopyrights(), agent.isPartialSha1Match(), agent.isCalculateHints(), agent.isCalculateMd5(), agent.getPythonRequirementsFileIncludes());
    }

    @Deprecated
    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                                                           String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                                           String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                                           Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5, String[] pythonRequirementsFileIncludes) {
        return createProjects(scannerBaseDirs, convertListDirsToMap(scannerBaseDirs), scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth, archiveIncludes, archiveExcludes,
                archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match, calculateHints, calculateMd5, pythonRequirementsFileIncludes);
    }

    @Deprecated
    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean scmConnector,
                                                                           String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                                           String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                                           Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints,
                                                                           boolean calculateMd5, String[] pythonRequirementsFileIncludes) {
        AgentConfiguration agentConfiguration = new AgentConfiguration(includes, excludes, new String[]{}, new String[]{}, archiveExtractionDepth, archiveIncludes, archiveExcludes, archiveFastUnpack,
                followSymlinks, partialSha1Match, calculateHints, calculateMd5, showProgressBar, globCaseSensitive, false, excludedCopyrights, new String[]{}, new String[]{},
                pythonRequirementsFileIncludes, Constants.EMPTY_STRING);
        ProjectConfiguration projectConfiguration = new ProjectConfiguration(agentConfiguration, scannerBaseDirs, appPathsToDependencyDirs, scmConnector);
        return createProjects(projectConfiguration);
    }

    public Map<AgentProjectInfo, LinkedList<ViaComponents>> createProjects(ProjectConfiguration projectConfiguration) {
        MemoryUsageHelper.SystemStats systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());

        // get canonical paths
        Set<String> pathsToScan = getCanonicalPaths(projectConfiguration.getScannerBaseDirs());

        for (String appPath : projectConfiguration.getAppPathsToDependencyDirs().keySet()) {
            projectConfiguration.getAppPathsToDependencyDirs().put(appPath, getCanonicalPaths(projectConfiguration.getAppPathsToDependencyDirs().get(appPath)));
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
        AgentConfiguration agentConfiguration = projectConfiguration.getAgentConfiguration();
        if (agentConfiguration.getArchiveExtractionDepth() > 0) {
            ArchiveExtractor archiveExtractor = new ArchiveExtractor(agentConfiguration.getArchiveIncludes(), agentConfiguration.getArchiveExcludes(),
                    agentConfiguration.getExcludes(), agentConfiguration.isArchiveFastUnpack());
            logger.info("Starting Archive Extraction (may take a few minutes)");
            for (String scannerBaseDir : new LinkedHashSet<>(pathsToScan)) {
                unpackDirectory = archiveExtractor.extractArchives(scannerBaseDir, agentConfiguration.getArchiveExtractionDepth(), archiveDirectories);
                if (unpackDirectory != null) {
                    archiveExtraction = true;
                    String parentFileUrl = new File(scannerBaseDir).getParent();
                    logger.debug("Unpack directory: {}, parent file: {}", unpackDirectory, parentFileUrl);
                    archiveToBaseDirMap.put(unpackDirectory, parentFileUrl);
                    pathsToScan.add(unpackDirectory);
                    if (!projectConfiguration.getAppPathsToDependencyDirs().containsKey(FSAConfiguration.DEFAULT_KEY)) {
                        projectConfiguration.getAppPathsToDependencyDirs().put(FSAConfiguration.DEFAULT_KEY, new HashSet<>());
                    }
                    projectConfiguration.getAppPathsToDependencyDirs().get(FSAConfiguration.DEFAULT_KEY).add(unpackDirectory);
                }
            }
        }

        // create dependencies from files - first project is always the default one
        logger.info("Starting analysis");
        // Create LinkedHashMap in order to save the order of the projects. In this way the first project will be always the main project.
        Map<AgentProjectInfo, Path> allProjects = new LinkedHashMap<>();
        Map<AgentProjectInfo, LinkedList<ViaComponents>> allProjectsToViaComponents = new LinkedHashMap<>();
        AgentProjectInfo mainProject = new AgentProjectInfo();
        allProjects.put(mainProject, null);
        allProjectsToViaComponents.put(mainProject, new LinkedList<>());
        String[] excludes = agentConfiguration.getExcludes();

        logger.info("Scanning directories {} for matching Files (may take a few minutes)", pathsToScan);
        logger.info("Included file types: {}", String.join(Constants.COMMA, agentConfiguration.getIncludes()));
        logger.info("Excluded file types: {}", String.join(Constants.COMMA, agentConfiguration.getExcludes()));
        String[] resolversIncludesPattern = createResolversIncludesPattern(dependencyResolutionService.getDependencyResolvers());

        Map<File, Collection<String>> fileMapBeforeResolve = new FilesUtils().fillFilesMap(pathsToScan, resolversIncludesPattern, agentConfiguration.getExcludes(),
                agentConfiguration.isFollowSymlinks(), agentConfiguration.getGlobCaseSensitive());
        Set<String> allFiles = fileMapBeforeResolve.entrySet().stream().flatMap(folder -> folder.getValue().stream()).collect(Collectors.toSet());

        final int[] totalDependencies = {0};
        boolean isIgnoreSourceFiles = false;
        if (enableImpactAnalysis && iaLanguage != null) {
            for (String appPath : projectConfiguration.getAppPathsToDependencyDirs().keySet()) {
                if (!appPath.equals(FSAConfiguration.DEFAULT_KEY)) {
                    if ((projectConfiguration.getAppPathsToDependencyDirs().get(appPath)).iterator().next() != null) {
                        String pojoAppPath = appPath;
                        allProjectsToViaComponents.get(allProjects.keySet().stream().findFirst().get()).add(new ViaComponents(pojoAppPath, iaLanguage));
                    }
                }
            }
        // the 'allFiles' collection is derived from the manifest-files of each resolver -
        // therefore no need to check again if the files in that collection match the manifest-files of each resolver
        } else if (allFiles.size() > 0) {//(dependencyResolutionService != null && dependencyResolutionService.shouldResolveDependencies(allFiles)) {
            logger.info("Attempting to resolve dependencies");
            isIgnoreSourceFiles = dependencyResolutionService.isIgnoreSourceFiles();

            // get all resolution results
            Collection<ResolutionResult> resolutionResults = new ArrayList<>();
            for (String appPath : projectConfiguration.getAppPathsToDependencyDirs().keySet()) {
                ViaComponents viaComponents = null;
                ViaLanguage impactAnalysisLanguage = null;
                Collection<ResolutionResult> resolutionResult = new LinkedList<>();
                LinkedList<String> pathsList = new LinkedList<>();
                pathsList.addAll(projectConfiguration.getAppPathsToDependencyDirs().get(appPath));
                if ((appPath.equals(FSAConfiguration.DEFAULT_KEY) && projectConfiguration.getAppPathsToDependencyDirs().keySet().size() == 1) ||
                        (!appPath.equals(FSAConfiguration.DEFAULT_KEY) && projectConfiguration.getAppPathsToDependencyDirs().keySet().size() > 1)) {
                    resolutionResult = dependencyResolutionService.resolveDependencies(pathsList, agentConfiguration.getExcludes());
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
                                if (enableImpactAnalysis) {
                                    logger.error("Effective Usage Analysis will not run if the system cannot locate a valid dependency manager and the " +
                                            "-iaLanguage parameter is not specified. In order to run Effective Usage Analysis without a dependency manager specify -iaLanguage java");
                                    Main.exit(StatusCode.ERROR.getValue());
                                    //// TODO: 8/28/2018 as a result of WSE-765 exit using function from main. function signature should be change to throw an exception
                                }
                                break;
                        }
                    }
                } else if (resolutionResult.size() > 1 && enableImpactAnalysis) {
                    logger.info("Effective Usage Analysis will not run if an unsupported resolver is active. Verify that non-supported resolvers are not active");
                    Main.exit(StatusCode.ERROR.getValue());
                }
                if (impactAnalysisLanguage != null) {
                    viaComponents = new ViaComponents(appPath, impactAnalysisLanguage);
                }
                // TODO: Check why is result = null in the loop
                resolutionResult.removeIf(Objects::isNull);
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
                            if ((((DependencyType.MAVEN.equals(result.getDependencyType()) && (!dependencyResolutionService.isMavenAggregateModules() || !dependencyResolutionService.isSbtAggregateModules())) ||
                                    (DependencyType.GRADLE.equals(result.getDependencyType()) && !dependencyResolutionService.isGradleAggregateModules()) ||
                                    (DependencyType.HEX.equals(result.getDependencyType()) && !dependencyResolutionService.isHexAggregateModules()))) &&
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
                            List<String> usedSha1 = new LinkedList<>();
                            dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies, usedSha1));
                        }
                    }
                    if (viaComponents != null) {
                        viaComponents.getDependencies().addAll(dependenciesToVia);
                    }
                }
                resolutionResults.addAll(resolutionResult);
            }

            resolutionResults.stream().forEach(resolutionResult -> logger.debug("total resolved projects = {}", resolutionResult.getResolvedProjects().size()));
            logger.info(MessageFormat.format("Total dependencies found: {0}", totalDependencies[0]));

            // merge additional excludes
            Set<String> allExcludes = resolutionResults.stream().flatMap(resolution -> resolution.getExcludes().stream()).collect(Collectors.toSet());
            allExcludes.addAll(Arrays.stream(agentConfiguration.getExcludes()).collect(Collectors.toList()));

            // change the original excludes with the merged values
            excludes = new String[allExcludes.size()];
            excludes = allExcludes.toArray(excludes);
            dependencyResolutionService = null;
        }

        String[] excludesExtended = excludeFileSystemAgent(excludes);
        logger.info("Scanning directories {} for matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMap = new FilesUtils().fillFilesMap(pathsToScan, agentConfiguration.getIncludes(), excludesExtended,
                agentConfiguration.isFollowSymlinks(), agentConfiguration.getGlobCaseSensitive());
        long filesCount = fileMap.entrySet().stream().flatMap(folder -> folder.getValue().stream()).count();
        totalFiles += filesCount;
        logger.info(MessageFormat.format("Total files found according to the includes/excludes pattern: {0}", totalFiles));
        DependencyCalculator dependencyCalculator = new DependencyCalculator(showProgressBar);
        final Collection<DependencyInfo> filesDependencies = new LinkedList<>();

        if (!isIgnoreSourceFiles) {
            filesDependencies.addAll(dependencyCalculator.createDependencies(
                    projectConfiguration.isScmConnector(), totalFiles, fileMap, agentConfiguration.getExcludedCopyrights(),
                    agentConfiguration.isPartialSha1Match(), agentConfiguration.isCalculateHints(),
                    agentConfiguration.isCalculateMd5()));
        }

        if (allProjects.size() == 1) {
            AgentProjectInfo project = allProjects.keySet().stream().findFirst().get();
            project.getDependencies().addAll(filesDependencies);
            /// TODO: 8/14/2018 support multi module project with via
          /*  if (enableImpactAnalysis) {
                for (LinkedList<ViaComponents> viaComponentsList : allProjectsToViaComponents.values()) {
                    for (ViaComponents viaComponents : viaComponentsList) {
                        for (DependencyInfo dependencyInfo : filesDependencies) {
                            if (dependencyInfo.getSystemPath().equals(viaComponents.getAppPath())) {
                                viaComponents.getDependencies().add(dependencyInfo);
                            }
                        }
                    }
                }
            }*/
        } else {
            // Sort the projects by length of paths (from the longest to the shortest) in order to add filesDependencies to the most appropriate project
            // Example: project1 path: C:\Users\file\Data; project2 path: C:\Users\file\Data\folder; file dependency path: C:\Users\file\Data\folder\a.jar
            // Before sorting, the file dependency will be in project1. After sorting, the file dependency will be in project2.
            List<Map.Entry<AgentProjectInfo, Path>> entriesList = new ArrayList<>();
            allProjects.entrySet().forEach(entry -> {
                if (entry.getValue() != null) {
                    entriesList.add(entry);
                }
            });
            entriesList.sort(Map.Entry.comparingByValue());
            Collections.reverse(entriesList);
            Map<AgentProjectInfo, Path> result = new LinkedHashMap<>();
            entriesList.forEach(entry -> result.put(entry.getKey(), entry.getValue()));

            // remove files from handled projects
            result.entrySet().forEach(project -> {
                Collection<DependencyInfo> projectDependencies = filesDependencies.stream()
                        .filter(dependencyInfo -> project.getValue() != null && dependencyInfo.getSystemPath().contains(project.getValue().toString())).collect(Collectors.toList());
                project.getKey().getDependencies().addAll(projectDependencies);
                filesDependencies.removeAll(projectDependencies);
            });

            // create new projects if necessary
            if (!isIgnoreSourceFiles && filesDependencies.size() > 0) {
                projectConfiguration.getScannerBaseDirs().stream().forEach(directory -> {
                    List<Path> subDirectories;
                    // check all folders

                    String[] includesAll = {Constants.PATTERN};
                    subDirectories = new FilesUtils().getSubDirectories(directory, includesAll, null, agentConfiguration.isFollowSymlinks(),
                            agentConfiguration.getGlobCaseSensitive());
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
                                subProject.getDependencies().addAll(filesDependencies);
                                filesDependencies.removeAll(projectDependencies);
                            }
                        }
                    });
                });
                // Add the rest of the files dependencies to the main project
                if (!filesDependencies.isEmpty()) {
                    AgentProjectInfo subProject = allProjects.entrySet().stream().findFirst().get().getKey();
                    subProject.getDependencies().addAll(filesDependencies);
                }
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

    /* --- Private methods --- */

    private String[] createResolversIncludesPattern(Collection<AbstractDependencyResolver> dependencyResolvers) {
        Collection<String> resultIncludes = new ArrayList<>();
        // TODO - check if can be done with lambda
        for (AbstractDependencyResolver dependencyResolver : dependencyResolvers) {
            for (String manifestFile : dependencyResolver.getManifestFiles()){
                if (!manifestFile.isEmpty()) {
                    resultIncludes.add(Constants.PATTERN + manifestFile);
                }
            }
        }
        String[] resultArray = new String[resultIncludes.size()];
        resultIncludes.toArray(resultArray);
        return resultArray;
    }

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

    private void increaseCount(DependencyInfo dependency, int[] totalDependencies, List<String> usedSha1) {
        sha1 = dependency.getSha1();
        if (usedSha1.contains(sha1)) {
            return;
        }
        usedSha1.add(sha1);
        totalDependencies[0] += dependency.getChildren().size();
        dependency.getChildren().forEach(dependencyInfo -> increaseCount(dependencyInfo, totalDependencies, usedSha1));
    }

    private String[] excludeFileSystemAgent(String[] excludes) {
        String[] allExcludes = excludes == null ? new String[0] : excludes;
        String[] excludesFSA = new String[allExcludes.length + 1];
        System.arraycopy(allExcludes, 0, excludesFSA, 0, allExcludes.length);
        excludesFSA[allExcludes.length] = FSA_FILE;
        return excludesFSA;
    }
}