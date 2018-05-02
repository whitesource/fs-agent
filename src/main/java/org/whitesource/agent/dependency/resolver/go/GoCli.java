package org.whitesource.agent.dependency.resolver.go;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;

class GoCli {
    private final Logger logger = LoggerFactory.getLogger(GoCli.class);

    private final String CMD = "cmd";
    private final String C_CHAR_WINDOWS = "/c";
    protected static final String GO_ENSURE = "ensure";
    protected static final String GO_INIT = "init";
    protected static final String GO_SAVE = "save";

    protected boolean runCmd(String rootDirectory, String[] params){
        try {
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , params , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return false;
    }

    protected String[] getGoCommandParams(String command, GoDependencyManager dependencyManager){
        if (DependencyCollector.isWindows()) {
            return new String[] {CMD, C_CHAR_WINDOWS, dependencyManager.getType(), command};
        } else {
            return new String[] {dependencyManager.getType(), command};
        }
    }
}