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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ViaLanguage;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.agent.utils.WsStringUtils;
import org.whitesource.fs.configuration.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.whitesource.agent.Constants.COLON;
import static org.whitesource.agent.Constants.EMPTY_STRING;
import static org.whitesource.agent.client.ClientConstants.SERVICE_URL_KEYWORD;

/**
 * Author: eugen.horovitz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FSAConfiguration {

    /* --- Static members --- */

    public static Collection<String> ignoredWebProperties = Arrays.asList(
            ConfigPropertyKeys.SCM_REPOSITORIES_FILE, ConfigPropertyKeys.LOG_LEVEL_KEY, ConfigPropertyKeys.FOLLOW_SYMBOLIC_LINKS, ConfigPropertyKeys.SHOW_PROGRESS_BAR, ConfigPropertyKeys.PROJECT_CONFIGURATION_PATH, ConfigPropertyKeys.SCAN_PACKAGE_MANAGER, ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH,
            ConfigPropertyKeys.ENDPOINT_ENABLED, ConfigPropertyKeys.ENDPOINT_PORT, ConfigPropertyKeys.ENDPOINT_CERTIFICATE, ConfigPropertyKeys.ENDPOINT_PASS, ConfigPropertyKeys.ENDPOINT_SSL_ENABLED, ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, ConfigPropertyKeys.OFFLINE_ZIP_PROPERTY_KEY,
            ConfigPropertyKeys.OFFLINE_PRETTY_JSON_KEY, ConfigPropertyKeys.WHITESOURCE_CONFIGURATION, ConfigPropertyKeys.SCANNED_FOLDERS);

    public static final int VIA_DEFAULT_ANALYSIS_LEVEL = 1;
    public static final String DEFAULT_KEY = "defaultKey";
    public static final String APP_PATH = "-appPath";
    private static final String FALSE = "false";
    private static final String INFO = "info";
    public static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";
    public static final int DEFAULT_ARCHIVE_DEPTH = 0;
    private static final String NONE = "(none)";
    public static final String WHITE_SOURCE_DEFAULT_FOLDER_PATH = ".";
    public static final String PIP = "pip";
    public static final String PYTHON = "python";
    public static final String SERVICE_URL_REGEX = "http[s]?:\\/\\/[-\\w\\/=:.]*\\/agent";

    public static final int DEFAULT_PORT = 443;
    public static final boolean DEFAULT_SSL = true;
    private static final boolean DEFAULT_ENABLED = false;

    @FSAConfigProperty
    private boolean projectPerFolder;
    @FSAConfigProperty
    private int connectionTimeOut;


    /* --- Private fields --- */

    private final ScmConfiguration scm;
    @FSAConfigProperty
    private SenderConfiguration sender;
    @FSAConfigProperty
    private final OfflineConfiguration offline;
    @FSAConfigProperty
    private final ResolverConfiguration resolver;
    private final ConfigurationValidation configurationValidation;
    private final EndPointConfiguration endpoint;
    private final RemoteDockerConfiguration remoteDockerConfiguration;

    private final List<String> errors;

    /* --- Private final fields --- */

    private final List<String> offlineRequestFiles;
    @FSAConfigProperty
    private final String fileListPath;
    @FSAConfigProperty
    private List<String> dependencyDirs;
    @FSAConfigProperty
    private final String configFilePath;
    @FSAConfigProperty
    private final AgentConfiguration agent;
    @FSAConfigProperty
    private final RequestConfiguration request;
    private final List<String> requirementsFileIncludes;
    @FSAConfigProperty
    private final boolean scanPackageManager;
    @FSAConfigProperty
    private final boolean scanDockerImages;
    private final boolean scanTarImages;
    private final boolean deleteTarImages;

    private final String scannedFolders;

    @FSAConfigProperty
    private String logLevel;
    private String logContext;
    private boolean useCommandLineProductName;
    private boolean useCommandLineProjectName;
    private List<String> appPaths;
    private Map<String, Set<String>> appPathsToDependencyDirs;
    private String analyzeMultiModule;
    private String xModulePath;
    private boolean setUpMuiltiModuleFile;

    /* --- Constructors --- */

    public FSAConfiguration(FSAConfigProperties config) {
        this(config, null);
    }

    public FSAConfiguration() {
        this(new FSAConfigProperties(), null);
    }

    public FSAConfiguration(String[] args) {
        this(null, args);
    }

    public FSAConfiguration(FSAConfigProperties config, String[] args) {
        configurationValidation = new ConfigurationValidation();
        String projectName;
        errors = new ArrayList<>();
        appPathsToDependencyDirs = new HashMap<>();
        requirementsFileIncludes = new LinkedList<>();
        appPaths = null;
        String apiToken = null;
        String userKey = null;
        String serviceUrl = null;
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        if ((args != null)) {
            // read command line args
            // validate args // TODO use jCommander validators
            // TODO add usage command
            commandLineArgs.parseCommandLine(args);

            if (config == null) {
                analyzeMultiModule = commandLineArgs.analyzeMultiModule;

                // The config file is not necessary if there is the analyzeMultiModule parameter
                if (Boolean.valueOf(commandLineArgs.noConfig)) {
                    config = new FSAConfigProperties();
                    checkCmdArgsWithoutConfig(commandLineArgs);
                } else if (StringUtils.isEmpty(analyzeMultiModule)) {
                    Pair<FSAConfigProperties, List<String>> propertiesWithErrors = readWithError(commandLineArgs.configFilePath, commandLineArgs);
                    errors.addAll(propertiesWithErrors.getValue());
                    config = propertiesWithErrors.getKey();
                } else {
                    config = new FSAConfigProperties();
                }

                if (StringUtils.isNotEmpty(commandLineArgs.project)) {
                    config.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, commandLineArgs.project);
                }
            }

            scannedFolders = config.getProperty(ConfigPropertyKeys.SCANNED_FOLDERS);
            if (scannedFolders != null) {
                String[] libsList = scannedFolders.split(Constants.COMMA);
                // Trim all elements in libsList
                Arrays.stream(libsList).map(String::trim).toArray(unused -> libsList);
                dependencyDirs = Arrays.asList(libsList);
            }

            configFilePath = commandLineArgs.configFilePath;
            config.setProperty(ConfigPropertyKeys.PROJECT_CONFIGURATION_PATH, commandLineArgs.configFilePath);

            //override
            offlineRequestFiles = updateProperties(config, commandLineArgs);
            if (StringUtils.isNotEmpty(commandLineArgs.apiKey)) {
                apiToken = commandLineArgs.apiKey;
            }
            if (StringUtils.isNotEmpty(commandLineArgs.userKey)) {
                userKey = commandLineArgs.userKey;
            }
            if (StringUtils.isNotEmpty(commandLineArgs.wssUrl)) {
                serviceUrl = commandLineArgs.wssUrl;
            }
            projectName = config.getProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY);
            fileListPath = commandLineArgs.fileListPath;
            if (commandLineArgs.dependencyDirs != null && !commandLineArgs.dependencyDirs.isEmpty()) {
                dependencyDirs = commandLineArgs.dependencyDirs;
            }
            appPaths = commandLineArgs.appPath;
            if (StringUtils.isNotBlank(commandLineArgs.whiteSourceFolder)) {
                config.setProperty(ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH, commandLineArgs.whiteSourceFolder);
            }

            // requirements file includes
            requirementsFileIncludes.addAll(commandLineArgs.requirementsFileIncludes);
            if (!requirementsFileIncludes.isEmpty()) {
                String requirements = null;
                for (String requirementFileIncludes : requirementsFileIncludes) {
                    if (requirements == null) {
                        requirements = requirementFileIncludes + Constants.WHITESPACE;
                    } else {
                        requirements += requirementFileIncludes + Constants.WHITESPACE;
                    }
                }
                requirements = requirements.substring(0, requirements.length() - 1);
                config.setProperty(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, requirements);
            }
            commandLineArgsOverride(commandLineArgs);
        } else {
            projectName = config.getProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY);
            configFilePath = NONE;
            offlineRequestFiles = new ArrayList<>();
            fileListPath = null;
            scannedFolders = null;
            dependencyDirs = new ArrayList<>();
            commandLineArgsOverride(null);
        }

        scanPackageManager = config.getBooleanProperty(ConfigPropertyKeys.SCAN_PACKAGE_MANAGER, false);
        scanDockerImages = config.getBooleanProperty(ConfigPropertyKeys.SCAN_DOCKER_IMAGES, false);
        scanTarImages = config.getBooleanProperty(ConfigPropertyKeys.SCAN_TAR_IMAGES, false);
        deleteTarImages = config.getBooleanProperty(ConfigPropertyKeys.DELETE_TAR_FILES, true);

        if (dependencyDirs == null)
            dependencyDirs = new ArrayList<>();

        // validate scanned folder
        if (dependencyDirs.isEmpty() && StringUtils.isEmpty(fileListPath) && (offlineRequestFiles == null || offlineRequestFiles.isEmpty())) {
            dependencyDirs.add(Constants.DOT);
        }

        // validate config
        String projectToken = config.getProperty(ConfigPropertyKeys.PROJECT_TOKEN_PROPERTY_KEY);
        String projectNameFinal = !StringUtils.isBlank(projectName) ? projectName : config.getProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY);
        projectPerFolder = config.getBooleanProperty(ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, false);
        if (StringUtils.isEmpty(apiToken)) {
            apiToken = getToken(config, ConfigPropertyKeys.ORG_TOKEN_FILE, ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY);
        }
        if (StringUtils.isEmpty(userKey)) {
            userKey = getToken(config, ConfigPropertyKeys.USER_KEY_FILE, ConfigPropertyKeys.USER_KEY_PROPERTY_KEY);
        }
        int archiveExtractionDepth = config.getArchiveDepth();
        String[] includes = Constants.TRUE.equals(commandLineArgs.noConfig) ? ExtensionUtils.INCLUDES : config.getIncludes();
        String[] projectPerFolderIncludes = config.getProjectPerFolderIncludes();
        String[] pythonRequirementsFileIncludes = config.getPythonIncludes();
        String[] argsForAppPathAndDirs = args;
        if (argsForAppPathAndDirs != null && argsForAppPathAndDirs.length == 0 && !dependencyDirs.isEmpty()) {
            argsForAppPathAndDirs = dependencyDirs.toArray(new String[0]);
        }
        initializeDependencyDirs(argsForAppPathAndDirs, config);
        String scanComment = config.getProperty(ConfigPropertyKeys.SCAN_COMMENT);

        // validate iaLanguage
        validateIaLanguage(config);

        // todo: check possibility to get the errors only in the end
        if (!Constants.TRUE.equals(commandLineArgs.noConfig)) {
            errors.addAll(configurationValidation.getConfigurationErrors(projectPerFolder, projectToken, projectNameFinal,
                    apiToken, configFilePath, archiveExtractionDepth, includes, projectPerFolderIncludes, pythonRequirementsFileIncludes, scanComment));
        }

        logLevel = config.getProperty(ConfigPropertyKeys.LOG_LEVEL_KEY, INFO);
        logContext = config.getProperty(ConfigPropertyKeys.LOG_CONTEXT);
        // DO NOT CHANGE THE POSITION OF THE THREE LINES BELOW
        if (StringUtils.isNotEmpty(logContext)) {
            LoggerFactory.contextId = logContext;
        }

        request = getRequest(config, apiToken, userKey, projectName, projectToken, scanComment);
        scm = getScm(config);
        agent = getAgent(config, commandLineArgs.noConfig);
        offline = getOffline(config);
        sender = null;
        try {
            sender = getSender(config, serviceUrl);
        } catch (Exception e){
            errors.add(e.getMessage());
        }
        resolver = getResolver(config);
        endpoint = getEndpoint(config);
        remoteDockerConfiguration = getRemoteDockerConfiguration(config);

        // The config file is not necessary if there is the analyzeMultiModule parameter
        if (StringUtils.isEmpty(analyzeMultiModule)) {
            // check properties to ensure via is ready to run
            checkPropertiesForVia(sender, resolver, appPathsToDependencyDirs, errors);
        } else {
            errors.clear();
            if (args.length == 4 && dependencyDirs.size() == 1) {
                Path path = Paths.get(analyzeMultiModule);
                try {
                    if (!Files.exists(path)) {
                        File setUpFile = new File(analyzeMultiModule);
                        boolean fileCreated = setUpFile.createNewFile();
                        if (fileCreated) {
                            setUpMuiltiModuleFile = true;
                        } else {
                            errors.add("The system could not create the multi-project setup file. Please contact support.");
                        }
                    } else {
                        errors.add("The file specified for storing multi-module analysis results already exists. Please specify a new file name.");
                    }
                } catch (IOException e) {
                    errors.add("The system could not create the multi-project setup file : " + path + " " + "Please contact support.");
                }
            } else {
                errors.add("Multi-module analysis could not run due to specified invalid parameters.");
            }
        }
    }

    private void checkCmdArgsWithoutConfig(CommandLineArgs commandLineArgs) {
        /* check if the minimum required settings for running without config file exist
            apiKey & projectName/projectToken & productName/productToken & scannedDirectory
         */
        if (commandLineArgs.apiKey == null || (commandLineArgs.projectToken == null && commandLineArgs.project == null) || commandLineArgs.dependencyDirs == null) {
            errors.add("The apiKey and project/projectToken parameters are required to perform a scan without a configuration file");
        }
    }

    private void validateIaLanguage(FSAConfigProperties config) {
        String iaLanguage = config.getProperty(ConfigPropertyKeys.IA_LANGUAGE);
        boolean iaLanguageValid = false;
        if (iaLanguage != null) {
            for (ViaLanguage viaLanguage : ViaLanguage.values()) {
                if (iaLanguage.toLowerCase().equals(viaLanguage.toString().toLowerCase())) {
                    iaLanguageValid = true;
                    break;
                }
            }
            if (!iaLanguageValid) {
                errors.add("Error: VIA setting are not applicable parameters are not valid. exiting... ");
            }
            if (iaLanguageValid && !config.getBooleanProperty(ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, false)) {
                errors.add("Error: VIA setting are not applicable parameters are not valid. exiting... ");
            }
        }
    }

     private String getToken(FSAConfigProperties config, String propertyKeyFile, String propertyKeyToken) {
        String token = null;
        String tokenFile = config.getProperty(propertyKeyFile);
        if (StringUtils.isNotEmpty(tokenFile)) {
            Pair<InputStream, List<String>> inputStreamAndErrors = getInputStreamFromFile(tokenFile, new CommandLineArgs());
            if (inputStreamAndErrors.getValue().isEmpty()) {
                try {
                    token = new BufferedReader(new InputStreamReader(inputStreamAndErrors.getKey())).readLine();
                } catch (IOException e) {
                    errors.add("Error occurred when reading from " + tokenFile + e.getMessage());
                }
            } else {
                errors.addAll(inputStreamAndErrors.getValue());
            }
        } else {
            token = config.getProperty(propertyKeyToken);
        }
        return token;
    }

    public void checkPropertiesForVia(SenderConfiguration sender, ResolverConfiguration resolver, Map<String, Set<String>> appPathsToDependencyDirs, List<String> errors) {
        Set<String> viaAppPaths = appPathsToDependencyDirs.keySet();
        if (sender != null && sender.isEnableImpactAnalysis()) {
            // the default appPath size is one (defaultKey = -d property), this one check if the user set more then one appPath
            if (viaAppPaths.size() == 1) {
                errors.add("Effective Usage Analysis will not run if the command line parameter 'appPath' is not specified");
            } else {
                boolean validViaAppPath = checkAppPathsForVia(viaAppPaths, errors);
                if (validViaAppPath) {
                    if (resolver.getMavenIgnoredScopes() == null || (!Arrays.asList(resolver.getMavenIgnoredScopes()).contains(MavenTreeDependencyCollector.ALL)
                            && !Arrays.asList(resolver.getMavenIgnoredScopes()).contains(MavenTreeDependencyCollector.NONE))
                            || !resolver.isMavenAggregateModules() || !resolver.isGradleAggregateModules()) {
                        errors.add("Effective Usage Analysis will not run if the following configuration file settings are not made: maven.ignoredScopes=All, " +
                                "maven.aggregateModules=true, gradle.aggregateModules=true");
                    }
                }
            }
        } else {
            if (viaAppPaths.size() > 1) {
                errors.add("Effective Usage Analysis will not run if the configuration file parameter enableImpactAnalysis is not set to 'true'");
            }
        }
    }

    // check validation for appPath property, first check if the path is exist and then if this path is not a directory
    private boolean checkAppPathsForVia(Set<String> keySet, List<String> errors) {
        for (String key : keySet) {
            if (!key.equals("defaultKey")) {
                File file = new File(key);
                if (!file.exists()) {
                    errors.add("Effective Usage Analysis will not run if the -appPath parameter references an invalid file path. Check that the -appPath parameter specifies a valid path");
                    return false;
                } else if (!file.isFile()) {
                    errors.add("Effective Usage Analysis will not run if the -appPath parameter references an invalid file path. Check that the -appPath parameter specifies a valid path");
                    return false;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private void initializeDependencyDirs(String[] argsForAppPathAndDirs, FSAConfigProperties config) {
        if (StringUtils.isNotEmpty(config.getProperty(ConfigPropertyKeys.X_PATHS))) {
            try {
                String textFromFile = new String(Files.readAllBytes(Paths.get(config.getProperty(ConfigPropertyKeys.X_PATHS))), StandardCharsets.UTF_8);
                textFromFile = textFromFile.replaceAll(Constants.COMMA + Constants.WHITESPACE, Constants.COMMA);
                textFromFile = textFromFile.replaceAll(System.lineSeparator(), Constants.WHITESPACE);
                argsForAppPathAndDirs = textFromFile.split(Constants.WHITESPACE);
                if (argsForAppPathAndDirs != null && argsForAppPathAndDirs.length > 0) {
                    initializeDependencyDirsToAppPath(argsForAppPathAndDirs);
                }
                for (String appPath : this.appPathsToDependencyDirs.keySet()) {
                    for (String dir : this.appPathsToDependencyDirs.get(appPath)) {
                        this.dependencyDirs.add(dir);
                    }
                }
            } catch (IOException e) {
                errors.add("Error: Could not read the xPaths file: " + config.getProperty(ConfigPropertyKeys.X_PATHS));
            }
        } else {
            if (argsForAppPathAndDirs != null && argsForAppPathAndDirs.length > 0) {
                initializeDependencyDirsToAppPath(argsForAppPathAndDirs);
            }
        }
    }

    private EndPointConfiguration getEndpoint(FSAConfigProperties config) {
        return new EndPointConfiguration(config.getIntProperty(ConfigPropertyKeys.ENDPOINT_PORT, DEFAULT_PORT),
                config.getProperty(ConfigPropertyKeys.ENDPOINT_CERTIFICATE),
                config.getProperty(ConfigPropertyKeys.ENDPOINT_PASS),
                config.getBooleanProperty(ConfigPropertyKeys.ENDPOINT_ENABLED, DEFAULT_ENABLED),
                config.getBooleanProperty(ConfigPropertyKeys.ENDPOINT_SSL_ENABLED, DEFAULT_SSL));
    }

    private ResolverConfiguration getResolver(FSAConfigProperties config) {

        boolean resolveAllDependencies = config.getBooleanProperty(ConfigPropertyKeys.RESOLVE_ALL_DEPENDENCIES, true);
        // todo split this in multiple configuration before release fsa as a service
        boolean npmRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.NPM_RUN_PRE_STEP, false);
        boolean npmIgnoreScripts = config.getBooleanProperty(ConfigPropertyKeys.NPM_IGNORE_SCRIPTS, false);
        boolean npmResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean npmIncludeDevDependencies = config.getBooleanProperty(ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, false);

        long npmTimeoutDependenciesCollector = config.getLongProperty(ConfigPropertyKeys.NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS, 60);
        boolean npmIgnoreNpmLsErrors = config.getBooleanProperty(ConfigPropertyKeys.NPM_IGNORE_NPM_LS_ERRORS, false);
        String npmAccessToken = config.getProperty(ConfigPropertyKeys.NPM_ACCESS_TOKEN);
        boolean npmYarnProject = config.getBooleanProperty(ConfigPropertyKeys.NPM_YARN_PROJECT, false);

        boolean bowerResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean bowerRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.BOWER_RUN_PRE_STEP, false);

        boolean nugetResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.NUGET_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean nugetRestoreDependencies = config.getBooleanProperty(ConfigPropertyKeys.NUGET_RESTORE_DEPENDENCIES, false);
        boolean nugetRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.NUGET_RUN_PRE_STEP, false);
        boolean nugetResolvePakcagesConfigFiles = config.getBooleanProperty(ConfigPropertyKeys.NUGET_RESOLVE_PACKAGES_CONFIG_FILES, true);
        boolean nugetResolveCsProjFiles = config.getBooleanProperty(ConfigPropertyKeys.NUGET_RESOLVE_CS_PROJ_FILES, true);

        boolean mavenResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        String[] mavenIgnoredScopes = config.getListProperty(ConfigPropertyKeys.MAVEN_IGNORED_SCOPES, null);
        boolean mavenAggregateModules = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_AGGREGATE_MODULES, false);
        boolean mavenIgnoredPomModules = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_IGNORE_POM_MODULES, true);
        boolean mavenRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_RUN_PRE_STEP, false);
        boolean mavenIgnoreDependencyTreeErrors = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_IGNORE_DEPENDENCY_TREE_ERRORS, false);
        String whiteSourceConfiguration = config.getProperty(ConfigPropertyKeys.PROJECT_CONFIGURATION_PATH);

        boolean pythonResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        String pipPath = config.getProperty(ConfigPropertyKeys.PYTHON_PIP_PATH, PIP);
        String pythonPath = config.getProperty(ConfigPropertyKeys.PYTHON_PATH, PYTHON);
        boolean pythonIsWssPluginInstalled = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_IS_WSS_PLUGIN_INSTALLED, false);
        boolean pythonUninstallWssPluginInstalled = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_UNINSTALL_WSS_PLUGIN, false);
        boolean pythonIgnorePipInstallErrors = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_IGNORE_PIP_INSTALL_ERRORS, false);
        boolean pythonInstallVirtualenv = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_INSTALL_VIRTUALENV, false);
        boolean pythonResolveHierarchyTree = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_RESOLVE_HIERARCHY_TREE, true);
        boolean pythonResolveSetupPyFiles = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_RESOLVE_SETUP_PY_FILES, false);
        String[] bomPatternForPython;
        if (pythonResolveSetupPyFiles) {
            bomPatternForPython = new String[]{Constants.PYTHON_REQUIREMENTS, Constants.SETUP_PY, Constants.PIPFILE};
            //bomPatternForPython = new String[]{Constants.PATTERN + Constants.PYTHON_REQUIREMENTS, Constants.PATTERN + Constants.SETUP_PY, Constants.PATTERN + Constants.PIPFILE};
        } else {
            bomPatternForPython = new String[]{Constants.PYTHON_REQUIREMENTS, Constants.PIPFILE};
            //bomPatternForPython = new String[]{Constants.PATTERN + Constants.PYTHON_REQUIREMENTS, Constants.PATTERN + Constants.PIPFILE};
        }

        String[] pythonRequirementsFileIncludes = config.getPythonIncludesWithPipfile(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, bomPatternForPython);
        boolean pythonRunPipenvPreStep = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_RUN_PIPENV_PRE_STEP, false);
        boolean pythonIgnorePipenvInstallErrors = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_IGNORE_PIPENV_INSTALL_ERRORS, false);
        boolean pythonInstallDevDependencies = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_PIPENV_DEV_DEPENDENCIES, false);

        boolean gradleResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.GRADLE_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean gradleRunAssembleCommand = config.getBooleanProperty(ConfigPropertyKeys.GRADLE_RUN_ASSEMBLE_COMMAND, true);
        boolean gradleAggregateModules = config.getBooleanProperty(ConfigPropertyKeys.GRADLE_AGGREGATE_MODULES, false);
        boolean gradleRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.GRADLE_RUN_PRE_STEP, false);
        String[] gradleIgnoredScopes = config.getListProperty(ConfigPropertyKeys.GRADLE_IGNORE_SCOPES, new String[0]);
        String graldeLocalRepositoryPath = config.getProperty(ConfigPropertyKeys.GRADLE_LOCAL_REPOSITORY_PATH, EMPTY_STRING);
        String gradlePreferredEnvironment = config.getProperty(ConfigPropertyKeys.GRADLE_PREFERRED_ENVIRONMENT, Constants.GRADLE);
        if (gradlePreferredEnvironment.isEmpty()) {
            gradlePreferredEnvironment = Constants.GRADLE;
        }

        boolean paketResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.PAKET_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        String[] paketIgnoredScopes = config.getListProperty(ConfigPropertyKeys.PAKET_IGNORED_GROUPS, null);
        boolean paketRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.PAKET_RUN_PRE_STEP, false);
        String paketPath = config.getProperty(ConfigPropertyKeys.PAKET_EXE_PATH, null);

        boolean goResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.GO_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        String goDependencyManager = config.getProperty(ConfigPropertyKeys.GO_DEPENDENCY_MANAGER, EMPTY_STRING);
        boolean goCollectDependenciesAtRuntime = config.getBooleanProperty(ConfigPropertyKeys.GO_COLLECT_DEPENDENCIES_AT_RUNTIME, false);
        boolean goIgnoreTestPackages = config.getBooleanProperty(ConfigPropertyKeys.GO_GLIDE_IGNORE_TEST_PACKAGES, true);
        boolean goGradleEnableTaskAlias = config.getBooleanProperty(ConfigPropertyKeys.GO_GRADLE_ENABLE_TASK_ALIAS, false);
        boolean addSha1 = config.getBooleanProperty("addSha1", false);

        boolean rubyResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.RUBY_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean rubyRunBundleInstall = config.getBooleanProperty(ConfigPropertyKeys.RUBY_RUN_BUNDLE_INSTALL, false);
        boolean rubyOverwriteGemFile = config.getBooleanProperty(ConfigPropertyKeys.RUBY_OVERWRITE_GEM_FILE, false);
        boolean rubyInstallMissingGems = config.getBooleanProperty(ConfigPropertyKeys.RUBY_INSTALL_MISSING_GEMS, false);

        boolean phpResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.PHP_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean phpRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.PHP_RUN_PRE_STEP, false);
        boolean phpIncludeDevDependencies = config.getBooleanProperty(ConfigPropertyKeys.PHP_INCLUDE_DEV_DEPENDENCIES, false);

        boolean sbtResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.SBT_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean sbtAggregateModules = config.getBooleanProperty(ConfigPropertyKeys.SBT_AGGREGATE_MODULES, false);
        boolean sbtRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.SBT_RUN_PRE_STEP, false);
        String sbtTargetFolder = config.getProperty(ConfigPropertyKeys.SBT_TARGET_FOLDER, EMPTY_STRING);

        boolean htmlResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.HTML_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean cocoapodsResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.COCOAPODS_RESOLVE_DEPENDENCIES, resolveAllDependencies);
        boolean cocoapodsRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.COCOAPODS_RUN_PRE_STEP, false);

        // TODO - as long as there's no support for hex on the server side - the default value of hex.resolveDependencies is FALSE
        boolean hexResolveDependencies = config.getBooleanProperty(ConfigPropertyKeys.HEX_RESOLVE_DEPENDENECIES, false);
        boolean hexRunPreStep = config.getBooleanProperty(ConfigPropertyKeys.HEX_RUN_PRE_STEP, false);
        boolean hexAggregateModules = config.getBooleanProperty(ConfigPropertyKeys.HEX_AGGREGATE_MODULES, false);

        boolean npmIgnoreSourceFiles;
        boolean bowerIgnoreSourceFiles;
        boolean nugetIgnoreSourceFiles;
        boolean mavenIgnoreSourceFiles;
        boolean pythonIgnoreSourceFiles;
        boolean gradleIgnoreSourceFiles;
        boolean paketIgnoreSourceFiles;
        boolean sbtIgnoreSourceFiles;
        boolean goIgnoreSourceFiles;
        boolean rubyIgnoreSourceFiles;
        boolean cocoapodsIgnoreSourceFiles;
        boolean hexIgnoreSourceFiles;
        boolean ignoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.IGNORE_SOURCE_FILES, ConfigPropertyKeys.DEPENDENCIES_ONLY, false);

        if (ignoreSourceFiles == true) {
            npmIgnoreSourceFiles = true;
            bowerIgnoreSourceFiles = true;
            nugetIgnoreSourceFiles = true;
            mavenIgnoreSourceFiles = true;
            gradleIgnoreSourceFiles = true;
            paketIgnoreSourceFiles = true;
            sbtIgnoreSourceFiles = true;
            goIgnoreSourceFiles = true;
            rubyIgnoreSourceFiles = true;
            pythonIgnoreSourceFiles = true;
            cocoapodsIgnoreSourceFiles = true;
            hexIgnoreSourceFiles = true;
        } else {
            npmIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.NPM_IGNORE_SOURCE_FILES, ConfigPropertyKeys.NPM_IGNORE_JAVA_SCRIPT_FILES, true);
            bowerIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.BOWER_IGNORE_SOURCE_FILES, false);
            nugetIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.NUGET_IGNORE_SOURCE_FILES, true);
            mavenIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.MAVEN_IGNORE_SOURCE_FILES, false);
            gradleIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.GRADLE_IGNORE_SOURCE_FILES, false);
            paketIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.PAKET_IGNORE_SOURCE_FILES, ConfigPropertyKeys.PAKET_IGNORE_FILES, true);
            sbtIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.SBT_IGNORE_SOURCE_FILES, false);
            goIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.GO_IGNORE_SOURCE_FILES, false);
            pythonIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.PYTHON_IGNORE_SOURCE_FILES, true);
            rubyIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.RUBY_IGNORE_SOURCE_FILES, true);
            cocoapodsIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.COCOAPODS_IGNORE_SOURCE_FILES, true);
            hexIgnoreSourceFiles = config.getBooleanProperty(ConfigPropertyKeys.HEX_IGNORE_SOURCE_FILES, true);
        }

        return new ResolverConfiguration(npmRunPreStep, npmResolveDependencies, npmIgnoreScripts, npmIncludeDevDependencies, npmIgnoreSourceFiles,
                npmTimeoutDependenciesCollector, npmAccessToken, npmIgnoreNpmLsErrors, npmYarnProject,
                bowerResolveDependencies, bowerRunPreStep, bowerIgnoreSourceFiles,
                nugetResolveDependencies, nugetRestoreDependencies, nugetRunPreStep, nugetIgnoreSourceFiles, nugetResolvePakcagesConfigFiles, nugetResolveCsProjFiles,
                mavenResolveDependencies, mavenIgnoredScopes, mavenAggregateModules, mavenIgnoredPomModules, mavenIgnoreSourceFiles, mavenRunPreStep, mavenIgnoreDependencyTreeErrors,
                pythonResolveDependencies, pipPath, pythonPath, pythonIsWssPluginInstalled, pythonUninstallWssPluginInstalled,
                pythonIgnorePipInstallErrors, pythonInstallVirtualenv, pythonResolveHierarchyTree, pythonRequirementsFileIncludes, pythonResolveSetupPyFiles, pythonIgnoreSourceFiles,
                pythonIgnorePipenvInstallErrors, pythonRunPipenvPreStep, pythonInstallDevDependencies,
                ignoreSourceFiles, whiteSourceConfiguration,
                gradleResolveDependencies, gradleRunAssembleCommand, gradleAggregateModules, gradlePreferredEnvironment, gradleIgnoreSourceFiles, gradleRunPreStep, gradleIgnoredScopes,
                graldeLocalRepositoryPath, paketResolveDependencies, paketIgnoredScopes, paketRunPreStep, paketPath, paketIgnoreSourceFiles,
                goResolveDependencies, goDependencyManager, goCollectDependenciesAtRuntime, goIgnoreTestPackages, goIgnoreSourceFiles, goGradleEnableTaskAlias,
                rubyResolveDependencies, rubyRunBundleInstall, rubyOverwriteGemFile, rubyInstallMissingGems, rubyIgnoreSourceFiles,
                phpResolveDependencies, phpRunPreStep, phpIncludeDevDependencies,
                sbtResolveDependencies, sbtAggregateModules, sbtRunPreStep, sbtTargetFolder, sbtIgnoreSourceFiles,
                htmlResolveDependencies, cocoapodsResolveDependencies, cocoapodsRunPreStep, cocoapodsIgnoreSourceFiles,
                hexResolveDependencies, hexRunPreStep, hexIgnoreSourceFiles, hexAggregateModules, addSha1);
    }

    private RequestConfiguration getRequest(FSAConfigProperties config, String apiToken, String userKey, String projectName, String projectToken, String scanComment) {
        String productToken = config.getProperty(ConfigPropertyKeys.PRODUCT_TOKEN_PROPERTY_KEY);
        String productName = config.getProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY);
        String productVersion = config.getProperty(ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY);
        String projectVersion = config.getProperty(ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY);
        List<String> appPath = (List<String>) config.get(ConfigPropertyKeys.APP_PATH);
        String iaLanguage = config.getProperty(ConfigPropertyKeys.IA_LANGUAGE, null);
        String viaDebug = config.getProperty(ConfigPropertyKeys.VIA_DEBUG, EMPTY_STRING);
        boolean projectPerSubFolder = config.getBooleanProperty(ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, false);
        String requesterEmail = config.getProperty(ConfigPropertyKeys.REQUESTER_EMAIL);
        int viaAnalysis = config.getIntProperty(ConfigPropertyKeys.VIA_ANALYSIS_LEVEL, VIA_DEFAULT_ANALYSIS_LEVEL);
        boolean requireKnownSha1 = config.getBooleanProperty(ConfigPropertyKeys.REQUIRE_KNOWN_SHA1, true);
        return new RequestConfiguration(apiToken, userKey, requesterEmail, projectPerSubFolder, projectName, projectToken,
                projectVersion, productName, productToken, productVersion, appPath, viaDebug, viaAnalysis, iaLanguage, scanComment, requireKnownSha1);
    }

    private SenderConfiguration getSender(FSAConfigProperties config, String cmdServiceUrl) throws Exception {
        String updateTypeValue = config.getProperty(ConfigPropertyKeys.UPDATE_TYPE, UpdateType.OVERRIDE.toString());
        boolean checkPolicies = config.getBooleanProperty(ConfigPropertyKeys.CHECK_POLICIES_PROPERTY_KEY, false);
        boolean forceCheckAllDependencies = config.getBooleanProperty(ConfigPropertyKeys.FORCE_CHECK_ALL_DEPENDENCIES, false);
        boolean updateInventory = config.getBooleanProperty(ConfigPropertyKeys.UPDATE_INVENTORY, true);
        boolean forceUpdate = config.getBooleanProperty(ConfigPropertyKeys.FORCE_UPDATE, false);
        boolean forceUpdateBuildFailed = config.getBooleanProperty(ConfigPropertyKeys.FORCE_UPDATE_FAIL_BUILD_ON_POLICY_VIOLATION, false);
        boolean enableImpactAnalysis = config.getBooleanProperty(ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, false);
        String serviceUrl = cmdServiceUrl != null ? cmdServiceUrl : config.getProperty(SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        Pattern urlPattern = Pattern.compile(SERVICE_URL_REGEX);
        Matcher matcher = urlPattern.matcher(serviceUrl);
        if (!matcher.find()){
            String msg = "Service URL is malformed: " + serviceUrl;
            if (serviceUrl.endsWith("/agent") == false){
                msg = msg.concat(" (make sure the URL ends with '/agent')");
            }
            throw new Exception(msg);
        }
        String proxyHost = config.getProperty(ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY);
        connectionTimeOut = Integer.parseInt(config.getProperty(ClientConstants.CONNECTION_TIMEOUT_KEYWORD,
                String.valueOf(ClientConstants.DEFAULT_CONNECTION_TIMEOUT_MINUTES)));
        int connectionRetries = config.getIntProperty(ConfigPropertyKeys.CONNECTION_RETRIES, 1);
        int connectionRetriesIntervals = config.getIntProperty(ConfigPropertyKeys.CONNECTION_RETRIES_INTERVALS, 3000);
        String senderPort = config.getProperty(ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY);

        int proxyPort;
        if (StringUtils.isNotEmpty(senderPort)) {
            proxyPort = Integer.parseInt(senderPort);
        } else {
            proxyPort = -1;
        }

        String proxyUser = config.getProperty(ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY);
        String proxyPassword = config.getProperty(ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY);
        boolean ignoreCertificateCheck = config.getBooleanProperty(ConfigPropertyKeys.IGNORE_CERTIFICATE_CHECK, false);
        boolean isSendLogsToWss = config.getBooleanProperty(ConfigPropertyKeys.SEND_LOGS_TO_WSS, false);

        return new SenderConfiguration(checkPolicies, serviceUrl, connectionTimeOut,
                proxyHost, proxyPort, proxyUser, proxyPassword,
                forceCheckAllDependencies, forceUpdate, forceUpdateBuildFailed, updateTypeValue,
                enableImpactAnalysis, ignoreCertificateCheck, connectionRetries, connectionRetriesIntervals, isSendLogsToWss, updateInventory);
    }

    private OfflineConfiguration getOffline(FSAConfigProperties config) {
        boolean enabled = config.getBooleanProperty(ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, false);
        boolean zip = config.getBooleanProperty(ConfigPropertyKeys.OFFLINE_ZIP_PROPERTY_KEY, false);
        boolean prettyJson = config.getBooleanProperty(ConfigPropertyKeys.OFFLINE_PRETTY_JSON_KEY, true);
        String wsFolder = StringUtils.isBlank(config.getProperty(ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH)) ? WHITE_SOURCE_DEFAULT_FOLDER_PATH : config.getProperty(ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH);
        return new OfflineConfiguration(enabled, zip, prettyJson, wsFolder);
    }

    private AgentConfiguration getAgent(FSAConfigProperties config, String noConfig) {
        String[] includes = Constants.TRUE.equals(noConfig) ? ExtensionUtils.INCLUDES : config.getIncludes();
        String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] dockerIncludes = config.getDockerIncludes();
        String[] dockerExcludes = config.getProperty(ConfigPropertyKeys.DOCKER_EXCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] projectPerFolderIncludes = config.getProjectPerFolderIncludes();
        String[] projectPerFolderExcludes = config.getProjectPerFolderExcludes();
        int archiveExtractionDepth = config.getArchiveDepth();
        String[] archiveIncludes = config.getProperty(ConfigPropertyKeys.ARCHIVE_INCLUDES_PATTERN_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] archiveExcludes = config.getProperty(ConfigPropertyKeys.ARCHIVE_EXCLUDES_PATTERN_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] pythonRequirementsFileIncludes = config.getPythonIncludes();
        boolean archiveFastUnpack = config.getBooleanProperty(ConfigPropertyKeys.ARCHIVE_FAST_UNPACK_KEY, false);
        boolean archiveFollowSymbolicLinks = config.getBooleanProperty(ConfigPropertyKeys.FOLLOW_SYMBOLIC_LINKS, true);
        boolean dockerScan = config.getBooleanProperty(ConfigPropertyKeys.SCAN_DOCKER_IMAGES, false);
        boolean partialSha1Match = config.getBooleanProperty(ConfigPropertyKeys.PARTIAL_SHA1_MATCH_KEY, false);
        boolean calculateHints = config.getBooleanProperty(ConfigPropertyKeys.CALCULATE_HINTS, false);
        boolean calculateMd5 = config.getBooleanProperty(ConfigPropertyKeys.CALCULATE_MD5, false);
        boolean showProgress = config.getBooleanProperty(ConfigPropertyKeys.SHOW_PROGRESS_BAR, true);
        Pair<Boolean, String> globalCaseSensitive = getGlobalCaseSensitive(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY));

        //key , val

        Collection<String> excludesCopyrights = getExcludeCopyrights(config.getProperty(ConfigPropertyKeys.EXCLUDED_COPYRIGHT_KEY, EMPTY_STRING));

        return new AgentConfiguration(includes, excludes, dockerIncludes, dockerExcludes,
                archiveExtractionDepth, archiveIncludes, archiveExcludes, archiveFastUnpack, archiveFollowSymbolicLinks,
                partialSha1Match, calculateHints, calculateMd5, showProgress, globalCaseSensitive.getKey(), dockerScan, excludesCopyrights, projectPerFolderIncludes,
                projectPerFolderExcludes, pythonRequirementsFileIncludes, globalCaseSensitive.getValue());
    }

    private Collection<String> getExcludeCopyrights(String excludedCopyrightsValue) {
        Collection<String> excludes = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(Constants.COMMA)));
        excludes.remove(EMPTY_STRING);
        return excludes;
    }

    private Pair<Boolean, String> getGlobalCaseSensitive(String globCaseSensitiveValue) {
        boolean globCaseSensitive = false;
        String error = null;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase(Constants.TRUE) || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
                error = null;
            } else if (globCaseSensitiveValue.equalsIgnoreCase(Constants.FALSE) || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
                error = null;
            } else {
                error = "Bad " + ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY + ". Received " + globCaseSensitiveValue + ", required true/false or y/n";
            }
        } else {
            error = null;
        }
        return new Pair<>(globCaseSensitive, error);
    }

    private ScmConfiguration getScm(FSAConfigProperties config) {
        String type = config.getProperty(ConfigPropertyKeys.SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(ConfigPropertyKeys.SCM_URL_PROPERTY_KEY);
        String user = config.getProperty(ConfigPropertyKeys.SCM_USER_PROPERTY_KEY);
        String pass = config.getProperty(ConfigPropertyKeys.SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(ConfigPropertyKeys.SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(ConfigPropertyKeys.SCM_TAG_PROPERTY_KEY);
        String ppk = config.getProperty(ConfigPropertyKeys.SCM_PPK_PROPERTY_KEY);

        //defaults
        String repositoriesPath = config.getProperty(ConfigPropertyKeys.SCM_REPOSITORIES_FILE);
        boolean npmInstall = config.getBooleanProperty(ConfigPropertyKeys.SCM_NPM_INSTALL, true);
        int npmInstallTimeoutMinutes = config.getIntProperty(ConfigPropertyKeys.SCM_NPM_INSTALL_TIMEOUT_MINUTES, 15);

        return new ScmConfiguration(type, user, pass, ppk, url, branch, tag, repositoriesPath, npmInstall, npmInstallTimeoutMinutes);
    }

    private RemoteDockerConfiguration getRemoteDockerConfiguration(FSAConfigProperties config) {
        String[] empty = new String[0];
        String[] dockerImages = config.getListProperty(ConfigPropertyKeys.DOCKER_PULL_IMAGES, null);
        String[] dockerTags = config.getListProperty(ConfigPropertyKeys.DOCKER_PULL_TAGS, null);
        String[] dockerDigests = config.getListProperty(ConfigPropertyKeys.DOCKER_PULL_DIGEST, null);
        boolean forceDelete = config.getBooleanProperty(ConfigPropertyKeys.DOCKER_DELETE_FORCE, false);
        boolean enablePulling = config.getBooleanProperty(ConfigPropertyKeys.DOCKER_PULL_ENABLE, false);
        boolean loginSudo = config.getBooleanProperty(ConfigPropertyKeys.DOCKER_LOGIN_SUDO, true);
        List<String> dockerImagesList = null;
        if (dockerImages != null) {
            dockerImagesList = new LinkedList<>(Arrays.asList(dockerImages));
        }
        List<String> dockerTagsList = null;
        if (dockerTags != null) {
            dockerTagsList = new LinkedList<>(Arrays.asList(dockerTags));
        }
        List<String> dockerDigestsList = null;
        if (dockerDigests != null) {
            dockerDigestsList = new LinkedList<>(Arrays.asList(dockerDigests));
        }

        int maxImagesScan = config.getIntProperty(ConfigPropertyKeys.DOCKER_SCAN_MAX_IMAGES, 0);
        int maxImagesPull = config.getIntProperty(ConfigPropertyKeys.DOCKER_PULL_MAX_IMAGES, 10);
        boolean pullForce = config.getBooleanProperty(ConfigPropertyKeys.DOCKER_PULL_FORCE, false);
        RemoteDockerConfiguration result = new RemoteDockerConfiguration(dockerImagesList, dockerTagsList,
                dockerDigestsList, forceDelete, enablePulling, maxImagesScan, pullForce, maxImagesPull, loginSudo);

        // Amazon configuration
        String[] dockerAmazonRegistryIds = config.getListProperty(ConfigPropertyKeys.DOCKER_AWS_REGISTRY_IDS, empty);
        String dockerAmazonRegion = config.getProperty(ConfigPropertyKeys.DOCKER_AWS_REGION, "east");
        boolean enableAmazon = config.getBooleanProperty(ConfigPropertyKeys.DOCKER_AWS_ENABLE, false);
        int maxPullImagesFromAmazon = config.getIntProperty(ConfigPropertyKeys.DOCKER_AWS_MAX_PULL_IMAGES, 0);
        result.setAmazonRegistryIds(new LinkedList<>(Arrays.asList(dockerAmazonRegistryIds)));
        result.setAmazonRegion(dockerAmazonRegion);
        result.setRemoteDockerAmazonEnabled(enableAmazon);
        result.setAmazonMaxPullImages(maxPullImagesFromAmazon);

        // Azure configuration
        result.setRemoteDockerAzureEnabled(config.getBooleanProperty(ConfigPropertyKeys.DOCKER_AZURE_ENABLED, false));
        result.setAzureUserName(config.getProperty(ConfigPropertyKeys.DOCKER_AZURE_USER_NAME, EMPTY_STRING));
        result.setAzureUserPassword(config.getProperty(ConfigPropertyKeys.DOCKER_AZURE_USER_PASSWORD, EMPTY_STRING));
        String[] dockerAzureRegistryNames = config.getListProperty(ConfigPropertyKeys.DOCKER_AZURE_REGISTRY_NAMES, empty);
        result.setAzureRegistryNames(new LinkedList<>(Arrays.asList(dockerAzureRegistryNames)));
        return result;
    }

    private void initializeDependencyDirsToAppPath(String[] args) {
        boolean wasDir = false;
        for (int i = 0; i < args.length; i++) {
            if (!wasDir && args[i].equals(APP_PATH)) {
                if (i + 3 < args.length && args[i + 2].equals(Constants.DASH + Constants.DIRECTORY)) {
                    List<String> paths = Arrays.asList(args[i + 3].split(Constants.COMMA));
                    Set<String> value = new HashSet<>();
                    value.addAll(paths);
                    appPathsToDependencyDirs.put(args[i + 1], value);
                    i = i + 3;
                } else {
                    errors.add("Error: the '-appPath' parameter must have a following '-d'.");
                    return;
                }
            } else if (wasDir && args[i].equals(APP_PATH)) {
                errors.add("Error: the '-appPath' parameter cannot follow the parameter '-d'.");
                break;
            } else if (args[i].equals(Constants.DASH + Constants.DIRECTORY)) {
                if (i + 1 < args.length) {
                    if (appPathsToDependencyDirs.containsKey(DEFAULT_KEY)) {
                        appPathsToDependencyDirs.get(DEFAULT_KEY).addAll(Arrays.asList(args[i + 1].split(Constants.COMMA)));
                    } else {
                        List<String> paths = Arrays.asList(args[i + 1].split(Constants.COMMA));
                        Set<String> value = new HashSet<>();
                        value.addAll(paths);
                        appPathsToDependencyDirs.put(DEFAULT_KEY, value);
                    }
                    i++;
                } else {
                    errors.add("Error: there is not path after the '-d' parameter.");
                    return;
                }
                wasDir = true;
            }
        }
        if (!wasDir) {
            appPathsToDependencyDirs.put(DEFAULT_KEY, new HashSet<>(dependencyDirs));
        }
    }

    public static Pair<FSAConfigProperties, List<String>> readWithError(String configFilePath, CommandLineArgs commandLineArgs) {
        FSAConfigProperties configProps = new FSAConfigProperties();
        Pair<InputStream, List<String>> inputStreamErrorsPair = getInputStreamFromFile(configFilePath, commandLineArgs);
        InputStream inputStream = inputStreamErrorsPair.getKey();
        List<String> errors = inputStreamErrorsPair.getValue();

        try {
            //                    configProps.load(inputStream); replaced by the below
            configProps.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            // Remove extra spaces from the values
            Set<String> keys = configProps.stringPropertyNames();
            for (String key : keys) {
                String value = configProps.getProperty(key);
                if (value != null) {
                    value = value.trim();
                }
                configProps.put(key, value);
            }
        } catch (Exception e) {
            errors.add("Error occurred when reading from " + configFilePath + ", error: " + e.getMessage());
        }
        return new Pair<>(configProps, errors);
    }

    private static Pair<InputStream, List<String>> getInputStreamFromFile(String filePath, CommandLineArgs commandLineArgs) {
        List<String> errors = new ArrayList<>();
        BufferedReader readFileFromUrl = null;
        StringBuffer writeUrlFileContent = null;
        //since we don't know if it a url or local path, so we first try to resolve a url, if it failed, then we try local path
        try {
            //assign the url to point to config path url
            URL url = new URL(filePath);
            //toDo complete proxy settings once finished in WSE-791
            Proxy proxy = null;
            String[] parsedProxy = null;
            String authUser = null;
            String authPass = null;
            URLConnection urlConnection;

            if (commandLineArgs.proxy != null) {
                //get hostname, port and credentials of proxy
                parsedProxy = parseProxy(commandLineArgs.proxy, errors);
                authUser = parsedProxy[2];
                authPass = parsedProxy[3];
                if (parsedProxy[1] != null && Integer.valueOf(parsedProxy[1]) > 0) {
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parsedProxy[0], Integer.valueOf(parsedProxy[1])));
                } else {
                    errors.add("Port must be set or greater than 0");
                }
            } else if (commandLineArgs.proxyHost != null) {
                authUser = commandLineArgs.proxyUser;
                authPass = commandLineArgs.proxyPass;
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(commandLineArgs.proxyHost, Integer.valueOf(commandLineArgs.proxyPort)));
            }
            //if proxy is set, so open the connection with proxy
            if (proxy != null) {
                //setting proxy authenticator and disable schemes for http connections
                ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator(authUser, authPass);
                Authenticator.setDefault(proxyAuthenticator);
                /*The 'jdk.http.auth.tunneling.disabledSchemes' property lists the authentication
                schemes that will be disabled when tunneling HTTPS over a proxy, HTTP CONNECT.
                so setting it to empty for this run only*/
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", EMPTY_STRING);
                urlConnection = url.openConnection(proxy);
            } else {
                urlConnection = url.openConnection();
            }
            urlConnection.connect();
            readFileFromUrl = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            writeUrlFileContent = new StringBuffer();
            //write data of the file to string buffer
            while ((inputLine = readFileFromUrl.readLine()) != null) {
                writeUrlFileContent.append(inputLine + "\n");
            }
        }
        //if error occurred that means it is not a url that we can resolve, still need to try local path, so do nothing in catch
        catch (MalformedURLException e) {
        } catch (IOException e) {
        }

        InputStream inputStream = null;
        //if there is any data written to the buffer, so convert to input stream
        if (writeUrlFileContent != null) {
            inputStream = IOUtils.toInputStream(writeUrlFileContent, UTF_8);
        } else {
            try {
                //if string buffer still null, so try to open stream of local file path
                inputStream = new FileInputStream(filePath);
            } catch (FileNotFoundException e) {
                errors.add("Failed to open " + filePath + " for reading " + e.getMessage());
            }
        }
        return new Pair<>(inputStream, errors);
    }

    /* --- Public getters --- */

    public RequestConfiguration getRequest() {
        return request;
    }

    public EndPointConfiguration getEndpoint() {
        return endpoint;
    }

    public SenderConfiguration getSender() {
        return sender;
    }

    public ScmConfiguration getScm() {
        return scm;
    }

    public AgentConfiguration getAgent() {
        return agent;
    }

    public OfflineConfiguration getOffline() {
        return offline;
    }

    public ResolverConfiguration getResolver() {
        return resolver;
    }

    public RemoteDockerConfiguration getRemoteDocker() {
        return remoteDockerConfiguration;
    }

    public String getScannedFolders() {
        return scannedFolders;
    }

    List<String> getErrors() {
        return errors;
    }

    public List<String> getOfflineRequestFiles() {
        return offlineRequestFiles;
    }

    public String getFileListPath() {
        return fileListPath;
    }

    public List<String> getDependencyDirs() {
        return dependencyDirs;
    }

    public boolean getUseCommandLineProductName() {
        return useCommandLineProductName;
    }

    public boolean getUseCommandLineProjectName() {
        return useCommandLineProjectName;
    }

    public List<String> getAppPaths() {
        return appPaths;
    }

    public Map<String, Set<String>> getAppPathsToDependencyDirs() {
        return appPathsToDependencyDirs;
    }

    public String getAnalyzeMultiModule() {
        return analyzeMultiModule;
    }

    public String getxModulePath() {
        return xModulePath;
    }

    public boolean isSetUpMuiltiModuleFile() {
        return setUpMuiltiModuleFile;
    }

    @JsonProperty(ConfigPropertyKeys.SCAN_PACKAGE_MANAGER)
    public boolean isScanProjectManager() {
        return scanPackageManager;
    }

    @JsonProperty(ConfigPropertyKeys.SCAN_DOCKER_IMAGES)
    public boolean isScanDockerImages() {
        return scanDockerImages;
    }

    @JsonProperty(ConfigPropertyKeys.LOG_LEVEL_KEY)
    public String getLogLevel() {
        return logLevel;
    }

    public boolean isScanImagesTar() {
        return scanTarImages;
    }

    public boolean deleteTarImages() {
        return deleteTarImages;
    }

    /* --- Public static methods--- */

    public static int getIntProperty(Properties config, String propertyKey, int defaultValue) {
        int value = defaultValue;
        String propertyValue = config.getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            try {
                value = Integer.valueOf(propertyValue);
            } catch (NumberFormatException e) {
                // do nothing
            }
        }
        return value;
    }

    public static boolean getBooleanProperty(Properties config, String propertyKey, boolean defaultValue) {
        boolean property = defaultValue;
        String propertyValue = config.getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Boolean.valueOf(propertyValue);
        }
        return property;
    }

    public static long getLongProperty(Properties config, String propertyKey, long defaultValue) {
        long property = defaultValue;
        String propertyValue = config.getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Long.parseLong(propertyValue);
        }
        return property;
    }

    public static String[] getListProperty(Properties config, String propertyName, String[] defaultValue) {
        String property = config.getProperty(propertyName);
        if (property == null) {
            return defaultValue;
        }
        return property.split(Constants.WHITESPACE);
    }

    public static int getArchiveDepth(Properties configProps) {
        return getIntProperty(configProps, ConfigPropertyKeys.ARCHIVE_EXTRACTION_DEPTH_KEY, FSAConfiguration.DEFAULT_ARCHIVE_DEPTH);
    }

    public static String[] getIncludes(Properties configProps) {
        String includesString = configProps.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING);
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    private static String[] getPythonIncludes(Properties configProps) {
        String includesString = configProps.getProperty(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, Constants.PYTHON_REQUIREMENTS);
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, Constants.PYTHON_REQUIREMENTS).split(Constants.WHITESPACE);
        }
        return new String[0];
    }

    public static String[] getProjectPerFolderIncludes(Properties configProps) {
        String projectPerFolderIncludesString = configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_INCLUDES, null);
        if (StringUtils.isNotBlank(projectPerFolderIncludesString)) {
            return configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_INCLUDES, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        if (EMPTY_STRING.equals(projectPerFolderIncludesString)) {
            return null;
        }
        String[] result = new String[1];
        result[0] = "*";
        return result;
    }

    public static String[] getProjectPerFolderExcludes(Properties configProps) {
        String projectPerFolderExcludesString = configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, EMPTY_STRING);
        if (StringUtils.isNotBlank(projectPerFolderExcludesString)) {
            return configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public static String[] getDockerIncludes(Properties configProps) {
        String includesString = configProps.getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING);
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public List<String> getRequirementsFileIncludes() {
        return requirementsFileIncludes;
    }

    public String getLogContext() {
        return this.logContext;
    }

    /* --- Private methods --- */

    private List<String> updateProperties(FSAConfigProperties configProps, CommandLineArgs commandLineArgs) {
        // Check whether the user inserted api key, project OR/AND product via command line
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY, commandLineArgs.apiKey);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.UPDATE_TYPE, commandLineArgs.updateType);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY, commandLineArgs.product);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY, commandLineArgs.productVersion);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY, commandLineArgs.projectVersion);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.USER_KEY_PROPERTY_KEY, commandLineArgs.userKey);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROJECT_TOKEN_PROPERTY_KEY, commandLineArgs.projectToken);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PRODUCT_TOKEN_PROPERTY_KEY, commandLineArgs.productToken);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.LOG_LEVEL_KEY, commandLineArgs.logLevel);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SEND_LOGS_TO_WSS, commandLineArgs.sendLogsToWss);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SCAN_COMMENT, commandLineArgs.scanComment);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.LOG_CONTEXT, commandLineArgs.logContext);
//        readPropertyFromCommandLine(configProps, SERVICE_URL_KEYWORD, commandLineArgs.wssUrl);
        // request file
        List<String> offlineRequestFiles = new LinkedList<>();
        offlineRequestFiles.addAll(commandLineArgs.requestFiles);
        if (offlineRequestFiles.size() > 0) {
            configProps.put(ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, FALSE);
        }
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, commandLineArgs.offline);
        //Impact Analysis parameters
        readListFromCommandLine(configProps, ConfigPropertyKeys.APP_PATH, commandLineArgs.appPath);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.VIA_DEBUG, commandLineArgs.viaDebug);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.VIA_ANALYSIS_LEVEL, commandLineArgs.viaLevel);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, commandLineArgs.enableImpactAnalysis);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.IA_LANGUAGE, commandLineArgs.iaLanguage);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.X_PATHS, commandLineArgs.xPaths);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ANALYZE_MULTI_MODULE, commandLineArgs.analyzeMultiModule);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.X_MODULE_PATH, commandLineArgs.xModulePath);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ADD_SHA1, commandLineArgs.addSha1);

        // proxy
        if (commandLineArgs.proxy == null) {
            readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY, commandLineArgs.proxyHost);
            readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY, commandLineArgs.proxyPort);
            readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY, commandLineArgs.proxyUser);
            readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY, commandLineArgs.proxyPass);
        } else {
            List<String> errors = new ArrayList<>();
            String[] parsedProxy = parseProxy(commandLineArgs.proxy, errors);
            if (errors.isEmpty()) {
                readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY, parsedProxy[0]);
                readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY, parsedProxy[1]);
                readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY, parsedProxy[2]);
                readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY, parsedProxy[3]);
            }
        }

        // archiving
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ARCHIVE_FAST_UNPACK_KEY, commandLineArgs.archiveFastUnpack);

        // project per folder
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, commandLineArgs.projectPerFolder);

        // Check whether the user inserted scmRepositoriesFile via command line
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SCM_REPOSITORIES_FILE, commandLineArgs.repositoriesFile);

        // User-entry of a flag that overrides default FSA process termination
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.REQUIRE_KNOWN_SHA1, commandLineArgs.requireKnownSha1);

        // docker flag to scan docker images
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SCAN_DOCKER_IMAGES, commandLineArgs.scanDockerImages);

        //docker flag to scan docker images by using docker or tar files folder (which specified with parameter -d)
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SCAN_TAR_IMAGES, commandLineArgs.scanDockerImages);

        //docker flag to delete tar images files after extracting
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.DELETE_TAR_FILES, commandLineArgs.scanDockerImages);

        return offlineRequestFiles;
    }

    //returns data of proxy url from command line parameter proxy
    public static String[] parseProxy(String proxy, List<String> errors) {
        String[] parsedProxyInfo = new String[4];
        if (proxy != null) {
            try {
                URL proxyAsUrl = new  URL(proxy);
                parsedProxyInfo[0] = proxyAsUrl.getHost();
                parsedProxyInfo[1] = String.valueOf(proxyAsUrl.getPort());
                if (proxyAsUrl.getUserInfo() != null) {
                    String[] parsedCred = proxyAsUrl.getUserInfo().split(COLON);
                    parsedProxyInfo[2] = parsedCred[0];
                    if (parsedCred.length > 1) {
                        parsedProxyInfo[3] = parsedCred[1];
                    }
                }

            } catch (MalformedURLException e) {
                errors.add("Malformed proxy url : {}" + e.getMessage());
            }
        }
        return parsedProxyInfo;
    }

    private void readPropertyFromCommandLine(FSAConfigProperties configProps, String propertyKey, String propertyValue) {
        if (StringUtils.isNotBlank(propertyValue)) {
            configProps.put(propertyKey, propertyValue);
        }
    }

    private void readListFromCommandLine(FSAConfigProperties configProps, String propertyKey, List<String> propertyValue) {
        if (!propertyValue.isEmpty()) {
            configProps.put(propertyKey, propertyValue);
        }
    }

    private void commandLineArgsOverride(CommandLineArgs commandLineArgs) {
        useCommandLineProductName = commandLineArgs != null && StringUtils.isNotBlank(commandLineArgs.product);
        useCommandLineProjectName = commandLineArgs != null && StringUtils.isNotBlank(commandLineArgs.project);
    }

    public void validate() {
        getErrors().clear();
        errors.addAll(configurationValidation.getConfigurationErrors(getRequest().isProjectPerSubFolder(), getRequest().getProjectToken(),
                getRequest().getProjectName(), getRequest().getApiToken(), configFilePath, getAgent().getArchiveExtractionDepth(),
                getAgent().getIncludes(), getAgent().getProjectPerFolderIncludes(), getAgent().getPythonRequirementsFileIncludes(), getRequest().getScanComment()));
    }

    //ProxyAuthenticator is used for proxy authentication requests
    static class ProxyAuthenticator extends Authenticator {

        private String user;
        private String password;

        public ProxyAuthenticator(String user, String password) {
            this.user = user;
            if (password != null) {
                this.password = password;
            } else {
                this.password = EMPTY_STRING;
            }
        }

        //called when a request over proxy is executed
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password.toCharArray());
        }
    }

    @Override
    public String toString() {
        return "FSA Configuration {" + Constants.NEW_LINE + WsStringUtils.toString(this) + "}";
    }
}