package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.*;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");
    private static final String FILE_SEPARATOR = "file.separator";
    private static final String JAR_EXTENSION = ".jar";

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;

    private ArrayList<String> topLevelFoldersNames;

    public GradleDependencyResolver(boolean runAssembleCommand){
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand);
        gradleCli = new GradleCli();
        topLevelFoldersNames = new ArrayList<>();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        topLevelFoldersNames.add(topLevelFolder.substring(topLevelFolder.lastIndexOf(System.getProperty(FILE_SEPARATOR)) + 1));
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        for (String topLeverFolderName : topLevelFoldersNames) {
            excludes.add("**/" + topLeverFolderName + JAR_EXTENSION);
        }
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return GRADLE_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GRADLE;
    }

    @Override
    protected String getBomPattern() {
        return BUILD_GRADLE;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        List<String> lines = gradleCli.runCmd(rootDirectory, gradleCli.getGradleCommandParams(MvnCommand.DEPENDENCIES));
        if (lines != null) {
            dependencyInfos.addAll(gradleLinesParser.parseLines(lines, rootDirectory));
        }
        return dependencyInfos;
    }
}
