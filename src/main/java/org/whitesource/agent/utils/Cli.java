package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.IOException;
import java.util.List;

public class Cli {
    private final Logger logger = LoggerFactory.getLogger(Cli.class);

    public  List<String> runCmd(String rootDirectory, String[] params){
        try {
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = commandLineProcess.executeProcess();
            if (!commandLineProcess.isErrorInProcess()) {
                return lines;
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , params , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
        return null;
    }

    public String[] getCommandParams(String command, String param){
        if (DependencyCollector.isWindows()) {
            return new String[] {Constants.CMD, DependencyCollector.C_CHAR_WINDOWS, command, param};
        } else {
            return new String[] {command, param};
        }
    }
}
