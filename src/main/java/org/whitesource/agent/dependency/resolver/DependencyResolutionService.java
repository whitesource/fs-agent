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
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;

import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES;
import static org.whitesource.agent.ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES;

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

        fileScanner = new FilesScanner();
        dependencyResolvers = new ArrayList<>();
        if (npmResolveDependencies) {
            dependencyResolvers.add(new NpmDependencyResolver(npmIncludeDevDependencies));
        }
    }

    /* --- Public methods --- */

    public boolean shouldResolveDependencies() {
        return dependencyResolvers.size() > 0;
    }

    public List<ResolutionResult> resolveDependencies(Collection<String> pathsToScan, String[] excludes) {
        List<ResolutionResult> resolutionResults = new ArrayList<>();
        dependencyResolvers.forEach(dependencyResolver -> {
            // add resolver excludes
            Collection<String> combinedExcludes = new LinkedList<>(Arrays.asList(excludes));
            Collection<String> resolverExcludes = dependencyResolver.getExcludes();
            for (String exclude : resolverExcludes) {
                combinedExcludes.add(exclude);
            }

            // get folders containing bom files
            Map<String, String[]> pathToBomFilesMap = fileScanner.findAllFiles(pathsToScan, dependencyResolver.getBomPattern(), combinedExcludes);

            // resolve dependencies
            pathToBomFilesMap.forEach((folder, bomFile) -> {
                // get top folders with boms (the parent of each project)
                Map<String, List<String>> topFolders = fileScanner.getTopFoldersWithIncludedFiles(folder, bomFile);

                // for each top folder, resolve dependencies
                topFolders.forEach((topFolder, bomFiles) -> {
                    ResolutionResult result = dependencyResolver.resolveDependencies(folder, topFolder, bomFiles);
                    resolutionResults.add(result);
                });
            });
        });
        return resolutionResults;
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