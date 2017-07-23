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
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public abstract class AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final Logger logger = LoggerFactory.getLogger(AbstractDependencyResolver.class);

    public static final String JS_PATTERN = "**/*.js";
    private static final String EXAMPLE = "**/example/**/";
    private static final String EXAMPLES = "**/examples/**/";
    private static final String WS_BOWER_FOLDER = "**/.ws_bower/**/";
    private static final String TEST = "**/test/**/";
    private static String BACK_SLASH = "\\";
    private static String FORWARD_SLASH = "/";

    /* --- Public methods --- */

    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, List<String> bomFiles) {
        // parse package.json files
        Collection<BomFile> parsedBomFiles = new LinkedList<>();

        Map<File, List<File>> mapBomFiles = bomFiles.stream().map(file -> new File(file)).collect(Collectors.groupingBy(File::getParentFile));

        List<File> files = mapBomFiles.entrySet().stream().map(entry -> {
            if (entry.getValue().size() > 1) {
                return entry.getValue().stream().filter(file -> fileShouldBeParsed(file)).findFirst().get();
            } else {
                return entry.getValue().stream().findFirst().get();
            }
        }).collect(Collectors.toList());

        files.forEach(bomFile -> {
            BomFile parsedBomFile = getBomParser().parseBomFile(bomFile.getAbsolutePath());
            if (parsedBomFile.isValid()) {
                parsedBomFiles.add(parsedBomFile);
            }
        });

        // try to collect dependencies via 'npm ls'
        Collection<DependencyInfo> dependencies = getDependencyCollector().collectDependencies(topLevelFolder);
        boolean lsSuccess = dependencies.size() > 0;
        if (lsSuccess) {
            handleLsSuccess(parsedBomFiles, dependencies);
        } else {
            dependencies.addAll(collectPackageJsonDependencies(parsedBomFiles));
        }

        // create excludes for .js files upon finding NPM dependencies
        List<String> excludes = new LinkedList<>();
        if (!dependencies.isEmpty()) {
            excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, Arrays.asList(JS_PATTERN)));
        }
        return new ResolutionResult(dependencies, excludes);
    }

    public Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        String bomPattern = getBomPattern();
        excludes.add(EXAMPLE + bomPattern);
        excludes.add(EXAMPLES + bomPattern);
        excludes.add(WS_BOWER_FOLDER + bomPattern);
        excludes.add(TEST + bomPattern);

        excludes.addAll(getLanguageExcludes());
        return excludes;
    }

    /* --- Abstract methods --- */

    protected abstract Collection<String> getSourceFileExtensions();

    protected abstract DependencyType getDependencyType();

    protected abstract String getBomPattern();

    protected abstract String getPreferredFileName();

    protected abstract BomParser getBomParser();

    protected abstract DependencyCollector getDependencyCollector();

    protected abstract boolean isMatchChildDependency(DependencyInfo childDependency, String name, String version);

    protected abstract void enrichDependency(DependencyInfo dependency, BomFile packageJson);

    protected abstract Collection<String> getLanguageExcludes();

    /* --- Private methods --- */

    /**
     * Collect dependencies from package.json files - without 'npm ls'
     */
    private Collection<DependencyInfo> collectPackageJsonDependencies(Collection<BomFile> packageJsons) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        Map<DependencyInfo, BomFile> dependencyPackageJsonMap = new HashMap<>();
        for (BomFile packageJson : packageJsons) {
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

    private List<String> normalizeLocalPath(String parentFolder, String topFolderFound, Collection<String> excludes) {
        String normalizedRoot = new File(parentFolder).getPath();
        if (normalizedRoot.equals(topFolderFound)) {
            topFolderFound = topFolderFound
                    .replace(normalizedRoot, "")
                    .replace(BACK_SLASH, FORWARD_SLASH);
        } else {
            topFolderFound = topFolderFound
                    .replace(parentFolder, "")
                    .replace(BACK_SLASH, FORWARD_SLASH);
        }

        if (topFolderFound.length() > 0)
            topFolderFound = topFolderFound.substring(1, topFolderFound.length()) + FORWARD_SLASH;

        String finalRes = topFolderFound;
        return excludes.stream().map(exclude -> finalRes + exclude).collect(Collectors.toList());
    }

    private boolean fileShouldBeParsed(File file) {
        return (file.getAbsolutePath().endsWith(getPreferredFileName()));
    }

    private void handleLsSuccess(Collection<BomFile> packageJsonFiles, Collection<DependencyInfo> dependencies) {
        Map<String, BomFile> resultFiles = packageJsonFiles.stream()
                .filter(packageJson -> packageJson != null && packageJson.isValid())
                .filter(distinctByKey(file -> file.getFileName()))
                .collect(Collectors.toMap(BomFile::getUniqueDependencyName, Function.identity()));

        dependencies.forEach(dependency -> handleLSDependencyRecursivelyImpl(dependency, resultFiles));
    }

    private void handleLSDependencyRecursivelyImpl(DependencyInfo dependency, Map<String, BomFile> resultFiles) {
        String uniqueName = BomFile.getUniqueDependencyName(dependency.getGroupId(), dependency.getVersion());
        BomFile packageJson = resultFiles.get(uniqueName);
        if (packageJson != null) {
            enrichDependency(dependency, packageJson);
        } else {
            logger.debug("Dependency {} could not be enriched.'package.json' could not be found", dependency.getArtifactId());
        }
        dependency.getChildren().forEach(childDependency -> handleLSDependencyRecursivelyImpl(childDependency, resultFiles));
    }

    private void setHierarchy(Map<DependencyInfo, BomFile> dependencyPackageJsonMap) {
        dependencyPackageJsonMap.forEach((dependency, packageJson) -> {
            packageJson.getDependencies().forEach((name, version) -> {
                Optional<DependencyInfo> childDep = dependencyPackageJsonMap.keySet().stream()
                        .filter(childDependency -> isMatchChildDependency(childDependency, name, version))
                        .findFirst();

                if (childDep.isPresent()) {
                    dependency.getChildren().add(childDep.get());
                }
            });
        });
    }

    private <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}