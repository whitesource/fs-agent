/**
 * Copyright (C) 2017 WhiteSource Ltd.
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
package org.whitesource.agent.dependency.resolver;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.CocoaPods.CocoaPodsDependencyResolver;
import org.whitesource.agent.dependency.resolver.bower.BowerDependencyResolver;
import org.whitesource.agent.dependency.resolver.dotNet.DotNetDependencyResolver;
import org.whitesource.agent.dependency.resolver.go.GoDependencyResolver;
import org.whitesource.agent.dependency.resolver.gradle.GradleDependencyResolver;
import org.whitesource.agent.dependency.resolver.hex.HexDependencyResolver;
import org.whitesource.agent.dependency.resolver.html.HtmlDependencyResolver;
import org.whitesource.agent.dependency.resolver.maven.MavenDependencyResolver;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;
import org.whitesource.agent.dependency.resolver.nuget.NugetDependencyResolver;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetConfigFileType;
import org.whitesource.agent.dependency.resolver.paket.PaketDependencyResolver;
import org.whitesource.agent.dependency.resolver.php.PhpDependencyResolver;
import org.whitesource.agent.dependency.resolver.python.PythonDependencyResolver;
import org.whitesource.agent.dependency.resolver.ruby.RubyDependencyResolver;
import org.whitesource.agent.dependency.resolver.sbt.SbtDependencyResolver;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.nio.file.Path;
import java.util.*;

/**
 * Holds and initiates all {@link AbstractDependencyResolver}s.
 *
 * @author eugen.horovitz
 */
public class DependencyResolutionService {

    /* --- Members --- */

    private final Logger logger = LoggerFactory.getLogger(DependencyResolutionService.class);

    private final FilesScanner fileScanner;
    private final Collection<AbstractDependencyResolver> dependencyResolvers;
    private final boolean ignoreSourceFiles;

    private boolean separateProjects;
    private boolean mavenAggregateModules;
    private boolean sbtAggregateModules;
    private boolean gradleAggregateModules;
    private boolean hexAggregateModules;

    /* --- Static members --- */

    public static final List<DependencyType> multiModuleDependencyTypes = Arrays.asList(DependencyType.MAVEN, DependencyType.GRADLE);

    /* --- Constructors --- */

