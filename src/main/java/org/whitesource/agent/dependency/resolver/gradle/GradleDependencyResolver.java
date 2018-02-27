package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    private static final String BUILD_GRADLE = "**/*build.gradle";
    private static final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleDependencyResolver.class);
    private static final String USER_HOME = "user.home";
    private static final String CMD = "cmd";
    private static final String C_Char_WINDOWS = "/c";
    private static final String GRADLE_PARAMS_TREE = "dependencies";
    private static final String GRADLE_COMMAND = "gradle";

    private String dotGradlePath;
    private GradleLinesParser gradleLinesParser;

    public GradleDependencyResolver(){
        super();
        gradleLinesParser = new GradleLinesParser();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles, String npmAccessToken) {
        List<DependencyInfo> dependencyInfos = collectDependencies(projectFolder);
        return null;
    }

    @Override
    protected Collection<String> getExcludes() {
        return null;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return null;
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
        if (StringUtils.isBlank(dotGradlePath)){
            this.dotGradlePath = getDotGradlePath();
        }
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        try {
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

    private String getDotGradlePath() {
        String currentUsersHomeDir = System.getProperty(USER_HOME);
        File dotGradle = Paths.get(currentUsersHomeDir, ".gradle", "caches","modules-2","files-2.1").toFile();

        if (dotGradle.exists()) {
            return dotGradle.getAbsolutePath();
        }
        logger.error("could not get .gradle path");
        return  null;
    }

    private String[] getLsCommandParams() {
        if (DependencyCollector.isWindows()) {
            return new String[] {CMD, C_Char_WINDOWS, GRADLE_COMMAND, GRADLE_PARAMS_TREE};
        } else {
            return new String[] {GRADLE_COMMAND, GRADLE_PARAMS_TREE};
        }
    }
}
