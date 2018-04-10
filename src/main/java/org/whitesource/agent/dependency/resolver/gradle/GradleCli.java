package org.whitesource.agent.dependency.resolver.gradle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;
import java.util.List;

class GradleCli {
    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleCli.class);

    private final String CMD = "cmd";
    private final String C_CHAR_WINDOWS = "/c";
    protected static final String GRADLE_PARAMS_TREE = "dependencies";
    protected static final String GRADLE_ASSEMBLE = "assemble";
    private final String GRADLE_COMMAND = "gradle";
    private final String GRADLE_COMMAND_W = "gradlew";

    protected List<String> runCmd(String rootDirectory, String[] params){
        try {
            // run gradle dependencies to get dependency tree
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                // in case gradle is not installed on the local machine, using 'gradlew' command, which uses local gradle wrapper
                for (int i = 0; i < params.length; i++){
                    if (params[i].equals(GRADLE_COMMAND)){
                        params[i] = GRADLE_COMMAND_W;
                        break;
                    }
                }
                commandLineProcess = new CommandLineProcess(rootDirectory,params);
                lines = commandLineProcess.executeProcess();
                if (!commandLineProcess.isErrorInProcess()){
                    return lines;
                }
            } else {
                return lines;
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , params , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return null;
    }

    protected String[] getGradleCommandParams(MvnCommand command){
        if (DependencyCollector.isWindows()) {
            return new String[] {CMD, C_CHAR_WINDOWS, GRADLE_COMMAND, command.name()};
        } else {
            return new String[] {GRADLE_COMMAND, GRADLE_PARAMS_TREE};
        }
    }
}

    enum MvnCommand {
        DEPENDENCIES(GradleCli.GRADLE_PARAMS_TREE),
        ASSEMBLE(GradleCli.GRADLE_ASSEMBLE);

        MvnCommand(String value){

        }
    }
