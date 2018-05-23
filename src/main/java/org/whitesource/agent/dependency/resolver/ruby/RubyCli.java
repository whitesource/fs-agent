package org.whitesource.agent.dependency.resolver.ruby;

import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;

public class RubyCli extends Cli {

    @Override
    public String[] getCommandParams(String command, String param){
        String[] params = param.split(" ");
        String[] output;
        if (DependencyCollector.isWindows()) {
            output = new String[3 + params.length];
            output[0] = DependencyCollector.CMD;
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
