package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.Constants;

public enum GradleMvnCommand {
    DEPENDENCIES(Constants.DEPENDENCIES),
    ASSEMBLE(GradleCli.GRADLE_ASSEMBLE),
    LOCK(GradleCli.GRADLE_LOCK);

    GradleMvnCommand(String value) {
    }
}