    public DependencyResolutionService(ResolverConfiguration config) {
        final boolean npmRunPreStep = config.isNpmRunPreStep();
        final boolean npmIgnoreScripts = config.isNpmIgnoreScripts();
        final boolean npmResolveDependencies = config.isNpmResolveDependencies();
        final boolean npmIncludeDevDependencies = config.isNpmIncludeDevDependencies();
        final long npmTimeoutDependenciesCollector = config.getNpmTimeoutDependenciesCollector();
        final boolean npmIgnoreNpmLsErrors = config.getNpmIgnoreNpmLsErrors();
        final String npmAccessToken = config.getNpmAccessToken();
        final boolean npmYarnProject = config.getNpmYarnProject();
        final boolean npmIgnoreSourceFiles = config.isNpmIgnoreSourceFiles();

        final boolean bowerResolveDependencies = config.isBowerResolveDependencies();
        final boolean bowerRunPreStep = config.isBowerRunPreStep();
        final boolean bowerIgnoreSourceFiles = config.isBowerIgnoreSourceFiles();

        final boolean nugetResolveDependencies = config.isNugetResolveDependencies();
        final boolean nugetRestoreDependencies = config.isNugetRestoreDependencies();
        final boolean nugetRunPreStep = config.isNugetRunPreStep();
        final boolean nugetIgnoreSourceFiles = config.isNugetIgnoreSourceFiles();
        final boolean nugetResolveCsProjFiles = config.isNugetResolveCsProjFiles();
        final boolean nugetResolvePackagesConfigFiles = config.isNugetResolvePackagesConfigFiles();

        final boolean mavenResolveDependencies = config.isMavenResolveDependencies();
        final String[] mavenIgnoredScopes = config.getMavenIgnoredScopes();
        final boolean mavenAggregateModules = config.isMavenAggregateModules();
        final boolean mavenIgnorePomModules = config.isMavenIgnorePomModules();
        final boolean mavenIgnoreSourceFiles = config.isMavenIgnoreSourceFiles();
        final boolean mavenRunPreStep = config.isMavenRunPreStep();
        final boolean mavenIgnoreDependencyTreeErrors = config.isMavenIgnoreDependencyTreeErrors();

        boolean pythonResolveDependencies = config.isPythonResolveDependencies();
        final String[] pythonRequirementsFileIncludes = config.getPythonRequirementsFileIncludes();
        final boolean pythonIgnoreSourceFiles = config.isPythonIgnoreSourceFiles();
        final boolean ignorePipEnvInstallErrors = config.isIgnorePipEnvInstallErrors();
        final boolean runPipenvPreStep = config.IsRunPipenvPreStep();
        final boolean pipenvInstallDevDependencies = config.isPipenvInstallDevDependencies();


        boolean gradleResolveDependencies = config.isGradleResolveDependencies();
        boolean gradleAggregateModules = config.isGradleAggregateModules();
        final boolean gradleIgnoreSourceFiles = config.isGradleIgnoreSourceFiles();
        boolean gradleRunPreStep = config.isGradleRunPreStep();
        final String[] gradleIgnoredScopes = config.getGradleIgnoredScopes();
        final String gradleLocalRepositoryPath = config.getGradleLocalRepositoryPath();

        final boolean paketResolveDependencies = config.isPaketResolveDependencies();
        final String[] paketIgnoredScopes = config.getPaketIgnoredScopes();
        final boolean paketRunPreStep = config.isPaketRunPreStep();
        final String paketPath = config.getPaketPath();
        final boolean paketIgnoreSourceFiles = config.isPaketIgnoreSourceFiles();


        final boolean goResolveDependencies = config.isGoResolveDependencies();
        final boolean goIgnoreSourceFiles = config.isGoGlideIgnoreSourceFiles();

        final boolean rubyResolveDependencies = config.isRubyResolveDependencies();
        final boolean rubyRunBundleInstall = config.isRubyRunBundleInstall();
        final boolean rubyOverwriteGemFile = config.isRubyOverwriteGemFile();
        final boolean rubyInstallMissingGems = config.isRubyInstallMissingGems();
        final boolean rubyIgnoreSourceFiles = config.isRubyIgnoreSourceFiles();

        final boolean phpResolveDependencies = config.isPhpResolveDependencies();
        final boolean phpRunPreStep = config.isPhpRunPreStep();
        final boolean phpIncludeDevDependencies = config.isPhpIncludeDevDependencies();

        final boolean sbtResolveDependencies = config.isSbtResolveDependencies();
        final boolean sbtAggregateModules = config.isSbtAggregateModules();
        final boolean sbtRunPreStep = config.isSbtRunPreStep();
        final String sbtTargetFolder = config.getSbtTargetFolder();
        final boolean sbtIgnoreSourceFiles = config.isSbtIgnoreSourceFiles();

        final boolean htmlResolveDependencies = config.isHtmlResolveDependencies();

        final boolean cocoapodsResolveDependencies = config.isCocoapodsResolveDependencies();
        final boolean cocoapodsRunPreStep = config.isCocoapodsRunPreStep();
        final boolean cocoapodsIgnoreSourceFiles = config.isCocoapodsIgnoreSourceFiles();

        final boolean hexResolveDependencies = config.isHexResolveDependencies();
        final boolean hexRunPreStep = config.isHexRunPreStep();
        final boolean hexAggregateModules = config.isHexAggregateModules();
        final boolean hexIgnoreSourceFiles = config.isHexIgnoreSourceFiles();

        ignoreSourceFiles = config.isIgnoreSourceFiles();

        fileScanner = new FilesScanner();
        dependencyResolvers = new ArrayList<>();
        if (npmResolveDependencies) {
            dependencyResolvers.add(new NpmDependencyResolver(npmIncludeDevDependencies, npmIgnoreSourceFiles, npmTimeoutDependenciesCollector, npmRunPreStep, npmIgnoreNpmLsErrors,
                    npmAccessToken, npmYarnProject, npmIgnoreScripts));
        }
        if (bowerResolveDependencies) {
            dependencyResolvers.add(new BowerDependencyResolver(npmTimeoutDependenciesCollector, bowerRunPreStep, bowerIgnoreSourceFiles));
        }
        if (nugetResolveDependencies) {
            String whitesourceConfiguration = config.getWhitesourceConfiguration();
            if (nugetResolvePackagesConfigFiles) {
                dependencyResolvers.add(new NugetDependencyResolver(whitesourceConfiguration, NugetConfigFileType.CONFIG_FILE_TYPE, nugetRunPreStep, nugetIgnoreSourceFiles));
            }
            if (nugetResolveCsProjFiles) {
                dependencyResolvers.add(new DotNetDependencyResolver(whitesourceConfiguration, NugetConfigFileType.CSPROJ_TYPE, nugetRestoreDependencies, nugetIgnoreSourceFiles));
            }
        }
        if (mavenResolveDependencies) {
            dependencyResolvers.add(new MavenDependencyResolver(mavenAggregateModules, mavenIgnoredScopes, mavenIgnoreSourceFiles, mavenIgnorePomModules, mavenRunPreStep,
                    mavenIgnoreDependencyTreeErrors));
            this.mavenAggregateModules = mavenAggregateModules;
        }
        if (pythonResolveDependencies) {
            dependencyResolvers.add(new PythonDependencyResolver(config.getPythonPath(), config.getPipPath(),
                    config.isPythonIgnorePipInstallErrors(), config.isPythonInstallVirtualenv(), config.isPythonResolveHierarchyTree(), pythonRequirementsFileIncludes,
                    pythonIgnoreSourceFiles, ignorePipEnvInstallErrors, runPipenvPreStep, pipenvInstallDevDependencies));
        }

        if (gradleResolveDependencies) {
            dependencyResolvers.add(new GradleDependencyResolver(config.isGradleRunAssembleCommand(), gradleIgnoreSourceFiles, gradleAggregateModules,
                    config.getGradlePreferredEnvironment(), gradleIgnoredScopes, gradleLocalRepositoryPath, gradleRunPreStep));
            this.gradleAggregateModules = gradleAggregateModules;
        }

        if (paketResolveDependencies) {
            dependencyResolvers.add(new PaketDependencyResolver(paketIgnoredScopes, paketIgnoreSourceFiles, paketRunPreStep, paketPath));
        }

        if (goResolveDependencies) {
            dependencyResolvers.add(new GoDependencyResolver(config.getGoDependencyManager(), config.isGoCollectDependenciesAtRuntime(), goIgnoreSourceFiles,
                    config.isGoGlideIgnoreTestPackages(), config.isGoGradleEnableTaskAlias(), config.getGradlePreferredEnvironment(), config.isAddSha1()));
        }

        if (rubyResolveDependencies) {
            dependencyResolvers.add(new RubyDependencyResolver(rubyRunBundleInstall, rubyOverwriteGemFile, rubyInstallMissingGems, rubyIgnoreSourceFiles));
        }

        if (phpResolveDependencies) {
            dependencyResolvers.add(new PhpDependencyResolver(phpRunPreStep, phpIncludeDevDependencies, config.isAddSha1()));
        }

        if (htmlResolveDependencies) {
            dependencyResolvers.add(new HtmlDependencyResolver());
        }

        if (sbtResolveDependencies) {
            dependencyResolvers.add(new SbtDependencyResolver(sbtAggregateModules, sbtIgnoreSourceFiles, sbtRunPreStep, sbtTargetFolder));
            this.sbtAggregateModules = sbtAggregateModules;
        }

        if (cocoapodsResolveDependencies) {
            dependencyResolvers.add(new CocoaPodsDependencyResolver(cocoapodsRunPreStep, cocoapodsIgnoreSourceFiles));
        }

        if (hexResolveDependencies) {
            dependencyResolvers.add(new HexDependencyResolver(hexIgnoreSourceFiles, hexRunPreStep, hexAggregateModules));
            this.hexAggregateModules = hexAggregateModules;
        }

        this.separateProjects = false;
    }

