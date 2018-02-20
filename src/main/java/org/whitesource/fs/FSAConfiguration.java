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
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.*;
import static org.whitesource.agent.client.ClientConstants.SERVICE_URL_KEYWORD;
import static org.whitesource.fs.FileSystemAgent.EMPTY_STRING;
import static org.whitesource.fs.FileSystemAgent.EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX;

/**
 * Author: eugen.horovitz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FSAConfiguration {

    /* --- Static members --- */

    public static Collection<String> ignoredWebProperties = Arrays.asList(
            SCM_REPOSITORIES_FILE, LOG_LEVEL_KEY, FOLLOW_SYMBOLIC_LINKS, SHOW_PROGRESS_BAR, PROJECT_CONFIGURATION_PATH, SCAN_PACKAGE_MANAGER, WHITESOURCE_FOLDER_PATH,
            ENDPOINT_ENABLED, ENDPOINT_PORT, ENDPOINT_CERTIFICATE, ENDPOINT_PASS, ENDPOINT_SSL_ENABLED, OFFLINE_PROPERTY_KEY, OFFLINE_ZIP_PROPERTY_KEY, OFFLINE_PRETTY_JSON_KEY, WHITESOURCE_CONFIGURATION);

    private static final String FALSE = "false";
    private static final String INFO = "info";
    public static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";
    private static final int DEFAULT_ARCHIVE_DEPTH = 0;
    private static final String NONE = "(none)";
    private static final String SPACE = " ";
    private static final String BLANK = "";

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
    private final List<String> dependencyDirs;
    private final String configFilePath;
    private final AgentConfiguration agent;
    private final RequestConfiguration request;
    private final boolean scanPackageManager;

    private String logLevel;
    private boolean useCommandLineProductName;
    private boolean useCommandLineProjectName;

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
                    config.setProperty(PROJECT_NAME_PROPERTY_KEY, commandLineArgs.project);
                }
            }

            configFilePath = commandLineArgs.configFilePath;
            config.setProperty(PROJECT_CONFIGURATION_PATH, commandLineArgs.configFilePath);

            //override
            offlineRequestFiles = updateProperties(config, commandLineArgs);
            projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
            fileListPath = commandLineArgs.fileListPath;
            dependencyDirs = commandLineArgs.dependencyDirs;
            if (StringUtils.isNotBlank(commandLineArgs.whiteSourceFolder)) {
                config.setProperty(WHITESOURCE_FOLDER_PATH, commandLineArgs.whiteSourceFolder);
            }
            commandLineArgsOverride(commandLineArgs);
        } else {
            projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
            configFilePath = NONE;
            offlineRequestFiles = new ArrayList<>();
            fileListPath = null;
            dependencyDirs = new ArrayList<>();
            commandLineArgsOverride(null);
        }

        scanPackageManager = getBooleanProperty(config, SCAN_PACKAGE_MANAGER, false);

        // validate config
        String projectToken = config.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        String projectNameFinal = !StringUtils.isBlank(projectName) ? projectName : config.getProperty(PROJECT_NAME_PROPERTY_KEY);
        boolean projectPerFolder = FSAConfiguration.getBooleanProperty(config, PROJECT_PER_SUBFOLDER, false);
        String apiToken = config.getProperty(ORG_TOKEN_PROPERTY_KEY);
        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(config);
        String[] includes = FSAConfiguration.getIncludes(config);

        // todo: check possibility to get the errors only in the end
        errors.addAll(configurationValidation.getConfigurationErrors(projectPerFolder, projectToken, projectNameFinal, apiToken, configFilePath, archiveExtractionDepth, includes));

        logLevel = config.getProperty(LOG_LEVEL_KEY, INFO);

        request = getRequest(config, apiToken, projectName, projectToken);
        scm = getScm(config);
        agent = getAgent(config);
        offline = getOffline(config);
        sender = getSender(config);
        resolver = getResolver(config);
        endpoint = getEndpoint(config);
    }

    private EndPointConfiguration getEndpoint(Properties config) {
        return new EndPointConfiguration(FSAConfiguration.getIntProperty(config, ENDPOINT_PORT, DEFAULT_PORT),
                config.getProperty(ENDPOINT_CERTIFICATE),
                config.getProperty(ENDPOINT_PASS),
                FSAConfiguration.getBooleanProperty(config, ENDPOINT_ENABLED, DEFAULT_ENABLED),
                FSAConfiguration.getBooleanProperty(config, ENDPOINT_SSL_ENABLED, DEFAULT_SSL));
    }

    private ResolverConfiguration getResolver(Properties config) {

        // todo split this in multiple configuration before release fsa as a service
        boolean npmRunPreStep = FSAConfiguration.getBooleanProperty(config, NPM_RUN_PRE_STEP, false);
        boolean npmResolveDependencies = FSAConfiguration.getBooleanProperty(config, NPM_RESOLVE_DEPENDENCIES, true);
        boolean npmIncludeDevDependencies = FSAConfiguration.getBooleanProperty(config, NPM_INCLUDE_DEV_DEPENDENCIES, false);
        boolean npmIgnoreJavaScriptFiles = FSAConfiguration.getBooleanProperty(config, NPM_IGNORE_JAVA_SCRIPT_FILES, true);
        long npmTimeoutDependenciesCollector = FSAConfiguration.getLongProperty(config, NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS, 60);
        boolean npmIgnoreNpmLsErrors = FSAConfiguration.getBooleanProperty(config, NPM_IGNORE_NPM_LS_ERRORS, false);
        String npmAccessToken = config.getProperty(NPM_ACCESS_TOKEN);

        boolean bowerResolveDependencies = FSAConfiguration.getBooleanProperty(config, BOWER_RESOLVE_DEPENDENCIES, true);
        boolean bowerRunPreStep = FSAConfiguration.getBooleanProperty(config, BOWER_RUN_PRE_STEP, false);

        boolean nugetResolveDependencies = FSAConfiguration.getBooleanProperty(config, NUGET_RESOLVE_DEPENDENCIES, true);

        boolean mavenResolveDependencies = FSAConfiguration.getBooleanProperty(config, MAVEN_RESOLVE_DEPENDENCIES, true);
        String[] mavenIgnoredScopes = FSAConfiguration.getListProperty(config, MAVEN_IGNORED_SCOPES, null);
        boolean mavenAggregateModules = FSAConfiguration.getBooleanProperty(config, MAVEN_AGGREGATE_MODULES, true);

        boolean dependenciesOnly = FSAConfiguration.getBooleanProperty(config, DEPENDENCIES_ONLY, false);

        String whitesourceConfiguration = config.getProperty(PROJECT_CONFIGURATION_PATH);

        boolean pythonResolveDependencies = FSAConfiguration.getBooleanProperty(config, PYTHON_RESOLVE_DEPENDENCIES, true);
        String pipPath = config.getProperty(PYTHON_PIP_PATH, PIP);
        String pythonPath = config.getProperty(PYTHON_PATH, PYTHON);
        boolean pythonIsWssPluginInstalled = FSAConfiguration.getBooleanProperty(config, PYTHON_IS_WSS_PLUGIN_INSTALLED, false);
        boolean pythonUninstallWssPluginInstalled = FSAConfiguration.getBooleanProperty(config, PYTHON_UNINSTALL_WSS_PLUGIN, true);

        return new ResolverConfiguration(npmRunPreStep, npmResolveDependencies, npmIncludeDevDependencies, npmIgnoreJavaScriptFiles, npmTimeoutDependenciesCollector, npmAccessToken, npmIgnoreNpmLsErrors,
                bowerResolveDependencies, bowerRunPreStep, nugetResolveDependencies,
                mavenResolveDependencies, mavenIgnoredScopes, mavenAggregateModules,
                pythonResolveDependencies, pipPath, pythonPath, pythonIsWssPluginInstalled, pythonUninstallWssPluginInstalled,
                dependenciesOnly, whitesourceConfiguration);
    }

    private RequestConfiguration getRequest(Properties config, String apiToken, String projectName, String projectToken) {
        String productToken = config.getProperty(ConfigPropertyKeys.PRODUCT_TOKEN_PROPERTY_KEY);
        String productName = config.getProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY);
        String productVersion = config.getProperty(ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY);
        String projectVersion = config.getProperty(PROJECT_VERSION_PROPERTY_KEY);
        String appPath = config.getProperty(APP_PATH, BLANK);
        boolean projectPerSubFolder = getBooleanProperty(config, PROJECT_PER_SUBFOLDER, false);
        String requesterEmail = config.getProperty(REQUESTER_EMAIL);
        return new RequestConfiguration(apiToken, requesterEmail, projectPerSubFolder, projectName, projectToken, projectVersion, productName, productToken, productVersion, appPath);
    }

    private SenderConfiguration getSender(Properties config) {
        String updateTypeValue = config.getProperty(UPDATE_TYPE, UpdateType.OVERRIDE.toString());
        boolean checkPolicies = FSAConfiguration.getBooleanProperty(config, CHECK_POLICIES_PROPERTY_KEY, false);
        boolean forceCheckAllDependencies = FSAConfiguration.getBooleanProperty(config, FORCE_CHECK_ALL_DEPENDENCIES, false);
        boolean forceUpdate = FSAConfiguration.getBooleanProperty(config, FORCE_UPDATE, false);
        boolean enableImpactAnalysis = FSAConfiguration.getBooleanProperty(config, ENABLE_IMPACT_ANALYSIS, false);
        String serviceUrl = config.getProperty(SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        String proxyHost = config.getProperty(PROXY_HOST_PROPERTY_KEY);
        int connectionTimeOut = Integer.parseInt(config.getProperty(ClientConstants.CONNECTION_TIMEOUT_KEYWORD,
                String.valueOf(ClientConstants.DEFAULT_CONNECTION_TIMEOUT_MINUTES)));
        int connectionRetries = FSAConfiguration.getIntProperty(config, CONNECTION_RETRIES, 1);
        int connectionRetriesIntervals = FSAConfiguration.getIntProperty(config, CONNECTION_RETRIES_INTERVALS, 3000);
        String senderPort = config.getProperty(PROXY_PORT_PROPERTY_KEY);

        int proxyPort;
        if (StringUtils.isNotEmpty(senderPort)) {
            proxyPort = Integer.parseInt(senderPort);
        } else {
            proxyPort = -1;
        }

        String proxyUser = config.getProperty(PROXY_USER_PROPERTY_KEY);
        String proxyPassword = config.getProperty(PROXY_PASS_PROPERTY_KEY);
        boolean ignoreCertificateCheck = FSAConfiguration.getBooleanProperty(config, IGNORE_CERTIFICATE_CHECK, false);

        return new SenderConfiguration(checkPolicies, serviceUrl, connectionTimeOut,
                proxyHost, proxyPort, proxyUser, proxyPassword,
                forceCheckAllDependencies, forceUpdate, updateTypeValue, enableImpactAnalysis, ignoreCertificateCheck, connectionRetries, connectionRetriesIntervals);
    }

    private OfflineConfiguration getOffline(Properties config) {
        boolean enabled = FSAConfiguration.getBooleanProperty(config, OFFLINE_PROPERTY_KEY, false);
        boolean zip = FSAConfiguration.getBooleanProperty(config, OFFLINE_ZIP_PROPERTY_KEY, false);
        boolean prettyJson = FSAConfiguration.getBooleanProperty(config, OFFLINE_PRETTY_JSON_KEY, false);
        String wsFolder = StringUtils.isBlank(config.getProperty(WHITESOURCE_FOLDER_PATH)) ? WHITE_SOURCE_DEFAULT_FOLDER_PATH : config.getProperty(WHITESOURCE_FOLDER_PATH);
        return new OfflineConfiguration(enabled, zip, prettyJson, wsFolder);
    }

    private AgentConfiguration getAgent(Properties config) {
        String[] includes = FSAConfiguration.getIncludes(config);
        String[] excludes = config.getProperty(EXCLUDES_PATTERN_PROPERTY_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(config);
        String[] archiveIncludes = config.getProperty(ARCHIVE_INCLUDES_PATTERN_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        String[] archiveExcludes = config.getProperty(ARCHIVE_EXCLUDES_PATTERN_KEY, EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        boolean archiveFastUnpack = FSAConfiguration.getBooleanProperty(config, ARCHIVE_FAST_UNPACK_KEY, false);
        boolean archiveFollowSymbolicLinks = FSAConfiguration.getBooleanProperty(config, FOLLOW_SYMBOLIC_LINKS, true);

        boolean partialSha1Match = FSAConfiguration.getBooleanProperty(config, PARTIAL_SHA1_MATCH_KEY, false);
        boolean calculateHints = FSAConfiguration.getBooleanProperty(config, CALCULATE_HINTS, false);
        boolean calculateMd5 = FSAConfiguration.getBooleanProperty(config, CALCULATE_MD5, false);
        boolean showProgress = FSAConfiguration.getBooleanProperty(config, SHOW_PROGRESS_BAR, true);
        Pair<Boolean, String> globalCaseSensitive = getGlobalCaseSensitive(config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY));
        //key , val

        Collection<String> excludesCopyrights = getExcludeCopyrights(config.getProperty(EXCLUDED_COPYRIGHT_KEY, ""));

        return new AgentConfiguration(includes, excludes,
                archiveExtractionDepth, archiveIncludes, archiveExcludes, archiveFastUnpack, archiveFollowSymbolicLinks,
                partialSha1Match, calculateHints, calculateMd5, showProgress, globalCaseSensitive.getKey(), globalCaseSensitive.getValue(), excludesCopyrights);
    }

    private Collection<String> getExcludeCopyrights(String excludedCopyrightsValue) {
        Collection<String> excludes = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludes.remove("");
        return excludes;
    }

    private Pair<Boolean, String> getGlobalCaseSensitive(String globCaseSensitiveValue) {
        boolean globCaseSensitive = false;
        String error = null;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase("true") || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
                error = null;
            } else if (globCaseSensitiveValue.equalsIgnoreCase("false") || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
                error = null;
            } else {
                error = "Bad " + CASE_SENSITIVE_GLOB_PROPERTY_KEY + ". Received " + globCaseSensitiveValue + ", required true/false or y/n";
            }
        } else {
            error = null;
        }
        return new Pair<>(globCaseSensitive, error);
    }

    private ScmConfiguration getScm(Properties config) {
        String type = config.getProperty(SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(SCM_URL_PROPERTY_KEY);
        String user = config.getProperty(SCM_USER_PROPERTY_KEY);
        String pass = config.getProperty(SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(SCM_TAG_PROPERTY_KEY);
        String ppk = config.getProperty(SCM_BRANCH_PROPERTY_KEY);

        //defaults
        String repositoriesPath = config.getProperty(SCM_REPOSITORIES_FILE);
        boolean npmInstall = FSAConfiguration.getBooleanProperty(config, SCM_NPM_INSTALL, true);
        int npmInstallTimeoutMinutes = FSAConfiguration.getIntProperty(config, SCM_NPM_INSTALL_TIMEOUT_MINUTES, 15);

        return new ScmConfiguration(type, user, pass, ppk, url, branch, tag, repositoriesPath, npmInstall, npmInstallTimeoutMinutes);
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

    @JsonProperty(SCAN_PACKAGE_MANAGER)
    public boolean isScanProjectManager() {
        return scanPackageManager;
    }

    @JsonProperty(LOG_LEVEL_KEY)
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
        return property.split(SPACE);
    }

    public static int getArchiveDepth(Properties configProps) {
        return getIntProperty(configProps, ARCHIVE_EXTRACTION_DEPTH_KEY, FSAConfiguration.DEFAULT_ARCHIVE_DEPTH);
    }

    public static String[] getIncludes(Properties configProps) {
        String includesString = configProps.getProperty(INCLUDES_PATTERN_PROPERTY_KEY, "");
        if (StringUtils.isNotBlank(includesString)) {
            return configProps.getProperty(INCLUDES_PATTERN_PROPERTY_KEY, "").split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    /* --- Private methods --- */

    private List<String> updateProperties(Properties configProps, CommandLineArgs commandLineArgs) {
        // Check whether the user inserted api key, project OR/AND product via command line
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY, commandLineArgs.apiKey);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.UPDATE_TYPE, commandLineArgs.updateType);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY, commandLineArgs.product);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY, commandLineArgs.productVersion);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY, commandLineArgs.projectVersion);

        // request file
        List<String> offlineRequestFiles = new LinkedList<>();
        offlineRequestFiles.addAll(commandLineArgs.requestFiles);
        if (offlineRequestFiles.size() > 0) {
            configProps.put(ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, FALSE);
        }
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, commandLineArgs.offline);
        //Impact Analysis parameters
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.APP_PATH, commandLineArgs.appPath);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.ENABLE_IMPACT_ANALYSIS, commandLineArgs.enableImpactAnalysis);
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

    private void commandLineArgsOverride(CommandLineArgs commandLineArgs) {
        useCommandLineProductName = commandLineArgs != null && StringUtils.isNotBlank(commandLineArgs.product);
        useCommandLineProjectName = commandLineArgs != null && StringUtils.isNotBlank(commandLineArgs.project);
    }

    public void validate() {
        getErrors().clear();
        errors.addAll(configurationValidation.getConfigurationErrors(getRequest().isProjectPerSubFolder(), getRequest().getProjectToken(),
                getRequest().getProjectName(), getRequest().getApiToken(), configFilePath, getAgent().getArchiveExtractionDepth(), getAgent().getIncludes()));
    }
}