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
package org.whitesource.fs.configuration;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.whitesource.fs.FSAConfiguration;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class ResolverConfiguration {

    /* --- Constructors --- */

    @JsonCreator
    public ResolverConfiguration(
             @JsonProperty(NPM_RUN_PRE_STEP) boolean npmRunPreStep,
             @JsonProperty(NPM_RESOLVE_DEPENDENCIES) boolean npmResolveDependencies,
             @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES) boolean npmIncludeDevDependencies,
             @JsonProperty(NPM_IGNORE_JAVA_SCRIPT_FILES) boolean npmIgnoreJavaScriptFiles,
             @JsonProperty(NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS) long npmTimeoutDependenciesCollector,
             @JsonProperty(NPM_ACCESS_TOKEN) String npmAccessToken,

             @JsonProperty(BOWER_RESOLVE_DEPENDENCIES) boolean bowerResolveDependencies,
             @JsonProperty(BOWER_RUN_PRE_STEP) boolean bowerRunPreStep,

             @JsonProperty(NUGET_RESOLVE_DEPENDENCIES) boolean nugetResolveDependencies,

             @JsonProperty(MAVEN_RESOLVE_DEPENDENCIES) boolean mavenResolveDependencies,
             @JsonProperty(MAVEN_IGNORED_SCOPES) String[] mavenIgnoredScopes,
             @JsonProperty(MAVEN_AGGREGATE_MODULES) boolean mavenAggregateModules,

             @JsonProperty(DEPENDENCIES_ONLY) boolean dependenciesOnly,
             @JsonProperty(WHITESOURCE_CONFIGURATION) String whitesourceConfiguration
    ) {
        this.npmRunPreStep = npmRunPreStep;
        this.npmResolveDependencies = npmResolveDependencies;
        this.npmIncludeDevDependencies = npmIncludeDevDependencies;
        this.npmIgnoreJavaScriptFiles = npmIgnoreJavaScriptFiles;
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.npmAccessToken = npmAccessToken;

        this.bowerResolveDependencies = bowerResolveDependencies;
        this.bowerRunPreStep = bowerRunPreStep;

        this.nugetResolveDependencies = nugetResolveDependencies;

        this.mavenResolveDependencies = mavenResolveDependencies;
        this.mavenIgnoredScopes = mavenIgnoredScopes;
        this.mavenAggregateModules = mavenAggregateModules;

        this.dependenciesOnly = dependenciesOnly;
        this.whitesourceConfiguration = whitesourceConfiguration;
    }

    public ResolverConfiguration(Properties config) {
        npmRunPreStep                   = FSAConfiguration.getBooleanProperty(config, NPM_RUN_PRE_STEP, false);
        pythonResolveDependencies       = FSAConfiguration.getBooleanProperty(config, PYTHON_RESOLVE_DEPENDENCIES, true);
        npmResolveDependencies          = FSAConfiguration.getBooleanProperty(config, NPM_RESOLVE_DEPENDENCIES, true);
        npmIncludeDevDependencies       = FSAConfiguration.getBooleanProperty(config, NPM_INCLUDE_DEV_DEPENDENCIES, false);
        npmIgnoreJavaScriptFiles        = FSAConfiguration.getBooleanProperty(config, NPM_IGNORE_JAVA_SCRIPT_FILES, true);
        npmTimeoutDependenciesCollector = FSAConfiguration.getLongProperty(config, NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS, 60);
        npmAccessToken                  = config.getProperty(NPM_ACCESS_TOKEN);
        bowerResolveDependencies        = FSAConfiguration.getBooleanProperty(config, BOWER_RESOLVE_DEPENDENCIES, true);
        bowerRunPreStep                 = FSAConfiguration.getBooleanProperty(config, BOWER_RUN_PRE_STEP, false);
        nugetResolveDependencies        = FSAConfiguration.getBooleanProperty(config, NUGET_RESOLVE_DEPENDENCIES, true);
        mavenResolveDependencies        = FSAConfiguration.getBooleanProperty(config, MAVEN_RESOLVE_DEPENDENCIES, true);
        mavenIgnoredScopes              = FSAConfiguration.getListProperty(config, MAVEN_IGNORED_SCOPES, null);
        mavenAggregateModules           = FSAConfiguration.getBooleanProperty(config, MAVEN_AGGREGATE_MODULES, true);
        dependenciesOnly                = FSAConfiguration.getBooleanProperty(config, DEPENDENCIES_ONLY, false);
        whitesourceConfiguration        = config.getProperty(PROJECT_CONFIGURATION_PATH);
    }

    /* --- Members --- */

    private boolean npmRunPreStep;
    private boolean npmResolveDependencies;
    private boolean npmIncludeDevDependencies;
    private boolean npmIgnoreJavaScriptFiles;
    private String npmAccessToken;
    private long npmTimeoutDependenciesCollector;
    private boolean bowerResolveDependencies;
    private boolean bowerRunPreStep;
    private boolean nugetResolveDependencies;
    private boolean mavenResolveDependencies;
    private String[] mavenIgnoredScopes;
    private boolean mavenAggregateModules;
    private boolean dependenciesOnly;
    private String whitesourceConfiguration;
    private boolean pythonResolveDependencies;

    /* --- Public getters --- */

    @JsonProperty(NPM_RUN_PRE_STEP)
    public boolean isNpmRunPreStep() {
        return npmRunPreStep;
    }

    @JsonProperty(NPM_RESOLVE_DEPENDENCIES)
    public boolean isNpmResolveDependencies() {
        return npmResolveDependencies;
    }

    @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES)
    public boolean isNpmIncludeDevDependencies() {
        return npmIncludeDevDependencies;
    }

    @JsonProperty(NPM_IGNORE_JAVA_SCRIPT_FILES)
    public boolean isNpmIgnoreJavaScriptFiles() {
        return npmIgnoreJavaScriptFiles;
    }

    @JsonProperty(NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS)
    public long getNpmTimeoutDependenciesCollector() {
        return npmTimeoutDependenciesCollector;
    }

    @JsonProperty(NPM_ACCESS_TOKEN)
    public String getNpmAccessToken() { return npmAccessToken; }

    @JsonProperty(BOWER_RESOLVE_DEPENDENCIES)
    public boolean isBowerResolveDependencies() {
        return bowerResolveDependencies;
    }

    @JsonProperty(BOWER_RUN_PRE_STEP)
    public boolean isBowerRunPreStep() {
        return bowerRunPreStep;
    }

    @JsonProperty(NUGET_RESOLVE_DEPENDENCIES)
    public boolean isNugetResolveDependencies() {
        return nugetResolveDependencies;
    }

    @JsonProperty(MAVEN_RESOLVE_DEPENDENCIES)
    public boolean isMavenResolveDependencies() {
        return mavenResolveDependencies;
    }

    @JsonProperty(MAVEN_IGNORED_SCOPES)
    public String[] getMavenIgnoredScopes() {
        return mavenIgnoredScopes;
    }

    @JsonProperty(MAVEN_AGGREGATE_MODULES)
    public boolean isMavenAggregateModules() {
        return mavenAggregateModules;
    }

    @JsonProperty(DEPENDENCIES_ONLY)
    public boolean isDependenciesOnly() {
        return dependenciesOnly;
    }

    @JsonProperty(WHITESOURCE_CONFIGURATION)
    public String getWhitesourceConfiguration() {
        return whitesourceConfiguration;
    }

    @JsonProperty(PYTHON_RESOLVE_DEPENDENCIES)
    public boolean isPythonResolveDependencies() {
        return pythonResolveDependencies;
    }
}
