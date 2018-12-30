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
package org.whitesource.fs;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.*;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.dependency.resolver.ViaMultiModuleAnalyzer;
import org.whitesource.agent.dependency.resolver.docker.DockerResolver;
import org.whitesource.agent.dependency.resolver.gradle.GradleDependencyResolver;
import org.whitesource.agent.dependency.resolver.maven.MavenDependencyResolver;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.dependency.resolver.packageManger.PackageManagerExtractor;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.ScmConfiguration;
import org.whitesource.fs.configuration.ScmRepositoriesParser;
import org.whitesource.scm.ScmConnector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File System Agent.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemAgent {

    /* --- Static members --- */

    private Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);
    private static final String NPM_COMMAND = NpmLsJsonDependencyCollector.isWindows() ? "npm.cmd" : "npm";
    private static final String PACKAGE_LOCK = "package-lock.json";
    private static final String PACKAGE_JSON = "package.json";

    /* --- Members --- */

    private List<String> dependencyDirs;
    private final FSAConfiguration config;

    private boolean projectPerSubFolder;

    /* --- Constructors --- */

    public FileSystemAgent(FSAConfiguration config, List<String> dependencyDirs) {
        this.config = config;
        projectPerSubFolder = config.getRequest().isProjectPerSubFolder();
        if (projectPerSubFolder) {
            this.dependencyDirs = new LinkedList<>();
            for (String directory : dependencyDirs) {
                File file = new File(directory);
                if (file.isDirectory()) {
                    List<Path> directories = new FilesUtils().getSubDirectories(directory, config.getAgent().getProjectPerFolderIncludes(),
                            config.getAgent().getProjectPerFolderExcludes(), config.getAgent().isFollowSymlinks(), config.getAgent().getGlobCaseSensitive());
                    directories.forEach(subDir -> this.dependencyDirs.add(subDir.toString()));
                    //In case no sub-folders were found, put the top folder path as the dependencyDirs.
                    if (CollectionUtils.isEmpty(directories)) {
                        this.dependencyDirs = dependencyDirs;
                    }
                } else if (file.isFile()) {
                    this.dependencyDirs.add(directory);
                } else {
                    logger.warn("{} is not a file nor a directory .", directory);
                }
            }
        } else {
            this.dependencyDirs = dependencyDirs;
        }
    }

    /* --- Overridden methods --- */

    public ProjectsDetails createProjects() {
        ProjectsDetails projects = new ProjectsDetails(new ArrayList<>(), StatusCode.SUCCESS, Constants.EMPTY_STRING);
        // Use FSA a as a package manger extractor for Debian/RPM/Arch Linux/Alpine

        // Check if scanPackageManager==true - This is the first priority and overrides other scans
        if (config.isScanProjectManager()) {
            Collection<AgentProjectInfo> tempProjects = new PackageManagerExtractor().createProjects();
            ProjectsDetails projectsDetails = new ProjectsDetails(tempProjects, StatusCode.SUCCESS, Constants.EMPTY_STRING);
            String projectName = config.getRequest().getProjectName();
            addSingleProjectToProjects(projectsDetails, projectName, projects);
            return projects;
        }

        // Check if docker.scanImages==true - This scans Docker Images, and should not scan any folders, so we exit
        // after the scan is done
        if (config.isScanDockerImages()) {
            Collection<AgentProjectInfo> tempDockerProjects = new DockerResolver(config).resolveDockerImages();
            return new ProjectsDetails(tempDockerProjects, StatusCode.SUCCESS, Constants.EMPTY_STRING);
        }

        if (config.isSetUpMuiltiModuleFile()) {
            ViaMultiModuleAnalyzer viaMultiModuleAnalyzer = new ViaMultiModuleAnalyzer(config.getDependencyDirs().get(0),
                    new MavenDependencyResolver(false, new String[]{Constants.NONE}, false,
                            false, false, false), Constants.TARGET, config.getAnalyzeMultiModule());
            if (viaMultiModuleAnalyzer.getBomFiles().isEmpty()) {
                viaMultiModuleAnalyzer = new ViaMultiModuleAnalyzer(config.getDependencyDirs().get(0),
                        new GradleDependencyResolver(false, false, false, Constants.EMPTY_STRING, new String[]{Constants.NONE},
                                Constants.EMPTY_STRING, false),
                        Constants.BUILD + File.separator + Constants.LIBS, config.getAnalyzeMultiModule());
            }
            if (!viaMultiModuleAnalyzer.getBomFiles().isEmpty()) {
                viaMultiModuleAnalyzer.writeFile();
            } else {
                logger.error("Multi-module analysis could not establish the appPath based on the specified path. Please review the specified -d path.");
                Main.exit(StatusCode.ERROR.getValue());
            }
            logger.info("The multi-module analysis setup file was created successfully.");
            Main.exit(StatusCode.SUCCESS.getValue());
        }

        // Scan folders and create a project per folder
        if (projectPerSubFolder) {
            if (this.config.getSender().isEnableImpactAnalysis()) {
                logger.warn("Could not executing VIA impact analysis with the 'projectPerFolder' flag");
                return projects;
            }
            Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
            Set<String> setDirs = new HashSet<>(1);

            for (String directory : dependencyDirs) {
                setDirs.add(directory);
                appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);

                ProjectsDetails projectsDetails = getProjects(Collections.singletonList(directory), appPathsToDependencyDirs);
                String projectName = new File(directory).getName();
                addSingleProjectToProjects(projectsDetails, projectName, projects);

                // return on the first project that fails
                if (!projectsDetails.getStatusCode().equals(StatusCode.SUCCESS)) {
                    // return status code if there is a failure
                    return new ProjectsDetails(new ArrayList<>(), projects.getStatusCode(), projects.getDetails());
                }
                appPathsToDependencyDirs.clear();
                setDirs.clear();
            }
            if (CollectionUtils.isEmpty(projects.getProjects())) {
                logger.warn("projectPerFolder = true, No sub-folders were found in project folder, scanning main project folder");
                projectPerSubFolder = false;
            } else {
                return projects;
            }
        }
        // Scan folders and create one project for all folders together
        if (!projectPerSubFolder) { // This 'if' is always true now, but keep it maybe we will do other checks in the future...
            projects = getProjects(dependencyDirs, config.getAppPathsToDependencyDirs());
            if (!projects.getProjects().isEmpty()) {
                AgentProjectInfo projectInfo = projects.getProjects().stream().findFirst().get();
                if (projectInfo.getCoordinates() == null) {
                    // use token or name + version
                    String projectToken = config.getRequest().getProjectToken();
                    if (StringUtils.isNotBlank(projectToken)) {
                        projectInfo.setProjectToken(projectToken);
                    } else {
                        String projectName = config.getRequest().getProjectName();
                        String projectVersion = config.getRequest().getProjectVersion();
                        projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
                    }
                }
            }
            return projects;
        }

        // todo: check for duplicates projects
        return projects;
    }

    /* --- Private methods --- */

    private void addSingleProjectToProjects(ProjectsDetails projectsDetails, String projectName, ProjectsDetails projects) {
        if (projectsDetails == null || projects == null || projectName == null) {
            logger.debug("projectsDetails {} , projects {} , projectName {}", projectsDetails, projectName, projects);
            return;
        }
        if (projectsDetails.getProjects().size() == 1) {
            String projectVersion = config.getRequest().getProjectVersion();
            AgentProjectInfo projectInfo = projectsDetails.getProjects().stream().findFirst().get();
            projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
            LinkedList<ViaComponents> viaComponents = projectsDetails.getProjectToViaComponents().get(projectInfo);
            projects.getProjectToViaComponents().put(projectInfo, viaComponents);
        } else {
            for (AgentProjectInfo projectInfo : projectsDetails.getProjects()) {
                logger.debug("Project not added - {}", projectInfo);
            }
        }
    }

    private ProjectsDetails getProjects(List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs) {
        // create getScm connector
        final StatusCode[] success = new StatusCode[]{StatusCode.SUCCESS};
        String separatorFiles = NpmLsJsonDependencyCollector.isWindows() ? "\\" : "/";
        Collection<String> scmPaths = new ArrayList<>();
        final boolean[] hasScmConnectors = new boolean[1];

        List<ScmConnector> scmConnectors = null;
        if (StringUtils.isNotBlank(config.getScm().getRepositoriesPath())) {
            Collection<ScmConfiguration> scmConfigurations = new ScmRepositoriesParser().parseRepositoriesFile(
                    config.getScm().getRepositoriesPath(), config.getScm().getType(), config.getScm().getPpk(), config.getScm().getUser(), config.getScm().getPass());
            scmConnectors = scmConfigurations.stream()
                    .map(scm -> ScmConnector.create(scm.getType(), scm.getUrl(), scm.getPpk(), scm.getUser(), scm.getPass(), scm.getBranch(), scm.getTag()))
                    .collect(Collectors.toList());
        } else {
            scmConnectors = Arrays.asList(ScmConnector.create(
                    config.getScm().getType(), config.getScm().getUrl(), config.getScm().getPpk(), config.getScm().getUser(),
                    config.getScm().getPass(), config.getScm().getBranch(), config.getScm().getTag()));
        }

        if (scmConnectors != null && scmConnectors.stream().anyMatch(scm -> scm != null)) {
            //scannerBaseDirs.clear();
            scmConnectors.stream().forEach(scmConnector -> {
                if (scmConnector != null) {
                    logger.info("Connecting to SCM");

                    String scmPath = scmConnector.cloneRepository().getPath();
                    Pair<String, StatusCode> result = npmInstallScmRepository(config.getScm().isNpmInstall(), config.getScm().getNpmInstallTimeoutMinutes(),
                            scmConnector, separatorFiles, scmPath);
                    scmPath = result.getKey();
                    success[0] = result.getValue();
                    scmPaths.add(scmPath);
                    scannerBaseDirs.add(scmPath);
                    if (!appPathsToDependencyDirs.containsKey(FSAConfiguration.DEFAULT_KEY)) {
                        appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, new HashSet<>());
                    }
                    appPathsToDependencyDirs.get(FSAConfiguration.DEFAULT_KEY).add(scmPath);
                    hasScmConnectors[0] = true;
                }
            });
        }

        if (StringUtils.isNotBlank(config.getAgent().getError())) {
            logger.error(config.getAgent().getError());
            if (scmConnectors != null) {
                scmConnectors.forEach(scmConnector -> scmConnector.deleteCloneDirectory());
            }
            return new ProjectsDetails(new ArrayList<>(), StatusCode.ERROR, config.getAgent().getError()); // TODO this is within a try frame. Throw an exception instead
        }

        Map<AgentProjectInfo, LinkedList<ViaComponents>> projectToAppPathAndLanguage;
        ViaLanguage viaLanguage = getIaLanguage(config.getRequest().getIaLanguage());
        ProjectConfiguration projectConfiguration = new ProjectConfiguration(config.getAgent(), scannerBaseDirs, appPathsToDependencyDirs, false);
        projectToAppPathAndLanguage = new FileSystemScanner(config.getResolver(), config.getAgent() , config.getSender().isEnableImpactAnalysis(), viaLanguage)
                    .createProjects(projectConfiguration);
        ProjectsDetails projectsDetails = new ProjectsDetails(projectToAppPathAndLanguage, success[0], Constants.EMPTY_STRING);

        // delete all temp scm files
        scmPaths.forEach(directory -> {
            if (directory != null) {
                try {
                    FileUtils.forceDelete(new File(directory));
                } catch (IOException e) {
                    // do nothing
                }
            }
        });
        return projectsDetails;
    }

    private ViaLanguage getIaLanguage(String iaLanguage) {
        ViaLanguage[] values = ViaLanguage.values();
        if (iaLanguage != null) {
            for (ViaLanguage value : values) {
                if (value.toString().toLowerCase().equals(iaLanguage.toLowerCase())) {
                    return value;
                }
            }
        }
        return null;
    }

    private Pair<String, StatusCode> npmInstallScmRepository(boolean scmNpmInstall, int npmInstallTimeoutMinutes, ScmConnector scmConnector,
                                                             String separatorFiles, String pathToCloneRepoFiles) {

        StatusCode success = StatusCode.SUCCESS;
        File packageJson = new File(pathToCloneRepoFiles + separatorFiles + PACKAGE_JSON);
        boolean npmInstallFailed = false;
        if (scmNpmInstall && packageJson.exists()) {
            // execute 'npm install'
            File packageLock = new File(pathToCloneRepoFiles + separatorFiles + PACKAGE_LOCK);
            if (packageLock.exists()) {
                packageLock.delete();
            }
            CommandLineProcess npmInstall = new CommandLineProcess(pathToCloneRepoFiles, new String[]{NPM_COMMAND, Constants.INSTALL});
            logger.info("Found package.json file, executing 'npm install' on {}", scmConnector.getUrl());
            try {
                npmInstall.executeProcessWithoutOutput();
                npmInstall.setTimeoutProcessMinutes(npmInstallTimeoutMinutes);
                if (npmInstall.isErrorInProcess()) {
                    npmInstallFailed = true;
                    logger.error("Failed to run 'npm install' on {}", scmConnector.getUrl());
                }
            } catch (IOException e) {
                npmInstallFailed = true;
                logger.error("Failed to start 'npm install', Please make sure 'npm' is installed. {}", e.getMessage());
                logger.debug("Failed to run 'npm install' command ", e);
            }
            if (npmInstallFailed) {
                // In case of error in 'npm install', delete and clone the repository to prevent wrong output
                success = StatusCode.PRE_STEP_FAILURE;
                scmConnector.deleteCloneDirectory();
                pathToCloneRepoFiles = scmConnector.cloneRepository().getPath();
            }
        }
        return new Pair<>(pathToCloneRepoFiles, success);
    }
}