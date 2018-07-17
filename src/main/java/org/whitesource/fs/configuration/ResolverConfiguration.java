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
import org.whitesource.agent.dependency.resolver.go.GoDependencyManager;

import java.util.Arrays;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class ResolverConfiguration {

    /* --- Constructors --- */

    @JsonCreator
    public ResolverConfiguration(
            @JsonProperty(NPM_RUN_PRE_STEP) boolean npmRunPreStep,
            @JsonProperty(NPM_RESOLVE_DEPENDENCIES) boolean npmResolveDependencies,
            @JsonProperty(NPM_IGNORE_SCRIPTS) boolean npmIgnoreScripts,
            @JsonProperty(NPM_INCLUDE_DEV_DEPENDENCIES) boolean npmIncludeDevDependencies,
            @JsonProperty(NPM_IGNORE_JAVA_SCRIPT_FILES) boolean npmIgnoreJavaScriptFiles,
            @JsonProperty(NPM_TIMEOUT_DEPENDENCIES_COLLECTOR_SECONDS) long npmTimeoutDependenciesCollector,
            @JsonProperty(NPM_ACCESS_TOKEN) String npmAccessToken,
            @JsonProperty(NPM_IGNORE_NPM_LS_ERRORS) boolean npmIgnoreNpmLsErrors,
            @JsonProperty(NPM_YARN_PROJECT) boolean npmYarnProject,

            @JsonProperty(BOWER_RESOLVE_DEPENDENCIES) boolean bowerResolveDependencies,
            @JsonProperty(BOWER_RUN_PRE_STEP) boolean bowerRunPreStep,

            @JsonProperty(NUGET_RESOLVE_DEPENDENCIES) boolean nugetResolveDependencies,
            @JsonProperty(NUGET_RESTORE_DEPENDENCIES) boolean nugetRestoreDependencies,

            @JsonProperty(MAVEN_RESOLVE_DEPENDENCIES) boolean mavenResolveDependencies,
            @JsonProperty(MAVEN_IGNORED_SCOPES) String[] mavenIgnoredScopes,
            @JsonProperty(MAVEN_AGGREGATE_MODULES) boolean mavenAggregateModules,
            @JsonProperty(MAVEN_IGNORE_POM_MODULES) boolean mavenIgnorePomModules,

            @JsonProperty(PYTHON_RESOLVE_DEPENDENCIES) boolean pythonResolveDependencies,
            @JsonProperty(PYTHON_PIP_PATH) String pipPath,
            @JsonProperty(PYTHON_PATH) String pythonPath,
            @JsonProperty(PYTHON_IS_WSS_PLUGIN_INSTALLED) boolean pythonIsWssPluginInstalled,
            @JsonProperty(PYTHON_UNINSTALL_WSS_PLUGIN) boolean pythonUninstallWssPlugin,
            @JsonProperty(PYTHON_IGNORE_PIP_INSTALL_ERRORS) boolean pythonIgnorePipInstallErrors,
            @JsonProperty(PYTHON_INSTALL_VIRTUALENV) boolean pythonInstallVirtualenv,
            @JsonProperty(PYTHON_RESOLVE_HIERARCHY_TREE) boolean pythonResolveHierarchyTree,
            @JsonProperty(PYTHON_REQUIREMENTS_FILE_INCLUDES) String[] pythonRequirementsFileIncludes,

            @JsonProperty(DEPENDENCIES_ONLY) boolean dependenciesOnly,
            @JsonProperty(WHITESOURCE_CONFIGURATION) String whitesourceConfiguration,

            @JsonProperty(GRADLE_RESOLVE_DEPENDENCIES) boolean  gradleResolveDependencies,
            @JsonProperty(GRADLE_RUN_ASSEMBLE_COMMAND) boolean  gradleRunAssembleCommand,
            @JsonProperty(GRADLE_AGGREGATE_MODULES) boolean     gradleAggregateModules,

            @JsonProperty(PAKET_RESOLVE_DEPENDENCIES) boolean paketResolveDependencies,
            @JsonProperty(PAKET_IGNORED_GROUPS) String[] paketIgnoredScopes,
            @JsonProperty(PAKET_IGNORE_FILES) boolean paketIgnoreFiles,
            @JsonProperty(PAKET_RUN_PRE_STEP) boolean paketRunPreStep,
            @JsonProperty(PAKET_EXE_PATH) String paketPath,

            @JsonProperty(GO_RESOLVE_DEPENDENCIES) boolean goResolveDependencies,
            @JsonProperty(GO_DEPENDENCY_MANAGER) String goDependencyManager,
            @JsonProperty(GO_COLLECT_DEPENDENCIES_AT_RUNTIME) boolean goCollectDependenciesAtRuntime,

            @JsonProperty(RUBY_RESOLVE_DEPENDENCIES) boolean rubyResolveDependencies,
            @JsonProperty(RUBY_RUN_BUNDLE_INSTALL) boolean rubyRunBundleInstall,
            @JsonProperty(RUBY_OVERWRITE_GEM_FILE) boolean rubyOverwriteGemFile,
            @JsonProperty(RUBY_INSTALL_MISSING_GEMS) boolean rubyInstallMissingGems,

            @JsonProperty(PHP_RESOLVE_DEPENDENCIES) boolean phpResolveDependencies,
            @JsonProperty(PHP_RUN_PRE_STEP) boolean phpRunPreStep,
            @JsonProperty(PHP_INCLUDE_DEV_DEPENDENCIES) boolean phpIncludeDevDependencies,
            @JsonProperty(SBT_RESOLVE_DEPENDENCIES) boolean sbtResolveDependencies,
            @JsonProperty(SBT_AGGREGATE_MODULES) boolean sbtAggregateModules,
            @JsonProperty(HTML_RESOLVE_DEPENDENCIES) boolean htmlResolveDependencies) {
        this.npmRunPreStep = npmRunPreStep;
        this.npmIgnoreScripts = npmIgnoreScripts;
        this.npmResolveDependencies = npmResolveDependencies;
        this.npmIncludeDevDependencies = npmIncludeDevDependencies;
        this.npmIgnoreJavaScriptFiles = npmIgnoreJavaScriptFiles;
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.npmAccessToken = npmAccessToken;
        this.npmIgnoreNpmLsErrors = npmIgnoreNpmLsErrors;
        this.npmYarnProject = npmYarnProject;

        this.bowerResolveDependencies = bowerResolveDependencies;
        this.bowerRunPreStep = bowerRunPreStep;

        this.nugetResolveDependencies = nugetResolveDependencies;
        this.nugetRestoreDependencies = nugetRestoreDependencies;

        this.mavenResolveDependencies = mavenResolveDependencies;
        this.mavenIgnoredScopes = mavenIgnoredScopes;
        this.mavenAggregateModules = mavenAggregateModules;
        this.mavenIgnorePomModules = mavenIgnorePomModules;

        this.pythonResolveDependencies = pythonResolveDependencies;
        this.pipPath = pipPath;
        this.pythonPath = pythonPath;
        this.pythonIsWssPluginInstalled = pythonIsWssPluginInstalled;
        this.pythonUninstallWssPlugin = pythonUninstallWssPlugin;
        this.pythonIgnorePipInstallErrors = pythonIgnorePipInstallErrors;
        this.pythonInstallVirtualenv = pythonInstallVirtualenv;
        this.pythonResolveHierarchyTree = pythonResolveHierarchyTree;
        this.pythonRequirementsFileIncludes = pythonRequirementsFileIncludes;

        this.dependenciesOnly = dependenciesOnly;
        this.whitesourceConfiguration = whitesourceConfiguration;

        this.gradleResolveDependencies = gradleResolveDependencies;
        this.gradleAggregateModules = gradleAggregateModules;
        this.gradleRunAssembleCommand = gradleRunAssembleCommand;

        this.paketResolveDependencies = paketResolveDependencies;
        this.paketIgnoredScopes = paketIgnoredScopes;
        this.paketIgnoreFiles = paketIgnoreFiles;
        this.paketRunPreStep = paketRunPreStep;
        this.paketPath = paketPath;

        this.goResolveDependencies = goResolveDependencies;
        if (goDependencyManager != null && !goDependencyManager.isEmpty()) {
            this.goDependencyManager = GoDependencyManager.getFromType(goDependencyManager);
        }
        this.goCollectDependenciesAtRuntime = goCollectDependenciesAtRuntime;

        this.rubyResolveDependencies = rubyResolveDependencies;
        this.rubyRunBundleInstall = rubyRunBundleInstall;
        this.rubyOverwriteGemFile = rubyOverwriteGemFile;
        this.rubyInstallMissingGems = rubyInstallMissingGems;

        this.phpResolveDependencies = phpResolveDependencies;
        this.phpRunPreStep = phpRunPreStep;
        this.phpIncludeDevDependencies = phpIncludeDevDependencies;

        this.sbtResolveDependencies = sbtResolveDependencies;
        this.sbtAggregateModules = sbtAggregateModules;

        this.htmlResolveDependencies = htmlResolveDependencies;
    }

    /* --- Members --- */

    private boolean     npmRunPreStep;
    private boolean     npmIgnoreScripts;
    private boolean     npmResolveDependencies;
    private boolean     npmIncludeDevDependencies;
    private boolean     npmIgnoreJavaScriptFiles;
    private String      npmAccessToken;
    private long        npmTimeoutDependenciesCollector;
    private boolean     npmIgnoreNpmLsErrors;
    private boolean     npmYarnProject;
    private boolean     bowerResolveDependencies;
    private boolean     bowerRunPreStep;
    private boolean     nugetResolveDependencies;
    private boolean     nugetRestoreDependencies;
    private boolean     mavenResolveDependencies;
    private String[]    mavenIgnoredScopes;
    private boolean     mavenAggregateModules;
    private boolean     mavenIgnorePomModules;
    private boolean     dependenciesOnly;
    private String      whitesourceConfiguration;
    private boolean     pythonResolveDependencies;
    private String      pipPath;
    private String      pythonPath;
    private boolean     pythonIgnorePipInstallErrors;
    private boolean     pythonInstallVirtualenv;
    private boolean     pythonResolveHierarchyTree;
    private String[] pythonRequirementsFileIncludes;

    private boolean gradleResolveDependencies;
    private boolean gradleRunAssembleCommand;
    private boolean gradleAggregateModules;

    private final boolean pythonIsWssPluginInstalled;
    private final boolean pythonUninstallWssPlugin;

    private boolean paketResolveDependencies;
    private String[] paketIgnoredScopes;
    private boolean paketIgnoreFiles;
    private boolean paketRunPreStep;
    private String paketPath;

    private boolean goResolveDependencies;
    private GoDependencyManager goDependencyManager;
    private boolean goCollectDependenciesAtRuntime;

    private boolean rubyResolveDependencies;
    private boolean rubyRunBundleInstall;
    private boolean rubyOverwriteGemFile;
    private boolean rubyInstallMissingGems;

    private boolean phpResolveDependencies;
    private boolean phpRunPreStep;
    private boolean phpIncludeDevDependencies;

    private boolean sbtResolveDependencies;
    private boolean sbtAggregateModules;

    private boolean htmlResolveDependencies;

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

    @JsonProperty(NUGET_RESOLVE_DEPENDENCIES)
    public boolean isNugetResolveDependencies() {
        return nugetResolveDependencies;
    }

    @JsonProperty(NUGET_RESTORE_DEPENDENCIES)
    public boolean isNugetRestoreDependencies() {
        return nugetRestoreDependencies;
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
    public boolean isMavenIgnorePomModules(){
        return mavenIgnorePomModules;
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

    public String[] getPythonRequirementsFileIncludes() {
        return pythonRequirementsFileIncludes;
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

    @JsonProperty(PAKET_RESOLVE_DEPENDENCIES)
    public boolean isPaketResolveDependencies() {
        return paketResolveDependencies;
    }

    @JsonProperty(PAKET_IGNORED_GROUPS)
    public String[] getPaketIgnoredScopes() {
        return paketIgnoredScopes;
    }

    @JsonProperty(PAKET_IGNORE_FILES)
    public boolean getPaketIgnoreFiles() {
        return paketIgnoreFiles;
    }

    @JsonProperty(PAKET_RUN_PRE_STEP)
    public boolean isPaketRunPreStep() {
        return paketRunPreStep;
    }

    @JsonProperty(PAKET_EXE_PATH)
    public String getPaketPath() {
        return paketPath;
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

    @JsonProperty(HTML_RESOLVE_DEPENDENCIES)
    public boolean isHtmlResolveDependencies() {
        return htmlResolveDependencies;
    }

    public void setNpmResolveDependencies(boolean npmResolveDependencies) {
        this.npmResolveDependencies = npmResolveDependencies;
    }

    public void setBowerResolveDependencies(boolean bowerResolveDependencies) {
        this.bowerResolveDependencies = bowerResolveDependencies;
    }

    public void setNugetResolveDependencies(boolean nugetResolveDependencies) {
        this.nugetResolveDependencies = nugetResolveDependencies;
    }

    public void setMavenResolveDependencies(boolean mavenResolveDependencies) {
        this.mavenResolveDependencies = mavenResolveDependencies;
    }

    public void setPythonResolveDependencies(boolean pythonResolveDependencies) {
        this.pythonResolveDependencies = pythonResolveDependencies;
    }

    public void setGradleResolveDependencies(boolean gradleResolveDependencies) {
        this.gradleResolveDependencies = gradleResolveDependencies;
    }

    public void setPhpResolveDependencies(boolean phpResolveDependencies) {
        this.phpResolveDependencies = phpResolveDependencies;
    }

    public void setPaketResolveDependencies(boolean paketResolveDependencies) {
        this.paketResolveDependencies = paketResolveDependencies;
    }

    public void setGoResolveDependencies(boolean goResolveDependencies) {
        this.goResolveDependencies = goResolveDependencies;
    }

    public void setRubyResolveDependencies(boolean rubyResolveDependencies) {
        this.rubyResolveDependencies = rubyResolveDependencies;
    }

    public void setSbtResolveDependencies(boolean sbtResolveDependencies) {
        this.sbtResolveDependencies = sbtResolveDependencies;
    }

    public void setHtmlResolveDependencies(boolean htmlResolveDependencies) {
        this.htmlResolveDependencies = htmlResolveDependencies;
    }

    @Override
    public String toString() {

        return ", dependenciesOnly=" + dependenciesOnly + '\n' +
                ", npmRunPreStep=" + npmRunPreStep +
                ", npmIgnoreScripts=" + npmIgnoreScripts +
                ", npmResolveDependencies=" + npmResolveDependencies +
                ", npmIncludeDevDependencies=" + npmIncludeDevDependencies +
                ", npmIgnoreJavaScriptFiles=" + npmIgnoreJavaScriptFiles +
                ", npmTimeoutDependenciesCollector=" + npmTimeoutDependenciesCollector +
                ", npmIgnoreNpmLsErrors=" + npmIgnoreNpmLsErrors + '\n' +
                ", bowerResolveDependencies=" + bowerResolveDependencies +
                ", bowerRunPreStep=" + bowerRunPreStep + '\n' +
                ", nugetResolveDependencies=" + nugetResolveDependencies +
                ", nugetRestoreDependencies=" + nugetRestoreDependencies + '\n' +
                ", mavenResolveDependencies=" + mavenResolveDependencies +
                ", mavenIgnoredScopes=" + Arrays.toString(mavenIgnoredScopes) +
                ", mavenAggregateModules=" + mavenAggregateModules + '\n' +
                ", pythonResolveDependencies=" + pythonResolveDependencies +
                ", pythonIgnorePipInstallErrors=" + pythonIgnorePipInstallErrors +
                ", pythonInstallVirtualenv=" + pythonInstallVirtualenv +
                ", pythonResolveHierarchyTree=" + pythonResolveHierarchyTree + '\n' +
                ", pythonRequirementsFileIncludes=" + Arrays.toString(pythonRequirementsFileIncludes) + '\n' +
                ", gradleResolveDependencies=" + gradleResolveDependencies +
                ", gradleRunAssembleCommand=" + gradleRunAssembleCommand + '\n' +
                ", paketResolveDependencies=" + paketResolveDependencies +
                ", paketIgnoredScopes=" + Arrays.toString(paketIgnoredScopes) +
                ", paketIgnoreFiles=" + paketIgnoreFiles +
                ", paketRunPreStep=" + paketRunPreStep + '\n' +
                ", goResolveDependencies=" + goResolveDependencies +
                ", goDependencyManager=" + goDependencyManager +
                ", goCollectDependenciesAtRuntime=" + goCollectDependenciesAtRuntime + '\n' +
                ", rubyResolveDependencies=" + rubyResolveDependencies +
                ", rubyRunBundleInstall=" + rubyRunBundleInstall +
                ", rubyOverwriteGemFile=" + rubyOverwriteGemFile +
                ", rubyInstallMissingGems=" + rubyInstallMissingGems + '\n' +
                ", phpResolveDependencies=" + phpResolveDependencies +
                ", phpRunPreStep=" + phpRunPreStep +
                ", phpIncludeDevDependenices=" + phpIncludeDevDependencies + '\n' +
                ", sbtResolveDependencies=" + sbtResolveDependencies +
                ", sbtAggregateModules=" + sbtAggregateModules + '\n' +
                ", htmlResolveDependencies=" + htmlResolveDependencies;
    }


}