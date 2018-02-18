/**
 * Copyright (C) 2014 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent;

/**
 * Property keys for the whitesource-docker-agent.configuration file.
 *
 * @author itai.marko
 * @author tom.shapira
 */
public final class ConfigPropertyKeys {

    public static final String CHECK_POLICIES_PROPERTY_KEY = "checkPolicies";
    public static final String FORCE_UPDATE = "forceUpdate";
    public static final String FORCE_CHECK_ALL_DEPENDENCIES = "forceCheckAllDependencies"; // optional
    public static final String ENABLE_IMPACT_ANALYSIS = "enableImpactAnalysis"; // optional
    public static final String CONNECTION_RETRIES = "connectionRetries";
    public static final String CONNECTION_RETRIES_INTERVALS = "connectionRetriesInterval";
    public static final String ORG_TOKEN_PROPERTY_KEY = "apiKey";
    public static final String PARTIAL_SHA1_MATCH_KEY = "partialSha1Match";
    public static final String PRODUCT_TOKEN_PROPERTY_KEY = "productToken"; // optional
    public static final String PRODUCT_NAME_PROPERTY_KEY = "productName"; // optional
    public static final String PRODUCT_VERSION_PROPERTY_KEY = "productVersion"; // optional
    public static final String APP_PATH = "appPath"; // optional
    public static final String PROJECT_TOKEN_PROPERTY_KEY = "projectToken";
    public static final String PROJECT_NAME_PROPERTY_KEY = "projectName";
    public static final String PROJECT_VERSION_PROPERTY_KEY = "projectVersion"; // optional
    public static final String INCLUDES_PATTERN_PROPERTY_KEY = "includes";
    public static final String EXCLUDES_PATTERN_PROPERTY_KEY = "excludes";
    public static final String ARCHIVE_EXTRACTION_DEPTH_KEY = "archiveExtractionDepth";
    public static final String ARCHIVE_INCLUDES_PATTERN_KEY = "archiveIncludes";
    public static final String ARCHIVE_EXCLUDES_PATTERN_KEY = "archiveExcludes";
    public static final String ARCHIVE_FAST_UNPACK_KEY = "archiveFastUnpack";
    public static final String CALCULATE_HINTS = "calculate.hints";
    public static final String CALCULATE_MD5 = "calculate.md5";
    public static final String REQUESTER_EMAIL = "requesterEmail";
    public static final String CASE_SENSITIVE_GLOB_PROPERTY_KEY = "case.sensitive.glob";
    public static final String PROXY_HOST_PROPERTY_KEY = "proxy.host";
    public static final String PROXY_PORT_PROPERTY_KEY = "proxy.port";
    public static final String PROXY_USER_PROPERTY_KEY = "proxy.user";
    public static final String PROXY_PASS_PROPERTY_KEY = "proxy.pass";
    public static final String IGNORE_CERTIFICATE_CHECK = "ignoreCertificateCheck";
    public static final String OFFLINE_PROPERTY_KEY = "offline";
    public static final String OFFLINE_ZIP_PROPERTY_KEY = "offline.zip";
    public static final String OFFLINE_PRETTY_JSON_KEY = "offline.prettyJson";
    public static final String SCM_TYPE_PROPERTY_KEY = "scm.type";
    public static final String SCM_URL_PROPERTY_KEY = "scm.url";
    public static final String SCM_PPK_PROPERTY_KEY = "scm.ppk";
    public static final String SCM_USER_PROPERTY_KEY = "scm.user";
    public static final String SCM_PASS_PROPERTY_KEY = "scm.pass";
    public static final String SCM_BRANCH_PROPERTY_KEY = "scm.branch";
    public static final String SCM_TAG_PROPERTY_KEY = "scm.tag";
    public static final String SCM_NPM_INSTALL = "scm.npmInstall";
    public static final String SCM_NPM_INSTALL_TIMEOUT_MINUTES = "scm.npmInstallTimeoutMinutes";
    public static final String SCM_REPOSITORIES_FILE = "scm.repositoriesFile";
    public static final String EXCLUDED_COPYRIGHT_KEY = "copyright.excludes";
    public static final String LOG_LEVEL_KEY = "log.level";
    public static final String FOLLOW_SYMBOLIC_LINKS = "followSymbolicLinks";
    public static final String SHOW_PROGRESS_BAR = "showProgressBar";

    public static final String NPM_RUN_PRE_STEP = "npm.runPreStep";
    public static final String NPM_RESOLVE_DEPENDENCIES = "npm.resolveDependencies";
    public static final String NPM_INCLUDE_DEV_DEPENDENCIES = "npm.includeDevDependencies";
    public static final String NPM_IGNORE_JAVA_SCRIPT_FILES = "npm.ignoreJavaScriptFiles";
    public static final String NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS = "npm.timeoutDependenciesCollectorInSeconds";
    public static final String NPM_ACCESS_TOKEN = "npm.accessToken";
    public static final String NPM_IGNORE_NPM_LS_ERRORS = "npm.ignoreNpmLsErrors";

    public static final String BOWER_RESOLVE_DEPENDENCIES = "bower.resolveDependencies";
    public static final String BOWER_RUN_PRE_STEP = "bower.runPreStep";

    public static final String PYTHON_RESOLVE_DEPENDENCIES = "python.resolveDependencies";
    public static final String PYTHON_PIP_PATH = "python.pipPath";
    public static final String PYTHON_PYTHON_PATH = "python.pythonPathPath";

    public static final String NUGET_RESOLVE_DEPENDENCIES = "nuget.resolveDependencies";
    public static final String MAVEN_IGNORED_SCOPES = "maven.ignoredScopes";
    public static final String MAVEN_RESOLVE_DEPENDENCIES = "maven.resolveDependencies";
    public static final String MAVEN_AGGREGATE_MODULES = "maven.aggregateModules";
    public static final String DEPENDENCIES_ONLY = "dependenciesOnly";
    public static final String PROJECT_PER_SUBFOLDER = "projectPerFolder";
    public static final String UPDATE_TYPE = "updateType";
    public static final String PROJECT_CONFIGURATION_PATH = "configFilePath";
    public static final String SCAN_PACKAGE_MANAGER = "scanPackageManager";
    public static final String WHITESOURCE_FOLDER_PATH = "whiteSourceFolderPath";

    public static final String ENDPOINT_ENABLED = "endpoint.enabled";
    public static final String ENDPOINT_PORT = "endpoint.port";
    public static final String ENDPOINT_CERTIFICATE = "endpoint.certificate";
    public static final String ENDPOINT_PASS = "endpoint.pass";
    public static final String ENDPOINT_SSL_ENABLED = "endpoint.ssl";

    public static final String WHITESOURCE_CONFIGURATION = "whitesourceConfiguration";
}