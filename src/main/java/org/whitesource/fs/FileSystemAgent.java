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
import org.whitesource.agent.CommandLineAgent;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.fs.configuration.ScmConfiguration;
import org.whitesource.fs.configuration.ScmRepositoriesParser;
import org.whitesource.scm.ScmConnector;

import java.io.File;
import java.io.IOException;
import java.io.IOException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * File System Agent.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemAgent extends CommandLineAgent {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);

    private static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";
    private static final String EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX = ",";
    private static final int DEFAULT_ARCHIVE_DEPTH = 0;

    private static final String NPM_COMMAND = NpmLsJsonDependencyCollector.isWindows() ? "npm.cmd" : "npm";
    private static final String NPM_INSTALL_COMMAND = "install";
    private static final String NPM_INSTALL_OUTPUT_DESTINATION = NpmLsJsonDependencyCollector.isWindows() ? "nul" : "/dev/null";
    private static final String PACKAGE_LOCK = "package-lock.json";
    private static final String NODE_MODULES = "node_modules";
    private static final String PACKAGE_JSON = "package.json";

    private static final String AGENT_TYPE = "fs-agent";
    private static final String VERSION = "version";
    private static final String AGENTS_VERSION = "agentsVersion";

    /* --- Members --- */

    private final List<String> dependencyDirs;
    private final Properties properties;

    private boolean projectPerSubFolder;

    /* --- Constructors --- */

    public FileSystemAgent(Properties config, List<String> dependencyDirs, List<String> offlineRequestFiles) {
        super(config, offlineRequestFiles);
        properties = getProperties();
        projectPerSubFolder = getBooleanProperty(PROJECT_PER_SUBFOLDER, false);
        if (projectPerSubFolder) {
            this.dependencyDirs = new LinkedList<>();
            for (String directory :dependencyDirs) {


                File file = new File(directory);
                if (file.isDirectory()) {
                    String[] directories = getSubDirectories(directory);
                    Arrays.stream(directories).forEach(subDir -> this.dependencyDirs.add(subDir));
                } else if (file.isFile()) {
                    this.dependencyDirs.add(directory);
                }
                else{
                    logger.warn(directory + "is not a file nor a directory .");
                }
            }
        } else {
            this.dependencyDirs = dependencyDirs;
        }
    }

    private String[] getSubDirectories(String directory) {
        try {
            File file = new File(directory);
            String[] files = file.list((current, name) -> new File(current, name).isDirectory());
            if (files == null){
                logger.info("Error getting sub directories from: " + directory);
                return new String[0];
            }
            return files;
        } catch (Exception ex) {
            logger.info("Error getting sub directories from: " + directory, ex);
            return new String[0];
        }
    }

    /* --- Overridden methods --- */

    @Override
    protected Collection<AgentProjectInfo> createProjects() {
        if (projectPerSubFolder) {
            Collection<AgentProjectInfo> projects = new LinkedList<>();
            for (String directory : dependencyDirs) {
                AgentProjectInfo projectInfo = new AgentProjectInfo();
                String projectName = new File(directory).getName();
                String projectVersion = config.getProperty(PROJECT_VERSION_PROPERTY_KEY);
                projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
                projectInfo.setDependencies(getDependencyInfos(Collections.singletonList(directory)));
                projects.add(projectInfo);
            }
            return projects;
        } else {
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            // use token or name + version
            String projectToken = config.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
            if (StringUtils.isNotBlank(projectToken)) {
                projectInfo.setProjectToken(projectToken);
            } else {
                String projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
                String projectVersion = config.getProperty(PROJECT_VERSION_PROPERTY_KEY);
                projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
            }
            projectInfo.setDependencies(getDependencyInfos());

            Collection<AgentProjectInfo> projects = new LinkedList<>();
            // don't use Arrays.asList, might be removed later if no dependencies
            projects.add(projectInfo);
            return projects;
        }
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getAgentVersion() {
        return getResource(AGENTS_VERSION);
    }

    @Override
    protected String getPluginVersion() {
        return getResource(VERSION);
    }

    private String getResource(String propertyName) {
        getProperties();
        String val = (properties.getProperty(propertyName));
        if(StringUtils.isNotBlank(val)){
            return val;
        }
        return "";
    }

    /* --- Private methods --- */

    private Properties getProperties() {
        Properties properties = new Properties();
        try (InputStream stream = Main.class.getResourceAsStream("/project.properties")) {
            properties.load(stream);
        } catch (IOException e) {
            logger.error("Failed to get version ", e);
        }
        return properties;
    }

    private List<DependencyInfo> getDependencyInfos() {
        List<String> scannerBaseDirs = dependencyDirs;
        return getDependencyInfos(scannerBaseDirs);
    }

    private List<DependencyInfo> getDependencyInfos(List<String> scannerBaseDirs) {
        // create scm connector
        String scmType = config.getProperty(SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(SCM_URL_PROPERTY_KEY);
        String username = config.getProperty(SCM_USER_PROPERTY_KEY);
        String password = config.getProperty(SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(SCM_TAG_PROPERTY_KEY);
        String repositoriesFile = config.getProperty(SCM_REPOSITORIES_FILE);
        String privateKey = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        boolean isScmNpmInstall = getBooleanProperty(SCM_NPM_INSTALL, true);
        int npmInstallTimeoutMinutes = getIntProperty(SCM_NPM_INSTALL_TIMEOUT_MINUTES, 15);
        //ScmConnector scmConnector = ScmConnector.create(scmType, url, privateKey, username, password, branch, tag);
        String separatorFiles = NpmLsJsonDependencyCollector.isWindows() ? "\\" : "/";
        Collection<String> scmPaths = new ArrayList<>();
        final boolean[] hasScmConnectors = new boolean[1];

        List<ScmConnector> scmConnectors = null;
        if (StringUtils.isNotBlank(repositoriesFile)){
            Collection<ScmConfiguration> scmConfigurations = ScmRepositoriesParser.parseRepositoriesFile(repositoriesFile , scmType, privateKey, username, password);
            scmConnectors = scmConfigurations.stream()
                    .map(scm -> ScmConnector.create(scm.getType(), scm.getUrl(),scm.getPpk(),scm.getUser(), scm.getPass(), scm.getBranch(), scm.getTag()))
                    .collect(Collectors.toList());
        }else {
            scmConnectors = Arrays.asList(ScmConnector.create(scmType, url, privateKey, username, password, branch, tag));
        }

        if (scmConnectors != null && scmConnectors.stream().anyMatch(scm->scm!=null)) {
            scannerBaseDirs.clear();
            scmConnectors.stream().forEach(scmConnector -> {
                if (scmConnector != null) {
                    logger.info("Connecting to SCM");

                    String scmPath = scmConnector.cloneRepository().getPath();
                    scmPath = npmInstallScmRepository(isScmNpmInstall, npmInstallTimeoutMinutes, scmConnector, separatorFiles, scmPath);
                    scmPaths.add(scmPath);
                    scannerBaseDirs.add(scmPath);
                    hasScmConnectors[0] = true;
                }
            });
        }

        // read all properties
        final String[] includes = config.getProperty(INCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String[] excludes = config.getProperty(EXCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final int archiveExtractionDepth = getIntProperty(ARCHIVE_EXTRACTION_DEPTH_KEY, DEFAULT_ARCHIVE_DEPTH);
        final String[] archiveIncludes = config.getProperty(ARCHIVE_INCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String[] archiveExcludes = config.getProperty(ARCHIVE_EXCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final boolean archiveFastUnpack = getBooleanProperty(ARCHIVE_FAST_UNPACK_KEY, false);
        boolean followSymlinks = getBooleanProperty(FOLLOW_SYMBOLIC_LINKS, true);
        // check scan partial sha1s (false by default)
        boolean partialSha1Match = getBooleanProperty(PARTIAL_SHA1_MATCH_KEY, false);

        boolean calculateHints = getBooleanProperty(CALCULATE_HINTS, false);
        boolean calculateMd5 = getBooleanProperty(CALCULATE_MD5, false);

        // glob case sensitive
        final String globCaseSensitiveValue = config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY);
        boolean globCaseSensitive = false;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase("true") || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
            } else if (globCaseSensitiveValue.equalsIgnoreCase("false") || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
            } else {
                logger.error("Bad {}. Received {}, required true/false or y/n", CASE_SENSITIVE_GLOB_PROPERTY_KEY, globCaseSensitiveValue);
                if (scmConnectors != null) {
                    scmConnectors.forEach(scmConnector -> scmConnector.deleteCloneDirectory());
                }
                System.exit(-1); // TODO this is within a try frame. Throw an exception instead
            }
        }

        final String excludedCopyrightsValue = config.getProperty(EXCLUDED_COPYRIGHT_KEY, "");
        // get excluded copyrights
        Collection<String> excludedCopyrights = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludedCopyrights.remove("");

        boolean showProgressBar = getBooleanProperty(SHOW_PROGRESS_BAR, true);
        List<DependencyInfo> dependencyInfos = new FileSystemScanner(showProgressBar, new DependencyResolutionService(config)).createDependencies(
                scannerBaseDirs, hasScmConnectors[0], includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights,
                partialSha1Match, calculateHints, calculateMd5);

        // delete all temp scm files
        scmPaths.forEach(directory->{
            if (directory != null) {
                try {
                    FileUtils.forceDelete(new File(directory));
                } catch (IOException e) {
                    // do nothing
                }
            }
        });
        return  dependencyInfos;
    }

    private String npmInstallScmRepository(boolean scmNpmInstall, int npmInstallTimeoutMinutes, ScmConnector scmConnector,
                                           String separatorFiles, String pathToCloneRepoFiles) {
        File packageJson = new File(pathToCloneRepoFiles + separatorFiles + PACKAGE_JSON);
        boolean npmInstallFailed = false;
        if (scmNpmInstall && packageJson.exists()) {
            try {
                // execute 'npm install'
                File packageLock = new File(pathToCloneRepoFiles + separatorFiles + PACKAGE_LOCK);
                if (packageLock.exists()) {
                    packageLock.delete();
                }
                ProcessBuilder pb = new ProcessBuilder(NPM_COMMAND, NPM_INSTALL_COMMAND);
                pb.directory(new File(pathToCloneRepoFiles));
                // redirect the output to avoid output of npm install by operating system
                File npmOutput = new File(NPM_INSTALL_OUTPUT_DESTINATION);
                pb.redirectOutput(npmOutput);
                pb.redirectError(npmOutput);
                logger.info("Found package.json file, executing 'npm install' on {}", scmConnector.getUrl());
                try {
                    Process npmInstallProcess = pb.start();
                    npmInstallProcess.waitFor(npmInstallTimeoutMinutes, TimeUnit.MINUTES);
                    if (npmInstallProcess.exitValue() != 0) {
                        npmInstallFailed = true;
                        logger.error("Failed to run 'npm install' on {}", scmConnector.getUrl());
                    }
                } catch (InterruptedException e) {
                    npmInstallFailed = true;
                    logger.error("'npm install' was interrupted {}", e);
                }
            } catch (IOException e) {
                npmInstallFailed = true;
                logger.error("Failed to start 'npm install' {}", e);
            }
        }
        if (npmInstallFailed) {
            // In case of error in 'npm install', delete and clone the repository to prevent wrong output
            this.prepStepStatusCode = StatusCode.PREP_STEP_FAILURE;
            scmConnector.deleteCloneDirectory();
            pathToCloneRepoFiles = scmConnector.cloneRepository().getPath();
        }
        return pathToCloneRepoFiles;
    }

}