    /* --- Public methods --- */

    public boolean isMavenAggregateModules() {
        return mavenAggregateModules;
    }

    public boolean isSbtAggregateModules() {
        return sbtAggregateModules;
    }

    public boolean isGradleAggregateModules() {
        return gradleAggregateModules;
    }

    public boolean isHexAggregateModules() {
        return hexAggregateModules;
    }

    public boolean isSeparateProjects() {
        return separateProjects;
    }

    public boolean isIgnoreSourceFiles() {
        return ignoreSourceFiles;
    }

    public boolean shouldResolveDependencies(Set<String> allFoundFiles) {
        for (AbstractDependencyResolver dependencyResolver : dependencyResolvers) {
            for (String bomFile : dependencyResolver.getManifestFiles()) {
                boolean shouldResolve = allFoundFiles.stream().filter(file -> file.endsWith(bomFile)).findAny().isPresent();
                if (shouldResolve) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<ResolutionResult> resolveDependencies(Collection<String> pathsToScan, String[] excludes) {
        Map<ResolvedFolder, AbstractDependencyResolver> topFolderResolverMap = new HashMap<>();
        Collection<ResolutionResult> multiModuleResults = new LinkedList<>();
        Collection<ResolutionResult> htmlResults = new LinkedList<>();

        dependencyResolvers.forEach(dependencyResolver -> {
            // add resolver excludes
            Collection<String> combinedExcludes = new LinkedList<>(Arrays.asList(excludes));
            Collection<String> resolverExcludes = dependencyResolver.getExcludes();
            for (String exclude : resolverExcludes) {
                combinedExcludes.add(exclude);
            }
            logger.debug("Attempting to find the top folders of {} with pattern {}", pathsToScan, dependencyResolver.getBomPattern());
            Collection<ResolvedFolder> topFolders = fileScanner.findTopFolders(pathsToScan, dependencyResolver.getBomPattern(), combinedExcludes);
            topFolders.forEach(topFolder -> topFolderResolverMap.put(topFolder, dependencyResolver));
        });
        logger.debug("Attempting to reduce dependencies");
        // reduce the dependencies and duplicates files
        reduceDependencies(topFolderResolverMap);

        logger.debug("Finishing reduce dependencies");
        List<ResolutionResult> resolutionResults = new ArrayList<>();


        topFolderResolverMap.forEach((resolvedFolder, dependencyResolver) -> {
            if (!resolvedFolder.getTopFoldersFound().isEmpty()) {
                logger.info("Trying to resolve " + dependencyResolver.getDependencyTypeName() + " dependencies");
            }
            resolvedFolder.getTopFoldersFound().forEach((topFolder, bomFiles) -> {
                // don't print folder in case of html resolution
                if (dependencyResolver.printResolvedFolder()) {
                    logger.info("topFolder = " + topFolder);
                }
                logger.debug("topFolder = " + topFolder);
                ResolutionResult result = null;
                try {
                    result = dependencyResolver.resolveDependencies(resolvedFolder.getOriginalScanFolder(), topFolder, bomFiles);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                    logger.debug("{}", e.getStackTrace());
                }
                if (result != null) {
                    resolutionResults.add(result);

                    // create lists in order to match htmlResolver dependencies to their original project (Maven/Gradle/Sbt)
                    if (multiModuleDependencyTypes.contains(dependencyResolver.getDependencyType())) {
                        multiModuleResults.add(result);

                    } else if (Constants.HTML.toUpperCase().equals(dependencyResolver.getDependencyTypeName())) {
                        htmlResults.add(result);
                    }
                }
            });
        });
        // match htmlResolver dependencies to their original project (Maven/Gradle/Sbt)
        findAndSetHtmlProject(multiModuleResults, htmlResults, resolutionResults);
        return resolutionResults;
    }

    private void findAndSetHtmlProject(Collection<ResolutionResult> multiModuleResults, Collection<ResolutionResult> htmlResults,
                                       Collection<ResolutionResult> resolutionResults) {
        if (!(multiModuleResults.isEmpty() && htmlResults.isEmpty())) {
            // parameters initialization
            Map<AgentProjectInfo, ResolutionResult> agentProjectInfoToResolutionResult = new HashMap<>();
            Map<AgentProjectInfo, Path> multiModuleProjects = generateMultiProjectMap(multiModuleResults, agentProjectInfoToResolutionResult);
            Map<AgentProjectInfo, Path> htmlProjects = generateMultiProjectMap(htmlResults, agentProjectInfoToResolutionResult);
            // Match for each html project its original project & remove its "fake" project from the list.
            for (Map.Entry<AgentProjectInfo, Path> multiModuleProject : multiModuleProjects.entrySet()) {
                for (Map.Entry<AgentProjectInfo, Path> htmlProject : htmlProjects.entrySet()) {
                    if (htmlProject.getValue().toAbsolutePath().toString().contains(multiModuleProject.getValue().toAbsolutePath().toString())) {
                        ResolutionResult htmlResult = agentProjectInfoToResolutionResult.get(htmlProject.getKey());
                        resolutionResults.remove(htmlResult);
                        multiModuleProject.getKey().getDependencies().addAll(htmlProject.getKey().getDependencies());
                        ResolutionResult multiModuleResult = agentProjectInfoToResolutionResult.get(htmlProject.getKey());
                        multiModuleResult.getResolvedProjects().put(multiModuleProject.getKey(), multiModuleProject.getValue());
                    }
                }
            }
        }
    }

    private Map<AgentProjectInfo, Path> generateMultiProjectMap(Collection<ResolutionResult> projectResults, Map<AgentProjectInfo,
            ResolutionResult> agentProjectInfoToResolutionResult) {
        Map<AgentProjectInfo, Path> resolvedProjects = new HashMap<>();
        // initialize projectResults & resolvedProjects
        for (ResolutionResult result : projectResults) {
            Map<AgentProjectInfo, Path> projects = result.getResolvedProjects();
            for (Map.Entry<AgentProjectInfo, Path> project : projects.entrySet()) {
                agentProjectInfoToResolutionResult.put(project.getKey(), result);
            }
            resolvedProjects.putAll(projects);
        }
        return resolvedProjects;
    }

    public Collection<AbstractDependencyResolver> getDependencyResolvers() {
        return dependencyResolvers;
    }

    /* --- Private methods --- */
    private void reduceDependencies(Map<ResolvedFolder, AbstractDependencyResolver> topFolderResolverMap) {
        //reduce the dependencies and duplicates files
        Set<String> topFolders = new HashSet<>();
        topFolderResolverMap.entrySet().forEach((resolverEntry) -> topFolders.addAll(resolverEntry.getKey().getTopFoldersFound().keySet()));

        // Take all resolvers and their folders
        for (Map.Entry<ResolvedFolder, AbstractDependencyResolver> entry : topFolderResolverMap.entrySet()) {
            AbstractDependencyResolver resolver = entry.getValue();
            ResolvedFolder resolvedFolder = entry.getKey();
            if (resolver != null && resolvedFolder != null) {
                // All folders of the current resolver (top folders and sub folders)
                Set<String> foldersFound = resolvedFolder.getTopFoldersFound().keySet();
                // Get the relevant folders for the current resolver (can be all folders or top folders only ...)
                Collection<String> foldersForResolver = resolver.getRelevantScannedFolders(foldersFound);
                // Remove all the irrelevant folders
                resolvedFolder.getTopFoldersFound().keySet().removeIf(folder -> !foldersForResolver.contains(folder));
            }
        }

    }

    private boolean isChildFolder(String childFolder, String topFolderParent) {
        boolean result = childFolder.contains(topFolderParent) && !childFolder.equals(topFolderParent);
        return result;
    }
}