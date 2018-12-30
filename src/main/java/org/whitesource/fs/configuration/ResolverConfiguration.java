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
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.go.GoDependencyManager;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.fs.FSAConfigProperty;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class ResolverConfiguration {


    /* --- Constructors --- */

    @JsonCreator
    public ResolverConfiguration(
            @JsonProperty(NPM_RUN_PRE_STEP) boolean npmRunPreStep,
            @JsonProperty(NPM_RESOLVE_DEPENDENCIES) boolean npmResolveDependencies,
            @JsonProperty(NPM_IGNORE_SCRIPTS) boolean npmIgnoreScripts,
            @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES) boolean npmIncludeDevDependencies,
            @JsonProperty(NPM_IGNORE_SOURCE_FILES) boolean npmIgnoreSourceFiles,
            @JsonProperty(NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS) long npmTimeoutDependenciesCollector,
            @JsonProperty(NPM_ACCESS_TOKEN) String npmAccessToken,
            @JsonProperty(NPM_IGNORE_NPM_LS_ERRORS) boolean npmIgnoreNpmLsErrors,
            @JsonProperty(NPM_YARN_PROJECT) boolean npmYarnProject,

            @JsonProperty(BOWER_RESOLVE_DEPENDENCIES) boolean bowerResolveDependencies,
            @JsonProperty(BOWER_RUN_PRE_STEP) boolean bowerRunPreStep,
            @JsonProperty(BOWER_IGNORE_SOURCE_FILES) boolean bowerIgnoreSourceFiles,

            @JsonProperty(NUGET_RESOLVE_DEPENDENCIES) boolean nugetResolveDependencies,
            @JsonProperty(NUGET_RESTORE_DEPENDENCIES) boolean nugetRestoreDependencies,
            @JsonProperty(NUGET_RUN_PRE_STEP) boolean nugetRunPreStep,
            @JsonProperty(NUGET_IGNORE_SOURCE_FILES) boolean nugetIgnoreSourceFiles,
            @JsonProperty(NUGET_RESOLVE_PACKAGES_CONFIG_FILES) boolean nugetResolvePackagesConfigFiles,
            @JsonProperty(NUGET_RESOLVE_CS_PROJ_FILES) boolean nugetResolveCsProjFiles,

            @JsonProperty(MAVEN_RESOLVE_DEPENDENCIES) boolean mavenResolveDependencies,
            @JsonProperty(MAVEN_IGNORED_SCOPES) String[] mavenIgnoredScopes,
            @JsonProperty(MAVEN_AGGREGATE_MODULES) boolean mavenAggregateModules,
            @JsonProperty(MAVEN_IGNORE_POM_MODULES) boolean mavenIgnorePomModules,
            @JsonProperty(MAVEN_IGNORE_SOURCE_FILES) boolean mavenIgnoreSourceFiles,
            @JsonProperty(MAVEN_RUN_PRE_STEP) boolean mavenRunPreStep,
            @JsonProperty(MAVEN_IGNORE_DEPENDENCY_TREE_ERRORS) boolean mavenIgnoreDependencyTreeErrors,

            @JsonProperty(PYTHON_RESOLVE_DEPENDENCIES) boolean pythonResolveDependencies,
            @JsonProperty(PYTHON_PIP_PATH) String pipPath,
            @JsonProperty(PYTHON_PATH) String pythonPath,
            @JsonProperty(PYTHON_IS_WSS_PLUGIN_INSTALLED) boolean pythonIsWssPluginInstalled,
            @JsonProperty(PYTHON_UNINSTALL_WSS_PLUGIN) boolean pythonUninstallWssPlugin,
            @JsonProperty(PYTHON_IGNORE_PIP_INSTALL_ERRORS) boolean pythonIgnorePipInstallErrors,
            @JsonProperty(PYTHON_INSTALL_VIRTUALENV) boolean pythonInstallVirtualenv,
            @JsonProperty(PYTHON_RESOLVE_HIERARCHY_TREE) boolean pythonResolveHierarchyTree,
            @JsonProperty(PYTHON_REQUIREMENTS_FILE_INCLUDES) String[] pythonRequirementsFileIncludes,
            @JsonProperty(PYTHON_RESOLVE_SETUP_PY_FILES) boolean pythonResolveSetupPyFiles,
            @JsonProperty(PYTHON_IGNORE_SOURCE_FILES) boolean pythonIgnoreSourceFiles,
            @JsonProperty(PYTHON_IGNORE_PIPENV_INSTALL_ERRORS) boolean ignorePipEnvInstallErrors,
            @JsonProperty(PYTHON_RUN_PIPENV_PRE_STEP) boolean runPipenvPreStep,
            @JsonProperty(PYTHON_PIPENV_DEV_DEPENDENCIES) boolean pipenvInstallDevDependencies,
            @JsonProperty(IGNORE_SOURCE_FILES) boolean ignoreSourceFiles,
            //            @JsonProperty(DEPENDENCIES_ONLY) boolean dependenciesOnly,
            @JsonProperty(WHITESOURCE_CONFIGURATION) String whitesourceConfiguration,

            @JsonProperty(GRADLE_RESOLVE_DEPENDENCIES) boolean gradleResolveDependencies,
            @JsonProperty(GRADLE_RUN_ASSEMBLE_COMMAND) boolean gradleRunAssembleCommand,
            @JsonProperty(GRADLE_AGGREGATE_MODULES) boolean gradleAggregateModules,
            @JsonProperty(GRADLE_PREFERRED_ENVIRONMENT) String gradlePreferredEnvironment,
            @JsonProperty(GRADLE_IGNORE_SOURCE_FILES) boolean gradleIgnoreSourceFiles,
            @JsonProperty(GRADLE_RUN_PRE_STEP) boolean gradleRunPreStep,
            @JsonProperty(GRADLE_IGNORE_SCOPES) String[] gradleIgnoredScopes,
            @JsonProperty(GRADLE_LOCAL_REPOSITORY_PATH) String gradleLocalRepositoryPath,

            @JsonProperty(PAKET_RESOLVE_DEPENDENCIES) boolean paketResolveDependencies,
            @JsonProperty(PAKET_IGNORED_GROUPS) String[] paketIgnoredScopes,
            @JsonProperty(PAKET_RUN_PRE_STEP) boolean paketRunPreStep,
            @JsonProperty(PAKET_EXE_PATH) String paketPath,
            @JsonProperty(PAKET_IGNORE_SOURCE_FILES) boolean paketIgnoreSourceFiles,

            @JsonProperty(GO_RESOLVE_DEPENDENCIES) boolean goResolveDependencies,
            @JsonProperty(GO_DEPENDENCY_MANAGER) String goDependencyManager,
            @JsonProperty(GO_COLLECT_DEPENDENCIES_AT_RUNTIME) boolean goCollectDependenciesAtRuntime,
            @JsonProperty(GO_GLIDE_IGNORE_TEST_PACKAGES) boolean goIgnoreTestPackages,
            @JsonProperty(GO_IGNORE_SOURCE_FILES) boolean goIgnoreSourceFiles,
            @JsonProperty(GO_GRADLE_ENABLE_TASK_ALIAS) boolean goGradleEnableTaskAlias,

            @JsonProperty(RUBY_RESOLVE_DEPENDENCIES) boolean rubyResolveDependencies,
            @JsonProperty(RUBY_RUN_BUNDLE_INSTALL) boolean rubyRunBundleInstall,
            @JsonProperty(RUBY_OVERWRITE_GEM_FILE) boolean rubyOverwriteGemFile,
            @JsonProperty(RUBY_INSTALL_MISSING_GEMS) boolean rubyInstallMissingGems,
            @JsonProperty(RUBY_IGNORE_SOURCE_FILES) boolean rubyIgnoreSourceFiles,

            @JsonProperty(PHP_RESOLVE_DEPENDENCIES) boolean phpResolveDependencies,
            @JsonProperty(PHP_RUN_PRE_STEP) boolean phpRunPreStep,
            @JsonProperty(PHP_INCLUDE_DEV_DEPENDENCIES) boolean phpIncludeDevDependencies,

            @JsonProperty(SBT_RESOLVE_DEPENDENCIES) boolean sbtResolveDependencies,
            @JsonProperty(SBT_AGGREGATE_MODULES) boolean sbtAggregateModules,
            @JsonProperty(SBT_RUN_PRE_STEP) boolean sbtRunPreStep,
            @JsonProperty(SBT_TARGET_FOLDER) String sbtTargetFolder,
            @JsonProperty(SBT_IGNORE_SOURCE_FILES) boolean sbtIgnoreSourceFiles,

            @JsonProperty(HTML_RESOLVE_DEPENDENCIES) boolean htmlResolveDependencies,
            @JsonProperty(COCOAPODS_RESOLVE_DEPENDENCIES) boolean cocoapodsResolveDependencies,
            @JsonProperty(COCOAPODS_RUN_PRE_STEP) boolean cocoapodsRunPreStep,
            @JsonProperty(COCOAPODS_IGNORE_SOURCE_FILES) boolean cocoapodsIgnoreSourceFiles,
            @JsonProperty(HEX_RESOLVE_DEPENDENECIES) boolean hexResolveDependencies,
            @JsonProperty(HEX_RUN_PRE_STEP) boolean hexRunPreStep,
            @JsonProperty(HEX_IGNORE_SOURCE_FILES) boolean hexIgnoreSourceFiles,
            @JsonProperty(HEX_AGGREGATE_MODULES) boolean hexAggregateModules,
            @JsonProperty("addSha1") boolean addSha1) {
        this.npmRunPreStep = npmRunPreStep;
        this.npmIgnoreScripts = npmIgnoreScripts;
        this.npmResolveDependencies = npmResolveDependencies;
        this.npmIncludeDevDependencies = npmIncludeDevDependencies;
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.npmAccessToken = npmAccessToken;
        this.npmIgnoreNpmLsErrors = npmIgnoreNpmLsErrors;
        this.npmYarnProject = npmYarnProject;
        this.npmIgnoreSourceFiles = npmIgnoreSourceFiles;

        this.bowerResolveDependencies = bowerResolveDependencies;
        this.bowerRunPreStep = bowerRunPreStep;
        this.bowerIgnoreSourceFiles = bowerIgnoreSourceFiles;

        this.nugetResolveDependencies = nugetResolveDependencies;
        this.nugetRestoreDependencies = nugetRestoreDependencies;
        this.nugetRunPreStep = nugetRunPreStep;
        this.nugetIgnoreSourceFiles = nugetIgnoreSourceFiles;
        this.nugetResolveCsProjFiles = nugetResolveCsProjFiles;
        this.nugetResolvePackagesConfigFiles = nugetResolvePackagesConfigFiles;

        this.mavenResolveDependencies = mavenResolveDependencies;
        this.mavenIgnoredScopes = mavenIgnoredScopes;
        this.mavenAggregateModules = mavenAggregateModules;
        this.mavenIgnorePomModules = mavenIgnorePomModules;
        this.mavenIgnoreSourceFiles = mavenIgnoreSourceFiles;
        this.mavenRunPreStep = mavenRunPreStep;
        this.mavenIgnoreDependencyTreeErrors = mavenIgnoreDependencyTreeErrors;

        this.pythonResolveDependencies = pythonResolveDependencies;
        this.pipPath = pipPath;
        this.pythonPath = pythonPath;
        this.pythonIsWssPluginInstalled = pythonIsWssPluginInstalled;
        this.pythonUninstallWssPlugin = pythonUninstallWssPlugin;
        this.pythonIgnorePipInstallErrors = pythonIgnorePipInstallErrors;
        this.pythonInstallVirtualenv = pythonInstallVirtualenv;
        this.pythonResolveHierarchyTree = pythonResolveHierarchyTree;
        this.pythonRequirementsFileIncludes = pythonRequirementsFileIncludes;
        this.pythonResolveSetupPyFiles = pythonResolveSetupPyFiles;
        this.pythonIgnoreSourceFiles = pythonIgnoreSourceFiles;
        this.ignorePipEnvInstallErrors = ignorePipEnvInstallErrors;
        this.runPipenvPreStep = runPipenvPreStep;
        this.pipenvInstallDevDependencies = pipenvInstallDevDependencies;
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.whitesourceConfiguration = whitesourceConfiguration;

        this.gradleResolveDependencies = gradleResolveDependencies;
        this.gradleAggregateModules = gradleAggregateModules;
        this.gradleRunAssembleCommand = gradleRunAssembleCommand;
        this.gradlePreferredEnvironment = gradlePreferredEnvironment;
        this.gradleIgnoreSourceFiles = gradleIgnoreSourceFiles;
        this.gradleRunPreStep = gradleRunPreStep;
        this.gradleIgnoredScopes = gradleIgnoredScopes;
        this.gradleLocalRepositoryPath = gradleLocalRepositoryPath;

        this.paketResolveDependencies = paketResolveDependencies;
        this.paketIgnoredScopes = paketIgnoredScopes;
        this.paketRunPreStep = paketRunPreStep;
        this.paketPath = paketPath;
        this.paketIgnoreSourceFiles = paketIgnoreSourceFiles;

        this.goResolveDependencies = goResolveDependencies;
        if (goDependencyManager != null && !goDependencyManager.isEmpty()) {
            this.goDependencyManager = GoDependencyManager.getFromType(goDependencyManager);
        }
        this.goCollectDependenciesAtRuntime = goCollectDependenciesAtRuntime;
        this.goGlideIgnoreTestPackages = goIgnoreTestPackages;
        this.goGlideIgnoreSourceFiles = goIgnoreSourceFiles;
        this.goGradleEnableTaskAlias = goGradleEnableTaskAlias;

        this.rubyResolveDependencies = rubyResolveDependencies;
        this.rubyRunBundleInstall = rubyRunBundleInstall;
        this.rubyOverwriteGemFile = rubyOverwriteGemFile;
        this.rubyInstallMissingGems = rubyInstallMissingGems;
        this.rubyIgnoreSourceFiles = rubyIgnoreSourceFiles;

        this.phpResolveDependencies = phpResolveDependencies;
        this.phpRunPreStep = phpRunPreStep;
        this.phpIncludeDevDependencies = phpIncludeDevDependencies;

        this.sbtResolveDependencies = sbtResolveDependencies;
        this.sbtAggregateModules = sbtAggregateModules;
        this.sbtRunPreStep = sbtRunPreStep;
        this.sbtTargetFolder = sbtTargetFolder;
        this.sbtIgnoreSourceFiles = sbtIgnoreSourceFiles;

        this.htmlResolveDependencies = htmlResolveDependencies;

        this.cocoapodsResolveDependencies = cocoapodsResolveDependencies;
        this.cocoapodsRunPreStep = cocoapodsRunPreStep;
        this.cocoapodsIgnoreSourceFiles = cocoapodsIgnoreSourceFiles;

        this.hexResolveDependencies = hexResolveDependencies;
        this.hexRunPreStep = hexRunPreStep;
        this.hexIgnoreSourceFiles = hexIgnoreSourceFiles;
        this.hexAggregateModules = hexAggregateModules;

        this.addSha1 = addSha1;
    }

    /* --- Members --- */

    private static Logger logger;
    private String whitesourceConfiguration;

    @FSAConfigProperty
    private boolean ignoreSourceFiles;

    @FSAConfigProperty
    private boolean npmRunPreStep;
    @FSAConfigProperty
    private boolean npmIgnoreScripts;
    @FSAConfigProperty
    private boolean npmResolveDependencies;
    @FSAConfigProperty
    private boolean npmIncludeDevDependencies;
    @FSAConfigProperty
    private long npmTimeoutDependenciesCollector;
    @FSAConfigProperty
    private boolean npmIgnoreNpmLsErrors;
    @FSAConfigProperty
    private boolean npmYarnProject;
    @FSAConfigProperty
    private boolean npmIgnoreSourceFiles;
    private String npmAccessToken;

    @FSAConfigProperty
    private boolean bowerResolveDependencies;
    @FSAConfigProperty
    private boolean bowerRunPreStep;
    @FSAConfigProperty
    private boolean bowerIgnoreSourceFiles;

    @FSAConfigProperty
    private boolean nugetResolveDependencies;
    @FSAConfigProperty
    private boolean nugetRestoreDependencies;
    @FSAConfigProperty
    private boolean nugetRunPreStep;
    @FSAConfigProperty
    private boolean nugetIgnoreSourceFiles;
    @FSAConfigProperty
    private boolean nugetResolvePackagesConfigFiles;
    @FSAConfigProperty
    private boolean nugetResolveCsProjFiles;

    @FSAConfigProperty
    private boolean mavenResolveDependencies;
    @FSAConfigProperty
    private String[] mavenIgnoredScopes;
    @FSAConfigProperty
    private boolean mavenAggregateModules;
    @FSAConfigProperty
    private boolean mavenIgnorePomModules;
    @FSAConfigProperty
    private boolean mavenIgnoreSourceFiles;
    @FSAConfigProperty
    private boolean mavenRunPreStep;
    @FSAConfigProperty
    private boolean mavenIgnoreDependencyTreeErrors;

    @FSAConfigProperty
    private boolean pythonResolveDependencies;
    @FSAConfigProperty
    private String pipPath;
    @FSAConfigProperty
    private String pythonPath;
    @FSAConfigProperty
    private boolean pythonIgnorePipInstallErrors;
    @FSAConfigProperty
    private boolean pythonInstallVirtualenv;
    @FSAConfigProperty
    private boolean pythonResolveHierarchyTree;
    @FSAConfigProperty
    private String[] pythonRequirementsFileIncludes;
    @FSAConfigProperty
    private boolean pythonResolveSetupPyFiles;
    @FSAConfigProperty
    private boolean pythonIgnoreSourceFiles;
    @FSAConfigProperty
    private boolean ignorePipEnvInstallErrors;
    @FSAConfigProperty
    private boolean pipenvInstallDevDependencies;
    @FSAConfigProperty
    private boolean runPipenvPreStep;
    @FSAConfigProperty
    private final boolean pythonIsWssPluginInstalled;
    @FSAConfigProperty
    private final boolean pythonUninstallWssPlugin;

    @FSAConfigProperty
    private boolean gradleResolveDependencies;
    @FSAConfigProperty
    private boolean gradleRunAssembleCommand;
    @FSAConfigProperty
    private boolean gradleAggregateModules;
    @FSAConfigProperty
    private String gradlePreferredEnvironment;
    @FSAConfigProperty
    private String gradleLocalRepositoryPath;
    @FSAConfigProperty
    private boolean gradleIgnoreSourceFiles;
    @FSAConfigProperty
    private boolean gradleRunPreStep;
    @FSAConfigProperty
    private String[] gradleIgnoredScopes;

    @FSAConfigProperty
    private boolean paketResolveDependencies;
    @FSAConfigProperty
    private String[] paketIgnoredScopes;
    @FSAConfigProperty
    private boolean paketRunPreStep;
    @FSAConfigProperty
    private String paketPath;
    @FSAConfigProperty
    private boolean paketIgnoreSourceFiles;

    @FSAConfigProperty
    private boolean goResolveDependencies;
    @FSAConfigProperty
    private GoDependencyManager goDependencyManager;
    @FSAConfigProperty
    private boolean goCollectDependenciesAtRuntime;
    @FSAConfigProperty
    private boolean goGlideIgnoreTestPackages;
    @FSAConfigProperty
    private boolean goGlideIgnoreSourceFiles;
    @FSAConfigProperty
    private boolean goGradleEnableTaskAlias;

    @FSAConfigProperty
    private boolean rubyResolveDependencies;
    @FSAConfigProperty
    private boolean rubyRunBundleInstall;
    @FSAConfigProperty
    private boolean rubyOverwriteGemFile;
    @FSAConfigProperty
    private boolean rubyInstallMissingGems;
    @FSAConfigProperty
    private boolean rubyIgnoreSourceFiles;

    @FSAConfigProperty
    private boolean phpResolveDependencies;
    @FSAConfigProperty
    private boolean phpRunPreStep;
    @FSAConfigProperty
    private boolean phpIncludeDevDependencies;

    @FSAConfigProperty
    private boolean sbtResolveDependencies;
    @FSAConfigProperty
    private boolean sbtAggregateModules;
    @FSAConfigProperty
    private boolean sbtRunPreStep;
    @FSAConfigProperty
    private String sbtTargetFolder;
    @FSAConfigProperty
    private boolean sbtIgnoreSourceFiles;

    @FSAConfigProperty
    private boolean htmlResolveDependencies;

    @FSAConfigProperty
    private boolean cocoapodsResolveDependencies;
    @FSAConfigProperty
    private boolean cocoapodsRunPreStep;
    @FSAConfigProperty
    private boolean cocoapodsIgnoreSourceFiles;

    @FSAConfigProperty
    private boolean hexResolveDependencies;
    @FSAConfigProperty
    private boolean hexRunPreStep;
    @FSAConfigProperty
    private boolean hexAggregateModules;
    @FSAConfigProperty
    private boolean hexIgnoreSourceFiles;

    private boolean addSha1;

    /* --- Public getters --- */

    @JsonProperty(NPM_RUN_PRE_STEP)
    public boolean isNpmRunPreStep() {
        return npmRunPreStep;
    }

    @JsonProperty(NPM_IGNORE_SCRIPTS)
    public boolean isNpmIgnoreScripts() {
        return npmIgnoreScripts;
    }

    @JsonProperty(NPM_RESOLVE_DEPENDENCIES)
    public boolean isNpmResolveDependencies() {
        return npmResolveDependencies;
    }

    @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES)
    public boolean isNpmIncludeDevDependencies() {
        return npmIncludeDevDependencies;
    }

    @JsonProperty(NPM_IGNORE_SOURCE_FILES)
    public boolean isNpmIgnoreSourceFiles() {
        return npmIgnoreSourceFiles;
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

    @JsonProperty(NPM_YARN_PROJECT)
    public boolean getNpmYarnProject() {
        return npmYarnProject;
    }

    @JsonProperty(BOWER_RESOLVE_DEPENDENCIES)
    public boolean isBowerResolveDependencies() {
        return bowerResolveDependencies;
    }

    @JsonProperty(BOWER_RUN_PRE_STEP)
    public boolean isBowerRunPreStep() {
        return bowerRunPreStep;
    }

    @JsonProperty(BOWER_IGNORE_SOURCE_FILES)
    public boolean isBowerIgnoreSourceFiles() {
        return bowerIgnoreSourceFiles;
    }

    @JsonProperty(NUGET_RESOLVE_DEPENDENCIES)
    public boolean isNugetResolveDependencies() {
        return nugetResolveDependencies;
    }

    @JsonProperty(NUGET_RESTORE_DEPENDENCIES)
    public boolean isNugetRestoreDependencies() {
        return nugetRestoreDependencies;
    }

    @JsonProperty(NUGET_RUN_PRE_STEP)
    public boolean isNugetRunPreStep() {
        return nugetRunPreStep;
    }

    @JsonProperty(NUGET_RESOLVE_CS_PROJ_FILES)
    public boolean isNugetResolveCsProjFiles() {
        return nugetResolveCsProjFiles;
    }

    @JsonProperty(NUGET_RESOLVE_PACKAGES_CONFIG_FILES)
    public boolean isNugetResolvePackagesConfigFiles() {
        return nugetResolvePackagesConfigFiles;
    }

    @JsonProperty(NUGET_IGNORE_SOURCE_FILES)
    public boolean isNugetIgnoreSourceFiles() {
        return nugetIgnoreSourceFiles;
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

    @JsonProperty(MAVEN_IGNORE_POM_MODULES)
    public boolean isMavenIgnorePomModules() {
        return mavenIgnorePomModules;
    }

    @JsonProperty(MAVEN_IGNORE_SOURCE_FILES)
    public boolean isMavenIgnoreSourceFiles() {
        return mavenIgnoreSourceFiles;
    }

    @JsonProperty(MAVEN_RUN_PRE_STEP)
    public boolean isMavenRunPreStep() {
        return mavenRunPreStep;
    }

    @JsonProperty(MAVEN_IGNORE_DEPENDENCY_TREE_ERRORS)
    public boolean isMavenIgnoreDependencyTreeErrors() {
        return mavenIgnoreDependencyTreeErrors;
    }

    @JsonProperty(IGNORE_SOURCE_FILES)
    public boolean isIgnoreSourceFiles() {
        return ignoreSourceFiles;
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

    @JsonProperty(PYTHON_IGNORE_PIP_INSTALL_ERRORS)
    public boolean isPythonIgnorePipInstallErrors() {
        return pythonIgnorePipInstallErrors;
    }

    @JsonProperty(PYTHON_IS_WSS_PLUGIN_INSTALLED)
    public boolean isPythonIsWssPluginInstalled() {
        return pythonIsWssPluginInstalled;
    }

    @JsonProperty(PYTHON_UNINSTALL_WSS_PLUGIN)
    public boolean getPythonUninstallWssPlugin() {
        return pythonUninstallWssPlugin;
    }

    @JsonProperty(PYTHON_INSTALL_VIRTUALENV)
    public boolean isPythonInstallVirtualenv() {
        return pythonInstallVirtualenv;
    }

    @JsonProperty(PYTHON_RESOLVE_HIERARCHY_TREE)
    public boolean isPythonResolveHierarchyTree() {
        return pythonResolveHierarchyTree;
    }

    @JsonProperty(PYTHON_RESOLVE_SETUP_PY_FILES)
    public boolean isPythonResolveSetupPyFiles() {
        return this.pythonResolveSetupPyFiles;
    }

    public String[] getPythonRequirementsFileIncludes() {
        return pythonRequirementsFileIncludes;
    }

    @JsonProperty(PYTHON_IGNORE_SOURCE_FILES)
    public boolean isPythonIgnoreSourceFiles() {
        return pythonIgnoreSourceFiles;
    }

    @JsonProperty(PYTHON_RUN_PIPENV_PRE_STEP)
    public boolean IsRunPipenvPreStep() {
        return this.runPipenvPreStep;
    }

    @JsonProperty(PYTHON_IGNORE_PIPENV_INSTALL_ERRORS)
    public boolean isIgnorePipEnvInstallErrors() {
        return this.ignorePipEnvInstallErrors;
    }

    @JsonProperty(PYTHON_PIPENV_DEV_DEPENDENCIES)
    public boolean isPipenvInstallDevDependencies() {
        return pipenvInstallDevDependencies;
    }

    @JsonProperty(GRADLE_RESOLVE_DEPENDENCIES)
    public boolean isGradleResolveDependencies() {
        return gradleResolveDependencies;
    }

    @JsonProperty(GRADLE_AGGREGATE_MODULES)
    public boolean isGradleAggregateModules() {
        return gradleAggregateModules;
    }

    @JsonProperty(GRADLE_RUN_ASSEMBLE_COMMAND)
    public boolean isGradleRunAssembleCommand() {
        return gradleRunAssembleCommand;
    }

    @JsonProperty(GRADLE_PREFERRED_ENVIRONMENT)
    public String getGradlePreferredEnvironment() {
        return gradlePreferredEnvironment;
    }

    @JsonProperty(GRADLE_IGNORE_SOURCE_FILES)
    public boolean isGradleIgnoreSourceFiles() {
        return gradleIgnoreSourceFiles;
    }

    @JsonProperty(GRADLE_RUN_PRE_STEP)
    public boolean isGradleRunPreStep() {
        return gradleRunPreStep;
    }

    @JsonProperty(GRADLE_LOCAL_REPOSITORY_PATH)
    public String getGradleLocalRepositoryPath() {
        return gradleLocalRepositoryPath;
    }

    @JsonProperty(PAKET_RESOLVE_DEPENDENCIES)
    public boolean isPaketResolveDependencies() {
        return paketResolveDependencies;
    }

    @JsonProperty(PAKET_IGNORED_GROUPS)
    public String[] getPaketIgnoredScopes() {
        return paketIgnoredScopes;
    }

    @JsonProperty(PAKET_RUN_PRE_STEP)
    public boolean isPaketRunPreStep() {
        return paketRunPreStep;
    }

    @JsonProperty(PAKET_EXE_PATH)
    public String getPaketPath() {
        return paketPath;
    }

    @JsonProperty(PAKET_IGNORE_SOURCE_FILES)
    public boolean isPaketIgnoreSourceFiles() {
        return paketIgnoreSourceFiles;
    }

    @JsonProperty(GO_RESOLVE_DEPENDENCIES)
    public boolean isGoResolveDependencies() {
        return goResolveDependencies;
    }

    @JsonProperty(GO_DEPENDENCY_MANAGER)
    public GoDependencyManager getGoDependencyManager() {
        return goDependencyManager;
    }

    @JsonProperty(GO_COLLECT_DEPENDENCIES_AT_RUNTIME)
    public boolean isGoCollectDependenciesAtRuntime() {
        return goCollectDependenciesAtRuntime;
    }

    @JsonProperty(GO_GLIDE_IGNORE_TEST_PACKAGES)
    public boolean isGoGlideIgnoreTestPackages() {
        return goGlideIgnoreTestPackages;
    }

    @JsonProperty(GO_IGNORE_SOURCE_FILES)
    public boolean isGoGlideIgnoreSourceFiles() {
        return goGlideIgnoreSourceFiles;
    }

    @JsonProperty(GO_GRADLE_ENABLE_TASK_ALIAS)
    public boolean isGoGradleEnableTaskAlias() {
        return goGradleEnableTaskAlias;
    }

    @JsonProperty(RUBY_RESOLVE_DEPENDENCIES)
    public boolean isRubyResolveDependencies() {
        return rubyResolveDependencies;
    }

    @JsonProperty(RUBY_RUN_BUNDLE_INSTALL)
    public boolean isRubyRunBundleInstall() {
        return rubyRunBundleInstall;
    }

    @JsonProperty(RUBY_OVERWRITE_GEM_FILE)
    public boolean isRubyOverwriteGemFile() {
        return rubyOverwriteGemFile;
    }

    @JsonProperty(RUBY_INSTALL_MISSING_GEMS)
    public boolean isRubyInstallMissingGems() {
        return rubyInstallMissingGems;
    }

    @JsonProperty(RUBY_IGNORE_SOURCE_FILES)
    public boolean isRubyIgnoreSourceFiles() {
        return rubyIgnoreSourceFiles;
    }

    @JsonProperty(PHP_RESOLVE_DEPENDENCIES)
    public boolean isPhpResolveDependencies() {
        return phpResolveDependencies;
    }

    @JsonProperty(PHP_RUN_PRE_STEP)
    public boolean isPhpRunPreStep() {
        return phpRunPreStep;
    }

    @JsonProperty(PHP_INCLUDE_DEV_DEPENDENCIES)
    public boolean isPhpIncludeDevDependencies() {
        return phpIncludeDevDependencies;
    }

    @JsonProperty(SBT_RESOLVE_DEPENDENCIES)
    public boolean isSbtResolveDependencies() {
        return sbtResolveDependencies;
    }

    @JsonProperty(SBT_AGGREGATE_MODULES)
    public boolean isSbtAggregateModules() {
        return sbtAggregateModules;
    }

    @JsonProperty(SBT_RUN_PRE_STEP)
    public boolean isSbtRunPreStep() {
        return sbtRunPreStep;
    }

    @JsonProperty(SBT_TARGET_FOLDER)
    public String getSbtTargetFolder() {
        return sbtTargetFolder;
    }

    @JsonProperty(SBT_IGNORE_SOURCE_FILES)
    public boolean isSbtIgnoreSourceFiles() {
        return sbtIgnoreSourceFiles;
    }

    @JsonProperty(HTML_RESOLVE_DEPENDENCIES)
    public boolean isHtmlResolveDependencies() {
        return htmlResolveDependencies;
    }

    @JsonProperty(COCOAPODS_RESOLVE_DEPENDENCIES)
    public boolean isCocoapodsResolveDependencies() {
        return cocoapodsResolveDependencies;
    }

    @JsonProperty(COCOAPODS_RUN_PRE_STEP)
    public boolean isCocoapodsRunPreStep() {
        return cocoapodsRunPreStep;
    }

    @JsonProperty(COCOAPODS_IGNORE_SOURCE_FILES)
    public boolean isCocoapodsIgnoreSourceFiles() {
        return cocoapodsIgnoreSourceFiles;
    }

    @JsonProperty(HEX_RESOLVE_DEPENDENECIES)
    public boolean isHexResolveDependencies() {
        return hexResolveDependencies;
    }

    @JsonProperty(HEX_IGNORE_SOURCE_FILES)
    public boolean isHexIgnoreSourceFiles() {
        return hexIgnoreSourceFiles;
    }

    @JsonProperty(HEX_RUN_PRE_STEP)
    public boolean isHexRunPreStep() {
        return hexRunPreStep;
    }

    @JsonProperty(HEX_AGGREGATE_MODULES)
    public boolean isHexAggregateModules() {
        return hexAggregateModules;
    }

    public String[] getGradleIgnoredScopes() {
        return this.gradleIgnoredScopes;
    }

    public boolean isAddSha1() {
        return addSha1;
    }

    @Override
    public String toString() {
        logger = LoggerFactory.getLogger(ResolverConfiguration.class);
        StringBuilder result = new StringBuilder();
        Field[] fields = this.getClass().getDeclaredFields();
        String newResolver;
        String currentResolver = null;
        List<String> resolversList = Arrays.asList("npm", "bower", "nuget", "maven", "python", "gradle", "paket", "go", "ruby", "php", "sbt", "html", "cocoapods", "hex");
        for (Field field : fields) {
            if (field.isAnnotationPresent(FSAConfigProperty.class)) {
                try {
                    field.setAccessible(true);
                    String name = field.getName();
                    Object value = field.get(this);
                    Class fieldType = field.getType();

                    // Following block is to manage toString method to print each resolver parameters in One Line
                    // if field name contains pip it means that currentResolver is Python and this field should be in Python
                    if (!name.toLowerCase().contains("pip")) {
                        String regex = "([a-z]+)";
                        Pattern p = Pattern.compile(regex);
                        Matcher m = p.matcher(name);
                        if (m.find()) {
                            newResolver = m.group();
                            if (resolversList.contains(newResolver.toLowerCase())) {
                                if (currentResolver == null || !currentResolver.equals(newResolver)) {
                                    if (currentResolver != null) {
                                        result.append(Constants.CLOSE_CURLY_BRACKET);
                                        result.append(Constants.NEW_LINE);
                                    }
                                    result.append(newResolver);
                                    result.append(": {");
                                    currentResolver = newResolver;
                                }
                            } else {
                                // This block for parameters that are not belong to each resolver
                                result.append(field.getName() + Constants.EQUALS + value + Constants.NEW_LINE);
                                continue;
                            }
                        }
                    }
                    if (value == null) {
                        result.append(field.getName() + Constants.EQUALS + Constants.EMPTY_STRING + Constants.COMMA + Constants.WHITESPACE);
                    } else {
                        if (fieldType.isArray()) {
                            result.append(field.getName() + Constants.EQUALS + Arrays.toString((Object[]) value) + Constants.COMMA + Constants.WHITESPACE);
                        } else {
                            result.append(field.getName() + Constants.EQUALS + value + Constants.COMMA + Constants.WHITESPACE);
                        }
                    }
                } catch (IllegalAccessException e) {
                    logger.debug("Failed in Resolvers Configuration parsing toString - {}. Exception: {}", e.getMessage(), e.getStackTrace());
                }
            }
        }
        result.append("}" + Constants.NEW_LINE);
        return result.toString();
    }
}