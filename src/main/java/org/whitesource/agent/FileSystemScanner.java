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
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.MemoryUsageHelper;
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
    public static final String JAVA_SCRIPT = "javaScript";
    public static final String JAVA = "java";
    private static String FSA_FILE = "**/*whitesource-fs-agent-*.*jar";

    /* --- Members --- */

    private final boolean isSeparateProjects;
    private final AgentConfiguration agent;
    private final boolean showProgressBar;
    private boolean enableImpactAnalysis;
    private DependencyResolutionService dependencyResolutionService;

    /* --- Constructors --- */

    public FileSystemScanner(ResolverConfiguration resolver, AgentConfiguration agentConfiguration, boolean enableImpactAnalysis) {
        this.dependencyResolutionService = new DependencyResolutionService(resolver);
        this.isSeparateProjects = dependencyResolutionService.isSeparateProjects();
        this.agent = agentConfiguration;
        this.showProgressBar = agentConfiguration.isShowProgressBar();
        this.enableImpactAnalysis = enableImpactAnalysis;
    }

    /* --- Public methods --- */

    /**
     * This method is usually called from outside by different other tools
     * @param scannerBaseDirs folders to scan
     * @param scmConnector use scmConnector
     * @param includes includes glob patterns
     * @param excludes excludes glob patterns
     * @param globCaseSensitive global case sensitive
     * @param archiveExtractionDepth depth of recursive extraction
     * @param archiveIncludes includes glob patterns for extraction
     * @param archiveExcludes exclude glob patterns for extraction
     * @param archiveFastUnpack use fast extraction
     * @param followSymlinks use followSymlinks
     * @param excludedCopyrights use excludedCopyrights
     * @param partialSha1Match use partialSha1Match
     * @return
     */
    public List<DependencyInfo> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                               String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                               String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                               Collection<String> excludedCopyrights, boolean partialSha1Match) {
        Collection<AgentProjectInfo> projects = createProjects(scannerBaseDirs, scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights, partialSha1Match,
                false, false, null).keySet();
        return projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
    }

    public Map<AgentProjectInfo, String> createProjects(List<String> scannerBaseDirs, boolean hasScmConnector, String npmAccessToken) {
        return createProjects(scannerBaseDirs, hasScmConnector, agent.getIncludes(), agent.getExcludes(), agent.getGlobCaseSensitive(), agent.getArchiveExtractionDepth(),
        agent.getArchiveIncludes(), agent.getArchiveExcludes(), agent.isArchiveFastUnpack(), agent.isFollowSymlinks(),
                agent.getExcludedCopyrights(), agent.isPartialSha1Match(), agent.isCalculateHints(), agent.isCalculateMd5(), npmAccessToken);
    }


