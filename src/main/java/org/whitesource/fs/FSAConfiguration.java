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
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class FSAConfiguration {

    /* --- Static members --- */

    private static final String FALSE = "false";
    private static final String INFO = "info";
    public static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";
    private static final int DEFAULT_ARCHIVE_DEPTH = 0;
    private static final String NONE = "(none)";
    private static final String DEFAULT_API_TOKEN = "apiToken";
    private static final String DAFAULT_SAMPLE_PROJECT = "sampleProject";
    private static final String SPACE =" ";

    /* --- Private fields --- */

    private final String productToken;
    private final String productName;
    private final String productVersion;

    private final String projectName;
    private final ScmConfiguration scm;
    private final SenderConfiguration sender;
    private final OfflineConfiguration offline;
    private final ResolverConfiguration resolver;
    private final ConfigurationValidation configurationValidation;
    private final EndPointConfiguration endpoint;

    private final boolean hasErrors;

    /* --- Private final fields --- */

    private final List<String> offlineRequestFiles;
    private final String fileListPath;
    private final List<String> dependencyDirs;

    private final String[] includes;
    private final String[] excludes;
    private final int archiveExtractionDepth;
    private final String[] archiveIncludes;
    private final String[] archiveExcludes;
    private final boolean archiveFastUnpack;
    private final boolean followSymlinks;
    private final boolean partialSha1Match;
    private final boolean calculateHints;
    private final boolean calculateMd5;

    private final String projectVersion;
    private final String projectToken;
    private final boolean projectPerSubFolder;
    private final String orgToken;
    private final String requesterEmail;

    private final boolean showProgressBar;
    private final String excludedCopyrightsValue;
    private final String globCaseSensitiveValue;

    private String logLevel;

    /* --- Constructors --- */

    public FSAConfiguration(Properties config) {
        this(config,null);
    }

    public FSAConfiguration() {
        this(null,null);
    }

    public FSAConfiguration(String[] args) {
        this(null,args);
    }

    public FSAConfiguration(Properties config , String [] args) {

        configurationValidation = new ConfigurationValidation();

        //args provided and getSender not provided
        //if ((args != null && args.length !=0)) {
        if ((args != null)) {
            // read command line args
            // validate args // TODO use jCommander validators
            // TODO add usage command
            CommandLineArgs commandLineArgs = new CommandLineArgs();
            new JCommander(commandLineArgs, args);

            if (config == null) {
                Pair<Properties,Boolean> validationResult = configurationValidation.readWithError(commandLineArgs.configFilePath, commandLineArgs.project);
                config = validationResult.getKey();
                if(StringUtils.isNotEmpty(commandLineArgs.project)) {
                    config.setProperty(PROJECT_NAME_PROPERTY_KEY, commandLineArgs.project);
                }
            }
            config.setProperty(PROJECT_CONFIGURATION_PATH, commandLineArgs.configFilePath);

            //override
            offlineRequestFiles = updateProperties(config, commandLineArgs);
            projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
            hasErrors = configurationValidation.isConfigurationInError(config, NONE, projectName);
            fileListPath = commandLineArgs.fileListPath;
            dependencyDirs = commandLineArgs.dependencyDirs;

        }else {
            if (config != null) {
                projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
            }
            else {
                config = new Properties();
                projectName = DAFAULT_SAMPLE_PROJECT;
                config.setProperty(PROJECT_NAME_PROPERTY_KEY, projectName);
                String apiKey = DEFAULT_API_TOKEN;
                config.setProperty(ORG_TOKEN_PROPERTY_KEY, apiKey);
            }
            hasErrors = configurationValidation.isConfigurationInError(config, NONE, projectName);
            offlineRequestFiles = new ArrayList<>();
            fileListPath = null;
            dependencyDirs = new ArrayList<>();
        }

        productToken = config.getProperty(ConfigPropertyKeys.PRODUCT_TOKEN_PROPERTY_KEY);
        productName = config.getProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY);
        productVersion =config.getProperty(ConfigPropertyKeys.PRODUCT_VERSION_PROPERTY_KEY);

        logLevel = config.getProperty(LOG_LEVEL_KEY, INFO);

        scm = new ScmConfiguration(config);

        // read all properties
        includes = getIncludes(config);
        excludes = config.getProperty(EXCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);

        archiveExtractionDepth = getArchiveDepth(config);
        archiveIncludes = config.getProperty(ARCHIVE_INCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        archiveExcludes = config.getProperty(ARCHIVE_EXCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        archiveFastUnpack = getBooleanProperty(config, ARCHIVE_FAST_UNPACK_KEY, false);

        followSymlinks = getBooleanProperty(config, FOLLOW_SYMBOLIC_LINKS, true);
        // check scan partial sha1s (false by default)
        partialSha1Match = getBooleanProperty(config, PARTIAL_SHA1_MATCH_KEY, false);
        calculateHints = getBooleanProperty(config, CALCULATE_HINTS, false);
        calculateMd5 = getBooleanProperty(config, CALCULATE_MD5, false);
        globCaseSensitiveValue = config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY);
        excludedCopyrightsValue = config.getProperty(EXCLUDED_COPYRIGHT_KEY, "");
        showProgressBar = getBooleanProperty(config, SHOW_PROGRESS_BAR, true);


        orgToken = config.getProperty(ORG_TOKEN_PROPERTY_KEY);
        projectVersion = config.getProperty(PROJECT_VERSION_PROPERTY_KEY);
        projectToken = config.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        projectPerSubFolder = getBooleanProperty(config, PROJECT_PER_SUBFOLDER, false);
        requesterEmail = config.getProperty(REQUESTER_EMAIL);

        offline = new OfflineConfiguration(config);
        sender = new SenderConfiguration(config);
        resolver = new ResolverConfiguration(config);

        endpoint = new EndPointConfiguration(config);
    }

    /* --- Public getters --- */
    public EndPointConfiguration getEndpoint() {
        return endpoint;
    }

    public SenderConfiguration getSender() {
        return sender;
    }

    public ScmConfiguration getScm() {
        return scm;
    }

    public OfflineConfiguration getOffline() {
        return offline;
    }

    public ResolverConfiguration getResolver() {
        return resolver;
    }

    public boolean getHasErrors() {
        return hasErrors;
    }

    public boolean isShowProgressBar() {
        return showProgressBar;
    }

    public String getExcludedCopyrightsValue() {
        return excludedCopyrightsValue;
    }

    public String getGlobCaseSensitiveValue() {
        return globCaseSensitiveValue;
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

    public String[] getIncludes() {
        return includes;
    }

    public String[] getExcludes() {
        return excludes;
    }

    public int getArchiveExtractionDepth() {
        return archiveExtractionDepth;
    }

    public String[] getArchiveIncludes() {
        return archiveIncludes;
    }

    public String[] getArchiveExcludes() {
        return archiveExcludes;
    }

    public boolean isArchiveFastUnpack() {
        return archiveFastUnpack;
    }

    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    public boolean isPartialSha1Match() {
        return partialSha1Match;
    }

    public boolean isCalculateHints() {
        return calculateHints;
    }

    public boolean isCalculateMd5() {
        return calculateMd5;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectToken() {
        return projectToken;
    }

    public String getProductToken() {
        return productToken;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public boolean isProjectPerSubFolder() {
        return projectPerSubFolder;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getOrgToken() {
        return orgToken;
    }

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
        if (property == null){
            return defaultValue;
        }
        return property.split(SPACE);
    }

    public static int getArchiveDepth(Properties configProps) {
        return getIntProperty(configProps, ARCHIVE_EXTRACTION_DEPTH_KEY,  FSAConfiguration.DEFAULT_ARCHIVE_DEPTH);
    }

    public static String[] getIncludes(Properties configProps) {
        return configProps.getProperty(INCLUDES_PATTERN_PROPERTY_KEY, "").split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
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

        // proxy
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_HOST_PROPERTY_KEY, commandLineArgs.proxyHost);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PORT_PROPERTY_KEY, commandLineArgs.proxyPass);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_USER_PROPERTY_KEY, commandLineArgs.proxyPort);
        readPropertyFromCommandLine(configProps, ConfigPropertyKeys.PROXY_PASS_PROPERTY_KEY, commandLineArgs.proxyUser);

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
}
