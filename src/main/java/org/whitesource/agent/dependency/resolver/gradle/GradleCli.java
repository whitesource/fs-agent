package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;
import java.util.List;

public class GradleCli extends Cli {
    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleCli.class);

    protected static final String GRADLE_ASSEMBLE = "assemble";
    protected static final String GRADLE_PROJECTS = "projects";
    private final String GRADLE_COMMAND = "gradle";
    private final String GRADLE_COMMAND_W_WINDOWS = "gradlew";
    private final String GRADLE_COMMAND_W_LINUX = "./gradlew";

    private String topLevelFolderGradlew = null;

    private String preferredEnvironment;

    public GradleCli(String preferredEnvironment) {
        super();
        this.preferredEnvironment = preferredEnvironment;
    }

    public List<String> runGradleCmd(String rootDirectory, String[] params, boolean firstTime) {
        try {
            // run gradle dependencies to get dependency tree
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                // in case gradle is not installed on the local machine, using 'gradlew' command, which uses local gradle wrapper
                this.preferredEnvironment = this.preferredEnvironment.equals(Constants.GRADLE_WRAPPER) ? Constants.GRADLE : Constants.GRADLE_WRAPPER;
                params = getGradleCommandParams(GradleMvnCommand.DEPENDENCIES);
                commandLineProcess = new CommandLineProcess(rootDirectory, params);
                lines = commandLineProcess.executeProcess();
                if (!commandLineProcess.isErrorInProcess()) {
                    return lines;
                }
            } else {
                return lines;
            }
        } catch (IOException e) {
            if (firstTime && StringUtils.isNotBlank(params[0]) && params[0].contains(GRADLE_COMMAND)) {
                this.preferredEnvironment = this.preferredEnvironment.equals(Constants.GRADLE_WRAPPER) ? Constants.GRADLE : Constants.GRADLE_WRAPPER;
                params = getGradleCommandParams(GradleMvnCommand.DEPENDENCIES);
                // calling 'runGradleCmd' recursively only once, for otherwise there will be a stack-over-flow error
                return runGradleCmd(rootDirectory, params, false);
            } else {
                logger.warn("Error getting results after running Gradle command {} on {}, {}", params, rootDirectory, e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            }
        }
        return null;
    }

    public String[] getGradleCommandParams(GradleMvnCommand command) {
        String gradleCommand;
        // WSE-753 - use the default gradle environment, set from the config file
        if (preferredEnvironment.equals(Constants.GRADLE_WRAPPER)) {
            if (this.topLevelFolderGradlew != null) {
                gradleCommand = this.topLevelFolderGradlew + Constants.FORWARD_SLASH + GRADLE_COMMAND_W_WINDOWS;
            } else {
                gradleCommand = DependencyCollector.isWindows() ? GRADLE_COMMAND_W_WINDOWS : GRADLE_COMMAND_W_LINUX;
            }
        } else {
            gradleCommand = GRADLE_COMMAND;
        }
        return super.getCommandParams(gradleCommand, command.getCommand());
    }

    public void setTopLevelFolderGradlew(String topLevelFolderGradlew) {
        this.topLevelFolderGradlew = topLevelFolderGradlew;
    }
}