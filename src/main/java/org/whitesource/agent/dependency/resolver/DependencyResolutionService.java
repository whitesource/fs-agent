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
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.bower.BowerDependencyResolver;
import org.whitesource.agent.dependency.resolver.dotNet.DotNetDependencyResolver;
import org.whitesource.agent.dependency.resolver.go.GoDependencyResolver;
import org.whitesource.agent.dependency.resolver.gradle.GradleDependencyResolver;
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
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.FileNotFoundException;
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
    private final boolean dependenciesOnly;

    private boolean separateProjects;
    private boolean mavenAggregateModules;
    private boolean sbtAggregateModules;
    private boolean gradleAggregateModules;

    /* --- Static members --- */

    public static final List<DependencyType> multiModuleDependencyTypes = Arrays.asList(DependencyType.MAVEN, DependencyType.GRADLE);

    /* --- Constructors --- */

    public DependencyResolutionService(ResolverConfiguration config) {
        final boolean npmRunPreStep = config.isNpmRunPreStep();
        final boolean npmIgnoreScripts = config.isNpmIgnoreScripts();
        final boolean npmResolveDependencies = config.isNpmResolveDependencies();
        final boolean npmIncludeDevDependencies = config.isNpmIncludeDevDependencies();
        final boolean npmIgnoreJavaScriptFiles = config.isNpmIgnoreJavaScriptFiles();
        final long npmTimeoutDependenciesCollector = config.getNpmTimeoutDependenciesCollector();
        final boolean npmIgnoreNpmLsErrors = config.getNpmIgnoreNpmLsErrors();
        final String npmAccessToken = config.getNpmAccessToken();
        final boolean npmYarnProject = config.getNpmYarnProject();

        final boolean bowerResolveDependencies = config.isBowerResolveDependencies();
        final boolean bowerRunPreStep = config.isBowerRunPreStep();

        final boolean nugetResolveDependencies = config.isNugetResolveDependencies();
        final boolean nugetRestoreDependencies = config.isNugetRestoreDependencies();

        final boolean mavenResolveDependencies = config.isMavenResolveDependencies();
        final String[] mavenIgnoredScopes = config.getMavenIgnoredScopes();
        final boolean mavenAggregateModules = config.isMavenAggregateModules();
        final boolean mavenIgnorePomModules = config.isMavenIgnorePomModules();

        boolean pythonResolveDependencies = config.isPythonResolveDependencies();
        final String[] pythonRequirementsFileIncludes = config.getPythonRequirementsFileIncludes();

        boolean gradleResolveDependencies = config.isGradleResolveDependencies();
        boolean gradleAggregateModules = config.isGradleAggregateModules();

        final boolean paketResolveDependencies = config.isPaketResolveDependencies();
        final String[] paketIgnoredScopes = config.getPaketIgnoredScopes();
        final boolean paketIgnoreFiles = config.getPaketIgnoreFiles();
        final boolean paketRunPreStep = config.isPaketRunPreStep();
        final String paketPath = config.getPaketPath();

        final boolean goResolveDependencies = config.isGoResolveDependencies();

        final boolean rubyResolveDependencies = config.isRubyResolveDependencies();
        final boolean rubyRunBundleInstall = config.isRubyRunBundleInstall();
        final boolean rubyOverwriteGemFile = config.isRubyOverwriteGemFile();
        final boolean rubyInstallMissingGems = config.isRubyInstallMissingGems();

        final boolean phpResolveDependencies = config.isPhpResolveDependencies();
        final boolean phpRunPreStep = config.isPhpRunPreStep();
        final boolean phpIncludeDevDependencies = config.isPhpIncludeDevDependencies();

        final boolean sbtResolveDependencies = config.isSbtResolveDependencies();
        final boolean sbtAggregateModules = config.isSbtAggregateModules();

        final boolean htmlResolveDependencies = config.isHtmlResolveDependencies();

        dependenciesOnly = config.isDependenciesOnly();

        fileScanner = new FilesScanner();
        dependencyResolvers = new ArrayList<>();
        if (npmResolveDependencies) {
            dependencyResolvers.add(new NpmDependencyResolver(npmIncludeDevDependencies, npmIgnoreJavaScriptFiles, npmTimeoutDependenciesCollector, npmRunPreStep, npmIgnoreNpmLsErrors, npmAccessToken, npmYarnProject, npmIgnoreScripts));
        }
        if (bowerResolveDependencies) {
            dependencyResolvers.add(new BowerDependencyResolver(npmTimeoutDependenciesCollector, bowerRunPreStep));
        }
        if (nugetResolveDependencies) {
            String whitesourceConfiguration = config.getWhitesourceConfiguration();
            dependencyResolvers.add(new NugetDependencyResolver(whitesourceConfiguration, NugetConfigFileType.CONFIG_FILE_TYPE));
            dependencyResolvers.add(new DotNetDependencyResolver(whitesourceConfiguration, NugetConfigFileType.CSPROJ_TYPE, nugetRestoreDependencies));
        }
        if (mavenResolveDependencies) {
            dependencyResolvers.add(new MavenDependencyResolver(mavenAggregateModules, mavenIgnoredScopes, dependenciesOnly, mavenIgnorePomModules));
            this.mavenAggregateModules = mavenAggregateModules;
        }
        if (pythonResolveDependencies) {
            dependencyResolvers.add(new PythonDependencyResolver(config.getPythonPath(), config.getPipPath(),
                    config.isPythonIgnorePipInstallErrors(), config.isPythonInstallVirtualenv(), config.isPythonResolveHierarchyTree(), pythonRequirementsFileIncludes));
        }

        if (gradleResolveDependencies) {
            dependencyResolvers.add(new GradleDependencyResolver(config.isGradleRunAssembleCommand(), dependenciesOnly, gradleAggregateModules));
            this.gradleAggregateModules = gradleAggregateModules;
        }

        if (paketResolveDependencies) {
            dependencyResolvers.add(new PaketDependencyResolver(paketIgnoredScopes, paketIgnoreFiles, paketRunPreStep, paketPath));
        }

        if (goResolveDependencies) {
            dependencyResolvers.add(new GoDependencyResolver(config.getGoDependencyManager(), config.isGoCollectDependenciesAtRuntime(), config.isDependenciesOnly()));
        }

        if (rubyResolveDependencies) {
            dependencyResolvers.add(new RubyDependencyResolver(rubyRunBundleInstall, rubyOverwriteGemFile, rubyInstallMissingGems));
        }

        if (phpResolveDependencies) {
            dependencyResolvers.add(new PhpDependencyResolver(phpRunPreStep, phpIncludeDevDependencies));
        }

        if (htmlResolveDependencies) {
            dependencyResolvers.add(new HtmlDependencyResolver());
        }

        if (sbtResolveDependencies) {
            dependencyResolvers.add(new SbtDependencyResolver(sbtAggregateModules, dependenciesOnly));
            this.sbtAggregateModules = sbtAggregateModules;
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

    public boolean isSeparateProjects() {
        return separateProjects;
    }

    public boolean isDependenciesOnly() {
        return dependenciesOnly;
    }

    public boolean shouldResolveDependencies(Set<String> allFoundFiles) {
        for (AbstractDependencyResolver dependencyResolver : dependencyResolvers) {
            for (String fileExtension : dependencyResolver.getSourceFileExtensions()) {
                boolean shouldResolve = allFoundFiles.stream().filter(file -> file.endsWith(fileExtension)).findAny().isPresent();
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
                } catch (FileNotFoundException e) {
                    logger.error(e.getMessage());
                }
                resolutionResults.add(result);

                // create lists in order to match htmlResolver dependencies to their original project (Maven/Gradle/Sbt)
                if (multiModuleDependencyTypes.contains(dependencyResolver.getDependencyType())) {
                    multiModuleResults.add(result);

                } else if (Constants.HTML.toUpperCase().equals(dependencyResolver.getDependencyTypeName())) {
                    htmlResults.add(result);
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
        //remove all folders that have a parent already mapped
        topFolders.stream().sorted().forEach(topFolderParent -> {
            topFolderResolverMap.forEach((resolvedFolder, dependencyResolver) -> {
                if (!(dependencyResolver instanceof HtmlDependencyResolver)) {
                    resolvedFolder.getTopFoldersFound().entrySet().removeIf(topFolderChild -> isChildFolder(topFolderChild.getKey(), topFolderParent));
                }
            });
        });
    }

    private boolean isChildFolder(String childFolder, String topFolderParent) {
        boolean result = childFolder.contains(topFolderParent) && !childFolder.equals(topFolderParent);
        return result;
    }
}