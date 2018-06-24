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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.ViaComponents;
import org.whitesource.agent.ViaLanguage;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.dependency.resolver.docker.DockerResolver;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.dependency.resolver.packageManger.PackageManagerExtractor;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
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

    private final List<String> dependencyDirs;
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
                } else if (file.isFile()) {
                    this.dependencyDirs.add(directory);
                } else {
                    logger.warn(directory + "is not a file nor a directory .");
                }
            }
        } else {
            this.dependencyDirs = dependencyDirs;
        }
    }

    /* --- Overridden methods --- */

    public ProjectsDetails createProjects() {
        ProjectsDetails projects;
        if (projectPerSubFolder) {
            if (this.config.getSender().isEnableImpactAnalysis()) {
                logger.warn("Could not executing VIA impact analysis with the 'projectPerFolder' flag");
            }
            projects = new ProjectsDetails(new ArrayList<>(), StatusCode.SUCCESS, Constants.EMPTY_STRING);
            for (String directory : dependencyDirs) {
                Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
                Set<String> setDirs = new HashSet<>();
                setDirs.add(directory);
                appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);
                ProjectsDetails projectsDetails = getProjects(Collections.singletonList(directory), appPathsToDependencyDirs);
                if (projectsDetails.getProjects().size() == 1) {
                    String projectName = new File(directory).getName();
                    String projectVersion = config.getRequest().getProjectVersion();
                    AgentProjectInfo projectInfo = projectsDetails.getProjects().stream().findFirst().get();
                    projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
                    projects.getProjectToViaComponents().put(projectInfo, projectsDetails.getProjectToViaComponents().get(projectInfo));
                }
                // return on the first project that fails
                if (!projectsDetails.getStatusCode().equals(StatusCode.SUCCESS)) {
                    // return status code if there is a failure
                    return new ProjectsDetails(new ArrayList<>(), projects.getStatusCode(), projects.getDetails());
                }
            }
            return projects;
        } else {
            projects = getProjects(dependencyDirs, config.getAppPathsToDependencyDirs());
            if (projects.getProjects().size() > 0) {
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

            // todo: check for duplicates projects
            return projects;
        }
    }

    /* --- Private methods --- */

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

        Collection<AgentProjectInfo> projects = null;
        Map<AgentProjectInfo, LinkedList<ViaComponents>> projectToAppPathAndLanguage = null;
        ProjectsDetails projectsDetails;
        // Use FSA a as a package manger extractor for Debian/RPM/Arch Linux/Alpine
        if (config.isScanProjectManager()) {
            projects = new PackageManagerExtractor().createProjects();
            projectsDetails = new ProjectsDetails(projects, success[0], Constants.EMPTY_STRING);
        } else if (config.isScanDockerImages()) {
            projects = new DockerResolver(config).resolveDockerImages();
            projectsDetails = new ProjectsDetails(projects, success[0], Constants.EMPTY_STRING);
        } else {
            ViaLanguage viaLanguage = getIaLanguage(config.getRequest().getIaLanguage());
            projectToAppPathAndLanguage = new FileSystemScanner(config.getResolver(), config.getAgent() , config.getSender().isEnableImpactAnalysis(), viaLanguage)
                    .createProjects(scannerBaseDirs, appPathsToDependencyDirs, hasScmConnectors[0]);
            projectsDetails = new ProjectsDetails(projectToAppPathAndLanguage, success[0], Constants.EMPTY_STRING);
        }
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
                logger.error("Failed to start 'npm install' {}", e);
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