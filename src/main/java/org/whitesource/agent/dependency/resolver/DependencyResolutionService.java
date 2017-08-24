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

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.dependency.resolver.bower.BowerDependencyResolver;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;
import org.whitesource.agent.dependency.resolver.nuget.NugetDependencyResolver;
import org.whitesource.agent.utils.FilesScanner;

import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Holds and initiates all {@link AbstractDependencyResolver}s.
 *
 * @author eugen.horovitz
 */
public class DependencyResolutionService {

    /* --- Members --- */

    private final FilesScanner fileScanner;
    private final Collection<AbstractDependencyResolver> dependencyResolvers;

    /* --- Constructors --- */

    public DependencyResolutionService(Properties config) {
        final boolean npmResolveDependencies = getBooleanProperty(config, NPM_RESOLVE_DEPENDENCIES, true);
        final boolean npmIncludeDevDependencies = getBooleanProperty(config, NPM_INCLUDE_DEV_DEPENDENCIES, false);
        final boolean bowerResolveDependencies = getBooleanProperty(config, BOWER_RESOLVE_DEPENDENCIES, true);
        final boolean nugetResolveDependencies = getBooleanProperty(config, NUGET_RESOLVE_DEPENDENCIES, true);

        fileScanner = new FilesScanner();
        dependencyResolvers = new ArrayList<>();
        if (npmResolveDependencies) {
            dependencyResolvers.add(new NpmDependencyResolver(npmIncludeDevDependencies));
        }
        if (bowerResolveDependencies) {
            dependencyResolvers.add(new BowerDependencyResolver());
        }
        if (nugetResolveDependencies) {
            dependencyResolvers.add(new NugetDependencyResolver());
        }
    }

    /* --- Public methods --- */

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
        dependencyResolvers.forEach(dependencyResolver -> {
            // add resolver excludes
            Collection<String> combinedExcludes = new LinkedList<>(Arrays.asList(excludes));
            Collection<String> resolverExcludes = dependencyResolver.getExcludes();
            for (String exclude : resolverExcludes) {
                combinedExcludes.add(exclude);
            }

            Collection<ResolvedFolder> topFolders = fileScanner.findTopFolders(pathsToScan, dependencyResolver.getBomPattern(), combinedExcludes);
            topFolders.forEach(topFolder -> topFolderResolverMap.put(topFolder, dependencyResolver));
        });

        // reduce the dependencies and duplicates files
        reduceDependencies(topFolderResolverMap);

        List<ResolutionResult> resolutionResults = new ArrayList<>();
        topFolderResolverMap.forEach((resolvedFolder, dependencyResolver) -> {
            resolvedFolder.getTopFoldersFound().forEach((topFolder, bomFiles) -> {
                ResolutionResult result = dependencyResolver.resolveDependencies(resolvedFolder.getOriginalScanFolder(), topFolder, bomFiles);
                resolutionResults.add(result);
            });
        });

        return resolutionResults;
    }

    private void reduceDependencies(Map<ResolvedFolder, AbstractDependencyResolver> topFolderResolverMap) {
        //reduce the dependencies and duplicates files
        Set<String> topFolders = new HashSet<>();
        topFolderResolverMap.entrySet().forEach((resolverEntry) -> topFolders.addAll(resolverEntry.getKey().getTopFoldersFound().keySet()));
        //remove all folders that have a parent already mapped
        topFolders.stream().sorted().forEach(topFolderParent -> {
            topFolderResolverMap.forEach((resolvedFolder, dependencyResolver) -> {
                resolvedFolder.getTopFoldersFound().entrySet().removeIf(topFolderChild -> isChildFolder(topFolderChild.getKey(), topFolderParent));
            });
        });
    }

    private boolean isChildFolder(String childFolder, String topFolderParent) {
        boolean result = childFolder.contains(topFolderParent) && !childFolder.equals(topFolderParent);
        return result;
    }

    /* --- Private Methods --- */

    private boolean getBooleanProperty(Properties config, String propertyKey, boolean defaultValue) {
        boolean property = defaultValue;
        String propertyValue = config.getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Boolean.valueOf(propertyValue);
        }
        return property;
    }
}