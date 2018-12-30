package org.whitesource.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.IOException;
import java.util.LinkedList;
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
        return new LinkedList<>();
    }

    public String[] getCommandParams(String command, String param){
        if (param.contains(Constants.WHITESPACE)){
            return getCommandParamsArray(command, param);
        }
        if (DependencyCollector.isWindows()) {
            return new String[] {Constants.CMD, DependencyCollector.C_CHAR_WINDOWS, command, param};
        }
        return new String[] {command, param};
    }

    private String[] getCommandParamsArray(String command, String param){
        String[] params = param.split(Constants.WHITESPACE);
        String[] output;
        if (DependencyCollector.isWindows()) {
            output = new String[3 + params.length];
            output[0] = Constants.CMD;
            output[1] = DependencyCollector.C_CHAR_WINDOWS;
            output[2] = command;
            for (int i = 0; i < params.length; i++){
                output[i + 3] = params[i];
            }
        } else {
            output = new String[1 + params.length];
            output[0] = command;
            for (int i = 0; i < params.length; i++){
                output[i + 1] = params[i];
            }
        }
        return output;
    }
}
