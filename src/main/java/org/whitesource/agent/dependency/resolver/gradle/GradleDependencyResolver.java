package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String JAR_EXTENSION = ".jar";

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean dependenciesOnly;
    private boolean gradleAggregateModules;

    public GradleDependencyResolver(boolean runAssembleCommand, boolean dependenciesOnly, boolean gradleAggregateModules){
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand);
        gradleCli = new GradleCli();
        topLevelFoldersNames = new ArrayList<>();
        this.dependenciesOnly = dependenciesOnly;
        this.gradleAggregateModules = gradleAggregateModules;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        // each bom-file represents a module - identify its folder and scan it using 'gradle dependencies'
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        for (String bomFile : bomFiles){
            String bomFileFolder = new File(bomFile).getParent();
            List<DependencyInfo> dependencies = collectDependencies(bomFileFolder);
            if (dependencies.size() > 0) {
                AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
                agentProjectInfo.getDependencies().addAll(dependencies);
                if (!gradleAggregateModules) {
                    Coordinates coordinates = new Coordinates();
                    File bomFolder = new File(new File(bomFile).getParent());
                    coordinates.setArtifactId(bomFolder.getName());
                    agentProjectInfo.setCoordinates(coordinates);
                }
                projects.add(agentProjectInfo);
            }
        }

        Set<String> excludes = new HashSet<>();
        Map<AgentProjectInfo, Path> projectInfoPathMap = projects.stream().collect(Collectors.toMap(projectInfo -> projectInfo, projectInfo -> {
            if (dependenciesOnly) {
                excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder.toString(), GRADLE_SCRIPT_EXTENSION, null));
            }
            return Paths.get(topLevelFolder);
        }));

        ResolutionResult resolutionResult;
        if (!gradleAggregateModules) {
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
        for (String topLeverFolderName : topLevelFoldersNames) {
            excludes.add(GLOB_PATTERN + topLeverFolderName + JAR_EXTENSION);
        }
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return GRADLE_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GRADLE;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.GRADLE.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{BUILD_GRADLE};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        List<String> lines = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(MvnCommand.DEPENDENCIES));
        if (lines != null) {
            dependencyInfos.addAll(gradleLinesParser.parseLines(lines, rootDirectory));
        }
        return dependencyInfos;
    }
}
