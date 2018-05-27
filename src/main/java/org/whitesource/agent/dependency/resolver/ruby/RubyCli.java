package org.whitesource.agent.dependency.resolver.ruby;

import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;

public class RubyCli extends Cli {

    @Override
    public String[] getCommandParams(String command, String param){
        String[] params = param.split(" ");
        if (DependencyCollector.isWindows()) {
            return new String[] {DependencyCollector.CMD, DependencyCollector.C_CHAR_WINDOWS, command, params[0], params[1]};
        } else {
            return new String[] {command, params[0], params[1]};
        }
    }
}
