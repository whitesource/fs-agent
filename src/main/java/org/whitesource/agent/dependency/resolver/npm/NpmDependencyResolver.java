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
package org.whitesource.agent.dependency.resolver.npm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.*;

/**
 * Dependency Resolver for NPM projects.
 *
 * @author eugen.horovitz
 */
public class NpmDependencyResolver extends AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmDependencyResolver.class);

    public static final String JS_PATTERN = "/**/*.js";

    private static String PACKAGE_JSON = "package.json";

    /* --- Members --- */

    private final NpmLsDependencyCollector npmLsDependencyCollector;

    /* --- Constructor --- */

    public NpmDependencyResolver() {
        npmLsDependencyCollector = new NpmLsDependencyCollector();
    }

    /* --- Public methods --- */

    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, List<String> bomFiles) {
        // parse package.json files
        Collection<NpmPackageJsonFile> packageJsonFiles = new LinkedList<>();
        bomFiles.forEach(bomFile -> packageJsonFiles.add(NpmPackageJsonFile.parseNpmPackageJsonFile(bomFile)));

        // try to collect dependencies via 'npm ls'
        Collection<DependencyInfo> dependencies = npmLsDependencyCollector.collectDependencies(projectFolder);
        boolean npmLsSuccess = dependencies.size() > 0;
        if (npmLsSuccess) {
            handleNpmLsSuccess(packageJsonFiles, dependencies);
        } else {
            dependencies.addAll(collectPackageJsonDependencies(packageJsonFiles));
        }

        // create excludes for .js files upon finding NPM dependencies
        List<String> excludes = new LinkedList<>();
        if (!dependencies.isEmpty()) {
            excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, Arrays.asList(JS_PATTERN)));
        }
        return new ResolutionResult(dependencies, excludes);
    }

    /* --- Overridden methods --- */

    @Override
    public String getBomPattern() {
        return "**/*" + PACKAGE_JSON;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NPM;
    }

    /* --- Private methods --- */

    /**
     * Collect dependencies from package.json files - without 'npm ls'
     */
    private Collection<DependencyInfo> collectPackageJsonDependencies(Collection<NpmPackageJsonFile> packageJsons) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        Map<DependencyInfo, NpmPackageJsonFile> dependencyPackageJsonMap = new HashMap<>();
        for (NpmPackageJsonFile packageJson : packageJsons) {
            if (packageJson != null && packageJson.isValid()) {
                // do not add new dependencies if 'npm ls' already returned all
                DependencyInfo dependency = new DependencyInfo();
                dependencies.add(dependency);
                enrichDependency(dependency, packageJson);
                dependencyPackageJsonMap.put(dependency, packageJson);
            }
        }
        // set hierarchy in case the 'npm ls' did not run or it did not return results
        setHierarchy(dependencyPackageJsonMap);
        return dependencies;
    }

    private void handleNpmLsSuccess(Collection<NpmPackageJsonFile> packageJsonFiles, Collection<DependencyInfo> dependencies) {
        for (NpmPackageJsonFile packageJson : packageJsonFiles) {
            if (packageJson != null && packageJson.isValid()) {
                // enrich original dependency with data
                for (DependencyInfo dependency : dependencies) {
                    if (dependency.getVersion().equals(packageJson.getVersion()) &&
                            dependency.getFilename().equals(packageJson.getFileName())) {
                        enrichDependency(dependency, packageJson);
                    }
                }
            }
        }
    }

    private void enrichDependency(DependencyInfo dependency, NpmPackageJsonFile packageJson) {
        dependency.setSha1(packageJson.getSha1());
        dependency.setGroupId(packageJson.getName());
        dependency.setArtifactId(packageJson.getFileName());
        dependency.setVersion(packageJson.getVersion());
        dependency.setSystemPath(packageJson.getLocalFileName());
        dependency.setFilename(packageJson.getFileName());
        dependency.setDependencyType(getDependencyType());
    }

    private void setHierarchy(Map<DependencyInfo, NpmPackageJsonFile> dependencyPackageJsonMap) {
        dependencyPackageJsonMap.forEach((dependency, packageJson) -> {
            packageJson.getDependencies().forEach((name, version) -> {
                Optional<DependencyInfo> childDep = dependencyPackageJsonMap.keySet().stream()
                        .filter(childDependency -> childDependency.getFilename().equals(NpmPackageJsonFile.getNpmArtifactId(name, version)))
                        .findFirst();

                if (childDep.isPresent()) {
                    dependency.getChildren().add(childDep.get());
                }
            });
        });
    }
}