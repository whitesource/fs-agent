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
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
     * Dependency Resolver for Maven projects.
     *
     * @author eugen.horovitz
     */
    public class MavenDependencyResolver extends AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final String POM_XML = "pom.xml";
    private static final List<String> JAVA_EXTENSIONS = Arrays.asList(".java",".jar",".war",".ear",".car");

    //private static final String JAVA_EXTENSION_PATTERN = "**/*" + JAVA_EXTENSIONS;
    private static final String TARGET = "target";
    private static final String TEST = String.join(File.separator,new String[]{"src","test"});

    /* --- Constructor --- */

    public MavenDependencyResolver(boolean mavenIncludeDevDependencies) {
        super();
        this.dependencyCollector = new MavenTreeDependencyCollector(mavenIncludeDevDependencies);
        this.bomParser = new MavenParser();
    }

    /* --- Members --- */

    private final MavenTreeDependencyCollector dependencyCollector;

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, List<String> bomFiles) {
        // try to collect dependencies via 'npm ls'
        Collection<AgentProjectInfo> projects = dependencyCollector.collectDependencies(topLevelFolder);
        List<BomFile> files = bomFiles.stream().map(bomParser::parseBomFile)
                .filter(bom->!bom.getLocalFileName().contains(TARGET) && !bom.getLocalFileName().contains(TEST)).collect(Collectors.toList());
        // create excludes for .JAVA files upon finding MAVEN dependencies
        List<String> excludes = new LinkedList<>();

        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            Optional<BomFile> folderPath = files.stream().filter(file -> projectInfo.getCoordinates().getArtifactId().equals(file.getName())).findFirst();
            if (folderPath.isPresent()) {
                File topFolderFound = new File(folderPath.get().getLocalFileName()).getParentFile();

                // for java do not remove anything since they are not the duplicates of the dependencies found

                // excludes.addAll(normalizeLocalPath(projectFolder, topFolderFound.toString(), Arrays.asList(JAVA_EXTENSION_PATTERN), null));
                // excludes.addAll(normalizeLocalPath(projectFolder, topFolderFound.toString(), Arrays.asList(CLASS_EXTENSION_PATTERN), null));
                // excludes.addAll(normalizeLocalPath(projectFolder, topFolderFound.toString(), Arrays.asList(JAR_EXTENSION_PATTERN), null));

                return topFolderFound.toPath();
            }
            return null;
        }));
        ResolutionResult resolutionResult = new ResolutionResult(projectInfoPathMap, excludes);
        return resolutionResult;
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.addAll(getLanguageExcludes());
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return JAVA_EXTENSIONS;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.MAVEN;
    }

    @Override
    public String getBomPattern() {
        return "**/*" + POM_XML;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new HashSet<>();
    }
}