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
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.ConfigurationValidation;
import org.whitesource.fs.configuration.ScmConfiguration;
import org.whitesource.fs.configuration.ScmRepositoriesParser;
import org.whitesource.scm.ScmConnector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * File System Agent.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemAgent {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);
    private static final String EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX = ",";
    private static final String NPM_COMMAND = NpmLsJsonDependencyCollector.isWindows() ? "npm.cmd" : "npm";
    private static final String NPM_INSTALL_COMMAND = "install";
    private static final String PACKAGE_LOCK = "package-lock.json";
    private static final String PACKAGE_JSON = "package.json";

    /* --- Members --- */

    private final List<String> dependencyDirs;
    private final FSAConfiguration config;
    private final ConfigurationValidation configurationValidation;

    private boolean projectPerSubFolder;

    /* --- Constructors --- */

    public FileSystemAgent(FSAConfiguration config, List<String> dependencyDirs) {
        this.config = config;
        this.configurationValidation = new ConfigurationValidation();
        projectPerSubFolder = config.isProjectPerSubFolder();
        if (projectPerSubFolder) {
            this.dependencyDirs = new LinkedList<>();
            for (String directory : dependencyDirs) {

                File file = new File(directory);
                if (file.isDirectory()) {
                    List<Path> directories = FilesUtils.getSubDirectories(directory);
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
        ProjectsDetails projects = null;
        if (projectPerSubFolder) {
            for (String directory : dependencyDirs) {
                projects = getProjects(Collections.singletonList(directory));
                if (projects.getProjects().size() == 1) {
                    String projectName = new File(directory).getName();
                    String projectVersion = config.getProjectVersion();
                    projects.getProjects().stream().findFirst().get().setCoordinates(new Coordinates(null, projectName, projectVersion));
                }
                // return on the first project that fails
                if (!projects.getStatusCode().equals(StatusCode.SUCCESS)) {
                    // return status code if there is a failure
                    return new ProjectsDetails(new ArrayList<>(), projects.getStatusCode(), projects.getDetails());
                }
            }
            return projects;
        } else {
            projects = getProjects(dependencyDirs);
            if (projects.getProjects().size() > 0) {
                AgentProjectInfo projectInfo = projects.getProjects().stream().findFirst().get();
                if (projectInfo.getCoordinates() == null) {
                    // use token or name + version
                    String projectToken = config.getProjectToken();
                    if (StringUtils.isNotBlank(projectToken)) {
                        projectInfo.setProjectToken(projectToken);
                    } else {
                        String projectName = config.getProjectName();
                        String projectVersion = config.getProjectVersion();
                        projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
                    }
                }
            }

            // todo: check for duplicates projects
            return projects;
        }
    }

    /* --- Private methods --- */

    private ProjectsDetails getProjects(List<String> scannerBaseDirs) {
        // create scm connector
        final StatusCode[] success = new StatusCode[]{StatusCode.SUCCESS};
        String scmType = config.getScmType();
        String url = config.getScmUrl();
        String username = config.getScmUsername();
        String password = config.getScmPassword();
        String branch = config.getScmBranch();
        String tag = config.getScmTag();
        String repositoriesFile = config.getScmRepositoriesFile();
        String privateKey = config.getScmPrivateKey();
        boolean isScmNpmInstall = config.isScmNpmInstall();
        int npmInstallTimeoutMinutes = config.getScmNpmInstallTimeoutMinutes();
        String separatorFiles = NpmLsJsonDependencyCollector.isWindows() ? "\\" : "/";
        Collection<String> scmPaths = new ArrayList<>();
        final boolean[] hasScmConnectors = new boolean[1];

        List<ScmConnector> scmConnectors = null;
        if (StringUtils.isNotBlank(repositoriesFile)) {
            Collection<ScmConfiguration> scmConfigurations = ScmRepositoriesParser.parseRepositoriesFile(repositoriesFile, scmType, privateKey, username, password);
            scmConnectors = scmConfigurations.stream()
                    .map(scm -> ScmConnector.create(scm.getType(), scm.getUrl(), scm.getPpk(), scm.getUser(), scm.getPass(), scm.getBranch(), scm.getTag()))
                    .collect(Collectors.toList());
        } else {
            scmConnectors = Arrays.asList(ScmConnector.create(scmType, url, privateKey, username, password, branch, tag));
        }

        if (scmConnectors != null && scmConnectors.stream().anyMatch(scm -> scm != null)) {
            //scannerBaseDirs.clear();
            scmConnectors.stream().forEach(scmConnector -> {
                if (scmConnector != null) {
                    logger.info("Connecting to SCM");

                    String scmPath = scmConnector.cloneRepository().getPath();
                    Pair<String, StatusCode> result = npmInstallScmRepository(isScmNpmInstall, npmInstallTimeoutMinutes, scmConnector, separatorFiles, scmPath);
                    scmPath = result.getKey();
                    success[0] = result.getValue();
                    scmPaths.add(scmPath);
                    scannerBaseDirs.add(scmPath);
                    hasScmConnectors[0] = true;
                }
            });
        }

        // read all properties
        final String[] includes = config.getIncludes();
        final String[] excludes = config.getExcludes();
        final int archiveExtractionDepth = config.getArchiveExtractionDepth();
        final String[] archiveIncludes = config.getArchiveIncludes();
        final String[] archiveExcludes = config.getArchiveExcludes();
        final boolean archiveFastUnpack = config.isArchiveFastUnpack();
        boolean followSymlinks = config.isFollowSymlinks();
        // check scan partial sha1s (false by default)
        boolean partialSha1Match = config.isPartialSha1Match();

        boolean calculateHints = config.isCalculateHints();
        boolean calculateMd5 = config.isCalculateMd5();

        // glob case sensitive
        final String globCaseSensitiveValue = config.getGlobCaseSensitiveValue();
        boolean globCaseSensitive = false;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase("true") || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
            } else if (globCaseSensitiveValue.equalsIgnoreCase("false") || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
            } else {
                String error = "Bad " + CASE_SENSITIVE_GLOB_PROPERTY_KEY + ". Received " + globCaseSensitiveValue + ", required true/false or y/n";
                logger.error(error);
                if (scmConnectors != null) {
                    scmConnectors.forEach(scmConnector -> scmConnector.deleteCloneDirectory());
                }
                return new ProjectsDetails(new ArrayList<>(), StatusCode.ERROR, error); // TODO this is within a try frame. Throw an exception instead
            }
        }

        final String excludedCopyrightsValue = config.getExcludedCopyrightsValue();
        // get excluded copyrights
        Collection<String> excludedCopyrights = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludedCopyrights.remove("");

        boolean showProgressBar = config.isShowProgressBar();
        Collection<AgentProjectInfo> projects = new FileSystemScanner(showProgressBar, new DependencyResolutionService(config.getResolverConfiguration())).createProjects(
                scannerBaseDirs, hasScmConnectors[0], includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights,
                partialSha1Match, calculateHints, calculateMd5);

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
        return new ProjectsDetails(projects, success[0], "");
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
            CommandLineProcess npmInstall = new CommandLineProcess(pathToCloneRepoFiles, new String[]{NPM_COMMAND, NPM_INSTALL_COMMAND});
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
                success = StatusCode.PREP_STEP_FAILURE;
                scmConnector.deleteCloneDirectory();
                pathToCloneRepoFiles = scmConnector.cloneRepository().getPath();
            }
        }
        return new Pair<>(pathToCloneRepoFiles, success);
    }
}