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

import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    public DependencyResolutionService(boolean resolveNpmDependencies) {
        fileScanner = new FilesScanner();
        dependencyResolvers = new ArrayList<>();
        if (resolveNpmDependencies) {
            dependencyResolvers.add(new NpmDependencyResolver());
        }
    }

    /* --- Public methods --- */

    public boolean shouldResolveDependencies() {
        return dependencyResolvers.size() > 0;
    }

    public List<ResolutionResult> resolveDependencies(Collection<String> pathsToScan, String[] excludes) {
        List<ResolutionResult> resolutionResults = new ArrayList<>();
        dependencyResolvers.forEach(dependencyResolver -> {
            // get folders containing bom files
            Map<String, String[]> pathToBomFilesMap = fileScanner.findAllFiles(pathsToScan, dependencyResolver.getBomPattern(), excludes);

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
}