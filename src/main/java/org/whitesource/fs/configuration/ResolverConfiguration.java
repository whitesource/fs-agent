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
import static org.whitesource.agent.ConfigPropertyKeys.*;

public class ResolverConfiguration {
    private final boolean pythonIsWssPluginInstalled;

    /* --- Constructors --- */

    @JsonCreator
    public ResolverConfiguration(
            @JsonProperty(NPM_RUN_PRE_STEP) boolean npmRunPreStep,
            @JsonProperty(NPM_RESOLVE_DEPENDENCIES) boolean npmResolveDependencies,
            @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES) boolean npmIncludeDevDependencies,
            @JsonProperty(NPM_IGNORE_JAVA_SCRIPT_FILES) boolean npmIgnoreJavaScriptFiles,
            @JsonProperty(NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS) long npmTimeoutDependenciesCollector,
            @JsonProperty(NPM_ACCESS_TOKEN) String npmAccessToken,
            @JsonProperty(NPM_IGNORE_NPM_LS_ERRORS) boolean npmIgnoreNpmLsErrors,

            @JsonProperty(BOWER_RESOLVE_DEPENDENCIES) boolean bowerResolveDependencies,
            @JsonProperty(BOWER_RUN_PRE_STEP) boolean bowerRunPreStep,

            @JsonProperty(NUGET_RESOLVE_DEPENDENCIES) boolean nugetResolveDependencies,

            @JsonProperty(MAVEN_RESOLVE_DEPENDENCIES) boolean mavenResolveDependencies,
            @JsonProperty(MAVEN_IGNORED_SCOPES) String[] mavenIgnoredScopes,
            @JsonProperty(MAVEN_AGGREGATE_MODULES) boolean mavenAggregateModules,

            @JsonProperty(PYTHON_RESOLVE_DEPENDENCIES) boolean pythonResolveDependencies,
            @JsonProperty(PYTHON_PIP_PATH) String pipPath,
            @JsonProperty(PYTHON_PATH) String pythonPath,
            @JsonProperty(PYTHON_IS_WSS_PLUGIN_INSTALLED) boolean pythonIsWssPluginInstalled,

            @JsonProperty(DEPENDENCIES_ONLY) boolean dependenciesOnly,
            @JsonProperty(WHITESOURCE_CONFIGURATION) String whitesourceConfiguration
    ) {
        this.npmRunPreStep = npmRunPreStep;
        this.npmResolveDependencies = npmResolveDependencies;
        this.npmIncludeDevDependencies = npmIncludeDevDependencies;
        this.npmIgnoreJavaScriptFiles = npmIgnoreJavaScriptFiles;
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.npmAccessToken = npmAccessToken;
        this.npmIgnoreNpmLsErrors = npmIgnoreNpmLsErrors;

        this.bowerResolveDependencies = bowerResolveDependencies;
        this.bowerRunPreStep = bowerRunPreStep;

        this.nugetResolveDependencies = nugetResolveDependencies;

        this.mavenResolveDependencies = mavenResolveDependencies;
        this.mavenIgnoredScopes = mavenIgnoredScopes;
        this.mavenAggregateModules = mavenAggregateModules;

        this.pythonResolveDependencies = pythonResolveDependencies;
        this.pipPath = pipPath;
        this.pythonPath = pythonPath;
        this.pythonIsWssPluginInstalled = pythonIsWssPluginInstalled;

        this.dependenciesOnly = dependenciesOnly;
        this.whitesourceConfiguration = whitesourceConfiguration;
    }

    /* --- Members --- */

    private boolean npmRunPreStep;
    private boolean npmResolveDependencies;
    private boolean npmIncludeDevDependencies;
    private boolean npmIgnoreJavaScriptFiles;
    private String npmAccessToken;
    private long npmTimeoutDependenciesCollector;
    private boolean npmIgnoreNpmLsErrors;
    private boolean bowerResolveDependencies;
    private boolean bowerRunPreStep;
    private boolean nugetResolveDependencies;
    private boolean mavenResolveDependencies;
    private String[] mavenIgnoredScopes;
    private boolean mavenAggregateModules;
    private boolean dependenciesOnly;
    private String whitesourceConfiguration;
    private boolean pythonResolveDependencies;
    private String pipPath;
    private String pythonPath;

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
    public String getNpmAccessToken() {
        return npmAccessToken;
    }

    @JsonProperty(NPM_IGNORE_NPM_LS_ERRORS)
    public boolean getNpmIgnoreNpmLsErrors() {
        return npmIgnoreNpmLsErrors;
    }

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

    @JsonProperty(PYTHON_PIP_PATH)
    public String getPipPath() {
        return pipPath;
    }

    @JsonProperty(PYTHON_PATH)
    public String getPythonPath() {
        return pythonPath;
    }

    @JsonProperty(PYTHON_IS_WSS_PLUGIN_INSTALLED)
    public boolean isPythonIsWssPluginInstalled() {
        return pythonIsWssPluginInstalled;
    }
}