package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.*;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");

    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;

    public GradleDependencyResolver(boolean runAssembleCommand){
        super();
        gradleLinesParser = new GradleLinesParser(runAssembleCommand);
        gradleCli = new GradleCli();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles, String npmAccessToken) {
        List<DependencyInfo> dependencies = collectDependencies(projectFolder);

        return new ResolutionResult(dependencies, new LinkedList<>(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
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
