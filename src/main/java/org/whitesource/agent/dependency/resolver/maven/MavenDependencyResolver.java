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
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.AddDependencyFileRecursionHelper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Dependency Resolver for Maven projects.
 *
 * @author eugen.horovitz
 */
public class MavenDependencyResolver extends AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final String POM_XML = "pom.xml";
    private static final List<String> JAVA_EXTENSIONS = Arrays.asList(".java", ".jar", ".war", ".ear", ".car", ".class", "pom.xml");
    private static final String TEST = String.join(File.separator, new String[]{Constants.SRC, "test"});
    private final String MAIN_FOLDER = "Main_Folder";
    private final boolean mavenAggregateModules;
    private final boolean ignoreSourceFiles;
    private final boolean mavenIgnoreDependencyTreeErrors;
    private final boolean ignorePomModules;

    /* --- Constructor --- */

    public MavenDependencyResolver(boolean mavenAggregateModules, String[] mavenIgnoredScopes, boolean ignoreSourceFiles, boolean ignorePomModules, boolean runPreStep,boolean mavenIgnoreDependencyTreeErrors) {
        super();
        this.dependencyCollector = new MavenTreeDependencyCollector(mavenIgnoredScopes, ignorePomModules, runPreStep, mavenIgnoreDependencyTreeErrors);
        this.bomParser = new MavenPomParser(ignorePomModules);
        this.mavenAggregateModules = mavenAggregateModules;
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.mavenIgnoreDependencyTreeErrors = mavenIgnoreDependencyTreeErrors;
        this.ignorePomModules = ignorePomModules;
    }

    /* --- Members --- */

    private final MavenTreeDependencyCollector dependencyCollector;

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // try to collect dependencies via 'mvn dependency tree and parse'
        Collection<AgentProjectInfo> projects = dependencyCollector.collectDependencies(topLevelFolder);
        if (mavenIgnoreDependencyTreeErrors && dependencyCollector.isErrorsRunningDependencyTree()) {
             collectDependenciesFromPomXml(bomFiles, projects);
        }
        List<BomFile> files = bomFiles.stream().map(bomParser::parseBomFile)
                .filter(Objects::nonNull).filter(bom -> !bom.getLocalFileName().contains(TEST))
                .collect(Collectors.toList());
        // create excludes for .JAVA files upon finding MAVEN dependencies
        Set<String> excludes = new HashSet<>();

        addDependencyFile(projects, files);

        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            // map each pom file to specific project
            Optional<BomFile> folderPath = files.stream().filter(file -> projectInfo.getCoordinates().getArtifactId().equals(file.getName())).findFirst();
            if (folderPath.isPresent()) {
                File topFolderFound = new File(folderPath.get().getLocalFileName()).getParentFile();

                // in java do not remove anything since they are not the duplicates of the dependencies found
                // discard other java files only if specified ( decenciesOnly = true)
                if (ignoreSourceFiles) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topFolderFound.toString(), extensionPattern(JAVA_EXTENSIONS), null));
                }
                return topFolderFound.toPath();
            } else {
                if (ignoreSourceFiles) {
                    excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, extensionPattern(JAVA_EXTENSIONS), null));
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

    private void addDependencyFile(Collection<AgentProjectInfo> projects, List<BomFile> files) {
        projects.stream().forEach(agentProjectInfo -> {
            BomFile bomFile = files.stream().filter(b -> b.getName().equals(agentProjectInfo.getCoordinates().getArtifactId())).findFirst().orElse(null);
            if (bomFile != null){
                // this code turn the dependencies tree recursively into a flat-list,
                // so that each dependency has its dependencyFile set
                agentProjectInfo.getDependencies().stream()
                        .flatMap(AddDependencyFileRecursionHelper::flatten)
                        .forEach(dependencyInfo -> dependencyInfo.setDependencyFile(bomFile.getLocalFileName()));
            }
        });
    }

    // when failing to read data from 'mvn dependency:tree' output - trying to read directly from POM files
    private void collectDependenciesFromPomXml(Set<String> bomFiles, Collection<AgentProjectInfo> projects) {
        MavenPomParser pomParser = new MavenPomParser(ignorePomModules);
        List<BomFile> bomFileList = new LinkedList<>();
        HashMap<String, String> bomArtifactPathMap = new HashMap<>();
        for (String bomFileName : bomFiles) {
            BomFile bomfile = pomParser.parseBomFile(bomFileName);
            bomFileList.add(bomfile);
            bomArtifactPathMap.put(bomfile.getName(), bomFileName);
        }

        for (AgentProjectInfo project : projects) {
            //add dependencies from pom to the modules that didn't fail (or failed partially)
            String pomLocationPerProject = bomArtifactPathMap.get(project.getCoordinates().getArtifactId());
            if(pomLocationPerProject != null) {
                bomArtifactPathMap.remove(project.getCoordinates().getArtifactId());
                List<DependencyInfo> dependencyInfoList = pomParser.parseDependenciesFromPomXml(pomLocationPerProject);
                // making sure not to add duplication of already existing dependencies
                project.getDependencies().addAll(dependencyInfoList.stream().filter(dependencyInfo -> project.getDependencies().contains(dependencyInfo) == false).collect(Collectors.toList()));
            }
        }

        for (String artifactId : bomArtifactPathMap.keySet()) {
            for (BomFile missingProject : bomFileList) {
                //if project was not created due to failure add its dependencies
                if (artifactId.equals(missingProject.getName())) {
                    AgentProjectInfo projectInfo = new AgentProjectInfo();
                    projectInfo.setCoordinates(new Coordinates(missingProject.getGroupId(), missingProject.getName(), missingProject.getVersion()));
                    projectInfo.getDependencies().addAll(pomParser.parseDependenciesFromPomXml(bomArtifactPathMap.get(missingProject.getName())));
                    projects.add(projectInfo);
                    break;
                }
            }
        }
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
        return new String[]{Constants.PATTERN + POM_XML};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(POM_XML);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new HashSet<>();
    }

}