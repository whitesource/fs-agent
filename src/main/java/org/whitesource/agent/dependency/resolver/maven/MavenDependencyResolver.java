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
package org.whitesource.agent.dependency.resolver.maven;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Dependency Resolver for Maven projects.
 *
 * @author eugen.horovitz
 */
public class MavenDependencyResolver extends AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final String POM_XML = "**/pom.xml";
    private static final List<String> JAVA_EXTENSIONS = Arrays.asList(".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String TEST = String.join(File.separator, new String[]{Constants.SRC, "test"});
    private final boolean mavenAggregateModules;
    private final boolean dependenciesOnly;

    /* --- Constructor --- */

    public MavenDependencyResolver(boolean mavenAggregateModules, String[] mavenIgnoredScopes, boolean dependenciesOnly, boolean ignorePomModules) {
        super();
        this.dependencyCollector = new MavenTreeDependencyCollector(mavenIgnoredScopes, ignorePomModules);
        this.bomParser = new MavenPomParser();
        this.mavenAggregateModules = mavenAggregateModules;
        this.dependenciesOnly = dependenciesOnly;
    }

    /* --- Members --- */

    private final MavenTreeDependencyCollector dependencyCollector;

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // try to collect dependencies via 'mvn dependency tree and parse'
        Collection<AgentProjectInfo> projects = dependencyCollector.collectDependencies(topLevelFolder);

        List<BomFile> files = bomFiles.stream().map(bomParser::parseBomFile)
                .filter(Objects::nonNull).filter(bom -> !bom.getLocalFileName().contains(TEST))
                .collect(Collectors.toList());
        // create excludes for .JAVA files upon finding MAVEN dependencies
        Set<String> excludes = new HashSet<>();

        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {

            // map each pom file to specific project
            Optional<BomFile> folderPath = files.stream().filter(file -> projectInfo.getCoordinates().getArtifactId().equals(file.getName())).findFirst();
            if (folderPath.isPresent()) {
                File topFolderFound = new File(folderPath.get().getLocalFileName()).getParentFile();

                // in java do not remove anything since they are not the duplicates of the dependencies found
                // discard other java files only if specified ( decenciesOnly = true)
                if (dependenciesOnly) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topFolderFound.toString(), JAVA_EXTENSIONS, null));
                }
                return topFolderFound.toPath();
            }else {
                if (dependenciesOnly) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder.toString(), JAVA_EXTENSIONS, null));
                }
            }
            return Paths.get(topLevelFolder);
        }));

        ResolutionResult resolutionResult;
        if (!mavenAggregateModules) {
            resolutionResult = new ResolutionResult(projectInfoPathMap, excludes, getDependencyType(), topLevelFolder);
        } else {
            resolutionResult = new ResolutionResult(projectInfoPathMap.keySet().stream()
                    .flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()), excludes, getDependencyType(), topLevelFolder);
        }
        return resolutionResult;
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.addAll(getLanguageExcludes());
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return JAVA_EXTENSIONS;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.MAVEN;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.MAVEN.name();
    }

    @Override
    public String[] getBomPattern() {
        return new String[]{POM_XML};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new HashSet<>();
    }
}