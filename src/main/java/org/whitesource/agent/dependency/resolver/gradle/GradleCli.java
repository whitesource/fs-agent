package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;
import java.util.List;

class GradleCli extends Cli {
    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleCli.class);

    protected static final String GRADLE_ASSEMBLE = "assemble";
    private final String GRADLE_COMMAND = "gradle";
    private final String GRADLE_COMMAND_W_WINDOWS = "gradlew";
    private final String GRADLE_COMMAND_W_LINUX = "./gradlew";

    public List<String> runGradleCmd(String rootDirectory, String[] params) {
        try {
            // run gradle dependencies to get dependency tree
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                // in case gradle is not installed on the local machine, using 'gradlew' command, which uses local gradle wrapper
                setGradleCommandByEnv(params);
                commandLineProcess = new CommandLineProcess(rootDirectory, params);
                lines = commandLineProcess.executeProcess();
                if (!commandLineProcess.isErrorInProcess()) {
                    return lines;
                }
            } else {
                return lines;
            }
        } catch (IOException e) {
            if (StringUtils.isNotBlank(params[0]) && GRADLE_COMMAND.equals(params[0])) {
                setGradleCommandByEnv(params);
                return runGradleCmd(rootDirectory, params);
            } else {
                logger.warn("Error getting dependencies after running {} on {}, {}", params, rootDirectory, e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            }
        }
        return null;
    }

    // check if params contains gradle command and change it to gradlew command
    private void setGradleCommandByEnv(String[] params) {
        for (int i = 0; i < params.length; i++) {
            if (params[i].equals(GRADLE_COMMAND)) {
                if (DependencyCollector.isWindows()) {
                    params[i] = GRADLE_COMMAND_W_WINDOWS;
                } else {
                    params[i] = GRADLE_COMMAND_W_LINUX;
                }
                break;
            }
        }
    }

    protected String[] getGradleCommandParams(MvnCommand command) {
        return super.getCommandParams(GRADLE_COMMAND, command.name());
    }
}

enum MvnCommand {
    DEPENDENCIES(Constants.DEPENDENCIES),
    ASSEMBLE(GradleCli.GRADLE_ASSEMBLE);

    MvnCommand(String value) {
    }
}