//        public Collection<AgentProjectInfo> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
        public Map<AgentProjectInfo, String> createProjects(List<String> scannerBaseDirs, boolean scmConnector,
                                                       String[] includes, String[] excludes, boolean globCaseSensitive, int archiveExtractionDepth,
                                                       String[] archiveIncludes, String[] archiveExcludes, boolean archiveFastUnpack, boolean followSymlinks,
                                                       Collection<String> excludedCopyrights, boolean partialSha1Match, boolean calculateHints, boolean calculateMd5, String npmAccessToken) {

        MemoryUsageHelper.SystemStats systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());

        // get canonical paths
        Set<String> pathsToScan = getCanonicalPaths(scannerBaseDirs);

        // todo: consider adding exit since this can be called from other components
        //validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;

        String unpackDirectory = null;
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
                }
            }
        }

        // create dependencies from files - first project is always the default one
        logger.info("Starting Analysis");
        Map<AgentProjectInfo, Path> allProjects = new HashMap<>();
        AgentProjectInfo mainProject = new AgentProjectInfo();
        allProjects.put(mainProject, null);

        logger.info("Scanning Directories {} for Matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMapBeforeResolve = FilesUtils.fillFilesMap(pathsToScan, includes, excludes, followSymlinks, globCaseSensitive);
        Set<String> allFiles = fileMapBeforeResolve.entrySet().stream().flatMap(folder -> folder.getValue().stream()).collect(Collectors.toSet());

        Map<Collection<AgentProjectInfo>, String> projectsResult = new HashMap<>();

        boolean isDependenciesOnly = false;
        String impactAnalysisLanguage = null;
        if (dependencyResolutionService != null && dependencyResolutionService.shouldResolveDependencies(allFiles)) {
            logger.info("Attempting to resolve dependencies");
            isDependenciesOnly = dependencyResolutionService.isDependenciesOnly();

            // get all resolution results
            Collection<ResolutionResult> resolutionResults = dependencyResolutionService.resolveDependencies(pathsToScan, excludes, npmAccessToken);
            if (resolutionResults.size() == 1) {
                DependencyType dependencyType = resolutionResults.stream().findFirst().get().getDependencyType();
                // validate scanned language and set the
                switch (dependencyType) {
                    case NPM:
                    case BOWER:
                        impactAnalysisLanguage = JAVA_SCRIPT;
                        break;
                    case MAVEN:
                        impactAnalysisLanguage = JAVA;
                        break;
                    default: break;
                }
            } else if (resolutionResults.size() > 1 && enableImpactAnalysis){
                logger.info("Impact analysis won't run, more than one language detected");
            }


            // add all resolved dependencies
            final int[] totalDependencies = {0};
            resolutionResults.stream().forEach( result->
            {
                Map<AgentProjectInfo,Path> projects = result.getResolvedProjects();
                projects.entrySet().stream().forEach(project -> {
                    Collection<DependencyInfo> dependencies = project.getKey().getDependencies();

                    // do not add projects with no dependencies
                    if(!dependencies.isEmpty()) {
                        AgentProjectInfo currentProject;

                        // if it is single project threat it as the main
                        if(dependencyResolutionService.isSeparateProjects()) {
                            if (result.getDependencyType().equals(DependencyType.MAVEN) && result.getResolvedProjects().size() > 1) {
                                allProjects.put(project.getKey(), project.getValue());
                            }else{
                                currentProject = allProjects.keySet().stream().findFirst().get();
                                currentProject.getDependencies().addAll(project.getKey().getDependencies());
                            }
                        }else {
                            //allProjects.put(project.getKey(), project.getValue());
                            currentProject = allProjects.keySet().stream().findFirst().get();
                            currentProject.getDependencies().addAll(project.getKey().getDependencies());
                        }
                        totalDependencies[0] += dependencies.size();
                        dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies));
                    }
                });
            });
            logger.info(MessageFormat.format("Total dependencies Found: {0}", totalDependencies[0]));

            // merge additional excludes
            Set<String> allExcludes = resolutionResults.stream().flatMap(resolution -> resolution.getExcludes().stream()).collect(Collectors.toSet());
            allExcludes.addAll(Arrays.stream(excludes).collect(Collectors.toList()));

            // change the original excludes with the merged values
            excludes = new String[allExcludes.size()];
            excludes = allExcludes.toArray(excludes);
            dependencyResolutionService = null;
        }

        String[] excludesExtended = excludeFileSystemAgent(excludes);
        logger.info("Scanning Directories {} for Matching Files (may take a few minutes)", pathsToScan);
        Map<File, Collection<String>> fileMap = FilesUtils.fillFilesMap(pathsToScan, includes, excludesExtended, followSymlinks, globCaseSensitive);
        long filesCount = fileMap.entrySet().stream().flatMap(folder -> folder.getValue().stream()).count();
        totalFiles += filesCount;
        logger.info(MessageFormat.format("Total Files Found: {0}", totalFiles));
        DependencyCalculator dependencyCalculator = new DependencyCalculator(showProgressBar);
        final Collection<DependencyInfo> filesDependencies = new LinkedList<>();

        if (!isDependenciesOnly) {
            filesDependencies.addAll(dependencyCalculator.createDependencies(
                    scmConnector, totalFiles, fileMap, excludedCopyrights, partialSha1Match, calculateHints, calculateMd5));
        }

        if (allProjects.size() == 1 ) {
            AgentProjectInfo project = allProjects.keySet().stream().findFirst().get();
            project.getDependencies().addAll(filesDependencies);
        } else {
            // remove files from handled projects
            allProjects.entrySet().stream().forEach(project -> {
                    Collection<DependencyInfo> projectDependencies = filesDependencies.stream()
                            .filter(dependencyInfo -> project.getValue()!=null && dependencyInfo.getSystemPath().contains(project.getValue().toString())).collect(Collectors.toList());
                    project.getKey().getDependencies().addAll(projectDependencies);
                    filesDependencies.removeAll(projectDependencies);
            });

            // create new projects if necessary
            if (!isDependenciesOnly && filesDependencies.size() > 0) {
                scannerBaseDirs.stream().forEach(directory -> {
                    List<Path> subDirectories = new FilesUtils().getSubDirectories(directory);
                    subDirectories.forEach(subFolder -> {
                        if (filesDependencies.size() > 0) {
                            List<DependencyInfo> projectDependencies = filesDependencies.stream().
                                    filter(dependencyInfo -> dependencyInfo.getSystemPath().contains(subFolder.toString())).collect(Collectors.toList());
                            if (!projectDependencies.isEmpty()) {
                                AgentProjectInfo subProject;
                                if(isSeparateProjects) {
                                    subProject = new AgentProjectInfo();
                                    allProjects.put(subProject, null);
                                    subProject.setCoordinates(new Coordinates(null, subFolder.toFile().getName(), null));
                                }else{
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
                            String newSystemPath = systemPath.replace(key, archiveToBaseDirMap.get(key)).replaceAll(ArchiveExtractor.DEPTH_REGEX, "");
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
           }}
        }
        logger.info("Finished Analyzing Files");

        systemStats = MemoryUsageHelper.getMemoryUsage();
        logger.debug(systemStats.toString());
        // Set language for VIA project
        //TODO - change in future when there is more than one project scanned by VIA in the same run
        Map<AgentProjectInfo, String> projectsToLanguages = new HashMap<>();
        for (AgentProjectInfo agentProjectInfo : allProjects.keySet()) {
            if (impactAnalysisLanguage != null) {
                projectsToLanguages.put(agentProjectInfo, impactAnalysisLanguage);
            } else {
                projectsToLanguages.put(agentProjectInfo, null);
            }
        }
        return projectsToLanguages;
    }

    /* --- Private methods --- */

    private Set<String> getCanonicalPaths(List<String> scannerBaseDirs) {
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