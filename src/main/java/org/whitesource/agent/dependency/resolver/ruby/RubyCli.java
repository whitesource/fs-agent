package org.whitesource.agent.dependency.resolver.ruby;

import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;

public class RubyCli extends Cli {
// TODO - this class can be removed (the functionality here is incorporated into this method in Cli class)
    @Override
    public String[] getCommandParams(String command, String param){
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
