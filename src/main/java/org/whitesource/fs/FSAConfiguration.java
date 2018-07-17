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

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ViaLanguage;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.MAVEN_IGNORE_POM_MODULES;
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
    private static final int DEFAULT_ARCHIVE_DEPTH = 0;
    private static final String NONE = "(none)";

    @Override
    public String toString() {
        return "FSA Configuration {\n" +
                "logLevel" + logLevel + '\n' +
                "configFilePath=" + configFilePath + '\n' +
                "fileListPath=" + fileListPath + '\n' +
                "dependencyDirs=" + Arrays.asList(dependencyDirs) + '\n' +
                sender.toString() + '\n' +
                resolver.toString() + '\n' +
                ", dependencyDirs=" + Arrays.asList(dependencyDirs) + '\n' +
                request.toString() + '\n' +
                ", requirementsFileIncludes=" + Arrays.asList(requirementsFileIncludes) + '\n' +
                ", scanPackageManager=" + scanPackageManager + '\n' +
                ", scanDockerImages=" + scanDockerImages + '\n' +
                ", requirementsFileIncludes=" + Arrays.asList(requirementsFileIncludes) + '\n' +
                getAgent().toString() + '\n' +
                '}';
    }

    public static final String WHITE_SOURCE_DEFAULT_FOLDER_PATH = ".";
    public static final String PIP = "pip";
    public static final String PYTHON = "python";

    public static final int DEFAULT_PORT = 443;
    public static final boolean DEFAULT_SSL = true;
    private static final boolean DEFAULT_ENABLED = false;

    /* --- Private fields --- */

    private final ScmConfiguration scm;
    private final SenderConfiguration sender;
    private final OfflineConfiguration offline;
    private final ResolverConfiguration resolver;
    private final ConfigurationValidation configurationValidation;
    private final EndPointConfiguration endpoint;

    private final List<String> errors;

    /* --- Private final fields --- */

    private final List<String> offlineRequestFiles;
    private final String fileListPath;
    private List<String> dependencyDirs;
    private final String configFilePath;
    private final AgentConfiguration agent;
    private final RequestConfiguration request;
    private final List<String> requirementsFileIncludes;
    private final boolean scanPackageManager;
    private final boolean scanDockerImages;

    private final String scannedFolders;

    private String logLevel;
    private boolean useCommandLineProductName;
    private boolean useCommandLineProjectName;
    private List<String> appPaths;
    private Map<String, Set<String>> appPathsToDependencyDirs;

    /* --- Constructors --- */

    public FSAConfiguration(Properties config) {
        this(config, null);
    }

    public FSAConfiguration() {
        this(new Properties(), null);
    }

    public FSAConfiguration(String[] args) {
        this(null, args);
    }

    public FSAConfiguration(Properties config, String[] args) {
        configurationValidation = new ConfigurationValidation();
        String projectName;
        errors = new ArrayList<>();
        appPathsToDependencyDirs = new HashMap<>();
        requirementsFileIncludes = new LinkedList<>();
        appPaths = null;
        if ((args != null)) {
            // read command line args
            // validate args // TODO use jCommander validators
            // TODO add usage command
            CommandLineArgs commandLineArgs = new CommandLineArgs();
            new JCommander(commandLineArgs, args);

            if (config == null) {
                Pair<Properties, List<String>> propertiesWithErrors = readWithError(commandLineArgs.configFilePath);
                errors.addAll(propertiesWithErrors.getValue());
                config = propertiesWithErrors.getKey();
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

        scanPackageManager = getBooleanProperty(config, ConfigPropertyKeys.SCAN_PACKAGE_MANAGER, false);
        scanDockerImages = getBooleanProperty(config, ConfigPropertyKeys.SCAN_DOCKER_IMAGES, false);

        if (dependencyDirs == null)
            dependencyDirs = new ArrayList<>();

        // validate scanned folder
        if (dependencyDirs.isEmpty()) {
            dependencyDirs.add(Constants.DOT);
        }

        // validate config
        String projectToken = config.getProperty(ConfigPropertyKeys.PROJECT_TOKEN_PROPERTY_KEY);
        String projectNameFinal = !StringUtils.isBlank(projectName) ? projectName : config.getProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY);
        boolean projectPerFolder = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, false);
        String apiToken = config.getProperty(ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY);
        String userKey = config.getProperty(ConfigPropertyKeys.USER_KEY_PROPERTY_KEY);
        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(config);
        String[] includes = FSAConfiguration.getIncludes(config);
        String[] projectPerFolderIncludes = FSAConfiguration.getProjectPerFolderIncludes(config);
        String[] pythonRequirementsFileIncludes = FSAConfiguration.getPythonIncludes(config);
        String[] argsForAppPathAndDirs = args;
        if (argsForAppPathAndDirs != null && argsForAppPathAndDirs.length == 0 && !dependencyDirs.isEmpty()) {
            argsForAppPathAndDirs = dependencyDirs.toArray(new String[0]);
        }
        initializeDependencyDirs(argsForAppPathAndDirs, config);

        // validate iaLanguage
        String iaLanguage = config.getProperty(ConfigPropertyKeys.IA_LANGUAGE);
        boolean iaLanguageValid = false;
        if (iaLanguage != null) {
            for (ViaLanguage viaLanguage : ViaLanguage.values()) {
                if (iaLanguage.toLowerCase().equals(viaLanguage.toString().toLowerCase())) {
                    iaLanguageValid = true;
                }
            }
            if (!iaLanguageValid) {
                //todo move to debug mode after QA
                errors.add("Error: VIA setting are not applicable parameters are not valid. exiting... ");
            }
            if (iaLanguageValid && !getBooleanProperty(config, ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, false)) {
                //todo move to debug mode after QA
                errors.add("Error: VIA setting are not applicable parameters are not valid. exiting... ");
            }
        }

        // todo: check possibility to get the errors only in the end
        errors.addAll(configurationValidation.getConfigurationErrors(projectPerFolder, projectToken, projectNameFinal,
                apiToken, configFilePath, archiveExtractionDepth, includes, projectPerFolderIncludes, pythonRequirementsFileIncludes));

        logLevel = config.getProperty(ConfigPropertyKeys.LOG_LEVEL_KEY, INFO);

        request = getRequest(config, apiToken, userKey, projectName, projectToken);
        scm = getScm(config);
        agent = getAgent(config);
        offline = getOffline(config);
        sender = getSender(config);
        resolver = getResolver(config);
        endpoint = getEndpoint(config);
    }

    private void initializeDependencyDirs(String[] argsForAppPathAndDirs, Properties config) {
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

    private EndPointConfiguration getEndpoint(Properties config) {
        return new EndPointConfiguration(FSAConfiguration.getIntProperty(config, ConfigPropertyKeys.ENDPOINT_PORT, DEFAULT_PORT),
                config.getProperty(ConfigPropertyKeys.ENDPOINT_CERTIFICATE),
                config.getProperty(ConfigPropertyKeys.ENDPOINT_PASS),
                FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.ENDPOINT_ENABLED, DEFAULT_ENABLED),
                FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.ENDPOINT_SSL_ENABLED, DEFAULT_SSL));
    }

    private ResolverConfiguration getResolver(Properties config) {

        // todo split this in multiple configuration before release fsa as a service
        boolean npmRunPreStep = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_RUN_PRE_STEP, false);
        boolean npmIgnoreScripts = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_IGNORE_SCRIPTS, false);
        boolean npmResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, true);
        boolean npmIncludeDevDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, false);
        boolean npmIgnoreJavaScriptFiles = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_IGNORE_JAVA_SCRIPT_FILES, true);
        long npmTimeoutDependenciesCollector = FSAConfiguration.getLongProperty(config, ConfigPropertyKeys.NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS, 60);
        boolean npmIgnoreNpmLsErrors = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_IGNORE_NPM_LS_ERRORS, false);
        String npmAccessToken = config.getProperty(ConfigPropertyKeys.NPM_ACCESS_TOKEN);
        boolean npmYarnProject = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NPM_YARN_PROJECT, false);

        boolean bowerResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, true);
        boolean bowerRunPreStep = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.BOWER_RUN_PRE_STEP, false);

        boolean nugetResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NUGET_RESOLVE_DEPENDENCIES, true);
        boolean nugetRestoreDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.NUGET_RESTORE_DEPENDENCIES, false);

        boolean mavenResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.MAVEN_RESOLVE_DEPENDENCIES, true);
        String[] mavenIgnoredScopes = FSAConfiguration.getListProperty(config, ConfigPropertyKeys.MAVEN_IGNORED_SCOPES, null);
        boolean mavenAggregateModules = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.MAVEN_AGGREGATE_MODULES, false);
        boolean mavenIgnoredPomModules = FSAConfiguration.getBooleanProperty(config, MAVEN_IGNORE_POM_MODULES, true);

        boolean dependenciesOnly = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.DEPENDENCIES_ONLY, false);

        String whiteSourceConfiguration = config.getProperty(ConfigPropertyKeys.PROJECT_CONFIGURATION_PATH);

        boolean pythonResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_RESOLVE_DEPENDENCIES, true);
        String pipPath = config.getProperty(ConfigPropertyKeys.PYTHON_PIP_PATH, PIP);
        String pythonPath = config.getProperty(ConfigPropertyKeys.PYTHON_PATH, PYTHON);
        boolean pythonIsWssPluginInstalled = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_IS_WSS_PLUGIN_INSTALLED, false);
        boolean pythonUninstallWssPluginInstalled = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_UNINSTALL_WSS_PLUGIN, false);
        boolean pythonIgnorePipInstallErrors = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_IGNORE_PIP_INSTALL_ERRORS, false);
        boolean pythonInstallVirtualenv = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_INSTALL_VIRTUALENV, false);
        boolean pythonResolveHierarchyTree = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PYTHON_RESOLVE_HIERARCHY_TREE, true);
        String[] pythonRequirementsFileIncludes = FSAConfiguration.getListProperty(config, ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, new String[]{Constants.PYTHON_REQUIREMENTS});

        boolean gradleResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.GRADLE_RESOLVE_DEPENDENCIES, true);
        boolean gradleRunAssembleCommand = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.GRADLE_RUN_ASSEMBLE_COMMAND, true);
        boolean gradleAggregateModules = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.GRADLE_AGGREGATE_MODULES, false);

        boolean paketResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PAKET_RESOLVE_DEPENDENCIES, true);
        String[] paketIgnoredScopes = FSAConfiguration.getListProperty(config, ConfigPropertyKeys.PAKET_IGNORED_GROUPS, null);
        boolean paketIgnoreFiles = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PAKET_IGNORE_FILES, true);
        boolean paketRunPreStep = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PAKET_RUN_PRE_STEP, false);
        String paketPath = config.getProperty(ConfigPropertyKeys.PAKET_EXE_PATH, null);

        boolean goResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.GO_RESOLVE_DEPENDENCIES, true);
        String goDependencyManager = config.getProperty(ConfigPropertyKeys.GO_DEPENDENCY_MANAGER, Constants.EMPTY_STRING);
        boolean goCollectDependenciesAtRuntime = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.GO_COLLECT_DEPENDENCIES_AT_RUNTIME, false);

        boolean rubyResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.RUBY_RESOLVE_DEPENDENCIES, true);
        boolean rubyRunBundleInstall = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.RUBY_RUN_BUNDLE_INSTALL, false);
        boolean rubyOverwriteGemFile = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.RUBY_OVERWRITE_GEM_FILE, false);
        boolean rubyInstallMissingGems = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.RUBY_INSTALL_MISSING_GEMS, false);

        boolean phpResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PHP_RESOLVE_DEPENDENCIES, true);
        boolean phpRunPreStep = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PHP_RUN_PRE_STEP, false);
        boolean phpIncludeDevDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PHP_INCLUDE_DEV_DEPENDENCIES, false);

        boolean sbtResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.SBT_RESOLVE_DEPENDENCIES, true);
        boolean sbtAggregateModules = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.SBT_AGGREGATE_MODULES, false);

        boolean htmlResolveDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.HTML_RESOLVE_DEPENDENCIES, true);

        return new ResolverConfiguration(npmRunPreStep, npmResolveDependencies, npmIgnoreScripts, npmIncludeDevDependencies, npmIgnoreJavaScriptFiles,
                npmTimeoutDependenciesCollector, npmAccessToken, npmIgnoreNpmLsErrors, npmYarnProject,
                bowerResolveDependencies, bowerRunPreStep, nugetResolveDependencies, nugetRestoreDependencies,
                mavenResolveDependencies, mavenIgnoredScopes, mavenAggregateModules, mavenIgnoredPomModules,
                pythonResolveDependencies, pipPath, pythonPath, pythonIsWssPluginInstalled, pythonUninstallWssPluginInstalled,
                pythonIgnorePipInstallErrors, pythonInstallVirtualenv, pythonResolveHierarchyTree, pythonRequirementsFileIncludes,
                dependenciesOnly, whiteSourceConfiguration, gradleResolveDependencies, gradleRunAssembleCommand, gradleAggregateModules, paketResolveDependencies,
                paketIgnoredScopes, paketIgnoreFiles, paketRunPreStep, paketPath,
                goResolveDependencies, goDependencyManager, goCollectDependenciesAtRuntime, rubyResolveDependencies, rubyRunBundleInstall,
                rubyOverwriteGemFile, rubyInstallMissingGems,
                phpResolveDependencies, phpRunPreStep, phpIncludeDevDependencies, sbtResolveDependencies, sbtAggregateModules, htmlResolveDependencies);
    }

    private RequestConfiguration getRequest(Properties config, String apiToken, String userKey, String projectName, String projectToken) {
        String productToken = config.getProperty(ConfigPropertyKeys.PRODUCT_TOKEN_PROPERTY_KEY);
        String productName = config.getProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY);
        String productVersion = config.getProperty(ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY);
        String projectVersion = config.getProperty(ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY);
        List<String> appPath = (List<String>) config.get(ConfigPropertyKeys.APP_PATH);
        String iaLanguage = config.getProperty(ConfigPropertyKeys.IA_LANGUAGE, null);
        String viaDebug = config.getProperty(ConfigPropertyKeys.VIA_DEBUG, Constants.EMPTY_STRING);
        boolean projectPerSubFolder = getBooleanProperty(config, ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, false);
        String requesterEmail = config.getProperty(ConfigPropertyKeys.REQUESTER_EMAIL);

        int viaAnalysis = getIntProperty(config, ConfigPropertyKeys.VIA_ANALYSIS_LEVEL, VIA_DEFAULT_ANALYSIS_LEVEL);
        return new RequestConfiguration(apiToken, userKey, requesterEmail, projectPerSubFolder, projectName, projectToken,
                projectVersion, productName, productToken, productVersion, appPath, viaDebug, viaAnalysis, iaLanguage);
    }

    private SenderConfiguration getSender(Properties config) {
        String updateTypeValue = config.getProperty(ConfigPropertyKeys.UPDATE_TYPE, UpdateType.OVERRIDE.toString());
        boolean checkPolicies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.CHECK_POLICIES_PROPERTY_KEY, false);
        boolean forceCheckAllDependencies = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.FORCE_CHECK_ALL_DEPENDENCIES, false);
        boolean forceUpdate = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.FORCE_UPDATE, false);
        boolean enableImpactAnalysis = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, false);
        String serviceUrl = config.getProperty(SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        String proxyHost = config.getProperty(ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY);
        int connectionTimeOut = Integer.parseInt(config.getProperty(ClientConstants.CONNECTION_TIMEOUT_KEYWORD,
                String.valueOf(ClientConstants.DEFAULT_CONNECTION_TIMEOUT_MINUTES)));
        int connectionRetries = FSAConfiguration.getIntProperty(config, ConfigPropertyKeys.CONNECTION_RETRIES, 1);
        int connectionRetriesIntervals = FSAConfiguration.getIntProperty(config, ConfigPropertyKeys.CONNECTION_RETRIES_INTERVALS, 3000);
        String senderPort = config.getProperty(ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY);

        int proxyPort;
        if (StringUtils.isNotEmpty(senderPort)) {
            proxyPort = Integer.parseInt(senderPort);
        } else {
            proxyPort = -1;
        }

        String proxyUser = config.getProperty(ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY);
        String proxyPassword = config.getProperty(ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY);
        boolean ignoreCertificateCheck = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.IGNORE_CERTIFICATE_CHECK, false);

        return new SenderConfiguration(checkPolicies, serviceUrl, connectionTimeOut,
                proxyHost, proxyPort, proxyUser, proxyPassword,
                forceCheckAllDependencies, forceUpdate, updateTypeValue, enableImpactAnalysis, ignoreCertificateCheck, connectionRetries, connectionRetriesIntervals);
    }

    private OfflineConfiguration getOffline(Properties config) {
        boolean enabled = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, false);
        boolean zip = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.OFFLINE_ZIP_PROPERTY_KEY, false);
        boolean prettyJson = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.OFFLINE_PRETTY_JSON_KEY, false);
        String wsFolder = StringUtils.isBlank(config.getProperty(ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH)) ? WHITE_SOURCE_DEFAULT_FOLDER_PATH : config.getProperty(ConfigPropertyKeys.WHITESOURCE_FOLDER_PATH);
        return new OfflineConfiguration(enabled, zip, prettyJson, wsFolder);
    }

    private AgentConfiguration getAgent(Properties config) {
        String[] includes = FSAConfiguration.getIncludes(config);
        String[] excludes = config.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] dockerIncludes = FSAConfiguration.getDockerIncludes(config);
        String[] dockerExcludes = config.getProperty(ConfigPropertyKeys.DOCKER_EXCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] projectPerFolderIncludes = getProjectPerFolderIncludes(config);
        String[] projectPerFolderExcludes = getProjectPerFolderExcludes(config);
        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(config);
        String[] archiveIncludes = config.getProperty(ConfigPropertyKeys.ARCHIVE_INCLUDES_PATTERN_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] archiveExcludes = config.getProperty(ConfigPropertyKeys.ARCHIVE_EXCLUDES_PATTERN_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] pythonRequirementsFileIncludes = FSAConfiguration.getPythonIncludes(config);
        boolean archiveFastUnpack = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.ARCHIVE_FAST_UNPACK_KEY, false);
        boolean archiveFollowSymbolicLinks = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.FOLLOW_SYMBOLIC_LINKS, true);
        boolean dockerScan = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.SCAN_DOCKER_IMAGES, false);
        boolean partialSha1Match = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.PARTIAL_SHA1_MATCH_KEY, false);
        boolean calculateHints = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.CALCULATE_HINTS, false);
        boolean calculateMd5 = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.CALCULATE_MD5, false);
        boolean showProgress = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.SHOW_PROGRESS_BAR, true);
        Pair<Boolean, String> globalCaseSensitive = getGlobalCaseSensitive(config.getProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY));

        //key , val

        Collection<String> excludesCopyrights = getExcludeCopyrights(config.getProperty(ConfigPropertyKeys.EXCLUDED_COPYRIGHT_KEY, Constants.EMPTY_STRING));

        return new AgentConfiguration(includes, excludes, dockerIncludes, dockerExcludes,
                archiveExtractionDepth, archiveIncludes, archiveExcludes, archiveFastUnpack, archiveFollowSymbolicLinks,
                partialSha1Match, calculateHints, calculateMd5, showProgress, globalCaseSensitive.getKey(), dockerScan, excludesCopyrights, projectPerFolderIncludes,
                projectPerFolderExcludes, pythonRequirementsFileIncludes, globalCaseSensitive.getValue());
    }

    private Collection<String> getExcludeCopyrights(String excludedCopyrightsValue) {
        Collection<String> excludes = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(Constants.COMMA)));
        excludes.remove(Constants.EMPTY_STRING);
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

    private ScmConfiguration getScm(Properties config) {
        String type = config.getProperty(ConfigPropertyKeys.SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(ConfigPropertyKeys.SCM_URL_PROPERTY_KEY);
        String user = config.getProperty(ConfigPropertyKeys.SCM_USER_PROPERTY_KEY);
        String pass = config.getProperty(ConfigPropertyKeys.SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(ConfigPropertyKeys.SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(ConfigPropertyKeys.SCM_TAG_PROPERTY_KEY);
        String ppk = config.getProperty(ConfigPropertyKeys.SCM_PPK_PROPERTY_KEY);

        //defaults
        String repositoriesPath = config.getProperty(ConfigPropertyKeys.SCM_REPOSITORIES_FILE);
        boolean npmInstall = FSAConfiguration.getBooleanProperty(config, ConfigPropertyKeys.SCM_NPM_INSTALL, true);
        int npmInstallTimeoutMinutes = FSAConfiguration.getIntProperty(config, ConfigPropertyKeys.SCM_NPM_INSTALL_TIMEOUT_MINUTES, 15);

        return new ScmConfiguration(type, user, pass, ppk, url, branch, tag, repositoriesPath, npmInstall, npmInstallTimeoutMinutes);
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

    public static Pair<Properties, List<String>> readWithError(String configFilePath) {
        Properties configProps = new Properties();
        List<String> errors = new ArrayList<>();
        try {
            try (FileInputStream inputStream = new FileInputStream(configFilePath)) {
                try {
                    configProps.load(inputStream);
                } catch (FileNotFoundException e) {
                    errors.add("Failed to open " + configFilePath + " for reading " + e);
                } catch (IOException e) {
                    errors.add("Error occurred when reading from " + configFilePath + e);
                }
            }
        } catch (IOException e) {
            errors.add("Error occurred when reading from " + configFilePath + " - " + e);
        }
        return new Pair<>(configProps, errors);
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
        String includesString = configProps.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING);
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
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
            return configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_INCLUDES, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        if (Constants.EMPTY_STRING.equals(projectPerFolderIncludesString)) {
            return null;
        }
        String[] result = new String[1];
        result[0] = "*";
        return result;
    }

    public static String[] getProjectPerFolderExcludes(Properties configProps) {
        String projectPerFolderExcludesString = configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, Constants.EMPTY_STRING);
        if (StringUtils.isNotBlank(projectPerFolderExcludesString)) {
            return configProps.getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public static String[] getDockerIncludes(Properties configProps) {
        String includesString = configProps.getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING);
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public List<String> getRequirementsFileIncludes() {
        return requirementsFileIncludes;
    }

    /* --- Private methods --- */

    private List<String> updateProperties(Properties configProps, CommandLineArgs commandLineArgs) {
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
        // proxy
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY, commandLineArgs.proxyHost);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY, commandLineArgs.proxyPort);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY, commandLineArgs.proxyUser);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY, commandLineArgs.proxyPass);

        // archiving
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ARCHIVE_FAST_UNPACK_KEY, commandLineArgs.archiveFastUnpack);

        // project per folder
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROJECT_PER_SUBFOLDER, commandLineArgs.projectPerFolder);

        // Check whether the user inserted scmRepositoriesFile via command line
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.SCM_REPOSITORIES_FILE, commandLineArgs.repositoriesFile);

        return offlineRequestFiles;
    }

    private void readPropertyFromCommandLine(Properties configProps, String propertyKey, String propertyValue) {
        if (StringUtils.isNotBlank(propertyValue)) {
            configProps.put(propertyKey, propertyValue);
        }
    }

    private void readListFromCommandLine(Properties configProps, String propertyKey, List<String> propertyValue) {
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
                getAgent().getIncludes(), getAgent().getProjectPerFolderIncludes(), getAgent().getPythonRequirementsFileIncludes()));
    }
}