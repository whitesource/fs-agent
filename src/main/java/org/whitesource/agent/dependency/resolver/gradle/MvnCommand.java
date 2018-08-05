package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.Constants;

public enum MvnCommand {
    DEPENDENCIES(Constants.DEPENDENCIES),
    ASSEMBLE(GradleCli.GRADLE_ASSEMBLE);

    MvnCommand(String value) {
    }
}