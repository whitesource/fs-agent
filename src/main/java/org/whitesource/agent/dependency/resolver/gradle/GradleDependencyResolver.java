package org.whitesource.agent.dependency.resolver.gradle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;
import java.util.*;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleDependencyResolver.class);
    private static final String USER_HOME = "user.home";
    private static final String CMD = "cmd";
    private static final String C_Char_WINDOWS = "/c";
    private static final String GRADLE_PARAMS_TREE = "dependencies";
    private static final String GRADLE_COMMAND = "gradle";
    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle",".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");


    private GradleLinesParser gradleLinesParser;

    public GradleDependencyResolver(){
        super();
        gradleLinesParser = new GradleLinesParser();
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
        try {
            // run gradle dependencies to get dependency tree
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, getLsCommandParams());
            List<String> lines = commandLineProcess.executeProcess();
            if (!commandLineProcess.isErrorInProcess()) {
                dependencyInfos.addAll(gradleLinesParser.parseLines(lines));
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , getLsCommandParams() , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return dependencyInfos;
    }

    private String[] getLsCommandParams() {
        if (DependencyCollector.isWindows()) {
            return new String[] {CMD, C_Char_WINDOWS, GRADLE_COMMAND, GRADLE_PARAMS_TREE};
        } else {
            return new String[] {GRADLE_COMMAND, GRADLE_PARAMS_TREE};
        }
    }
}
