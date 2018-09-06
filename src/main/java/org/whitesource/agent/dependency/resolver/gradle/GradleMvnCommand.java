package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.Constants;

public enum GradleMvnCommand {
    COPY_DEPENDENCIES(Constants.COPY_DEPENDENCIES),
    DEPENDENCIES(Constants.DEPENDENCIES),
    ASSEMBLE(GradleCli.GRADLE_ASSEMBLE),
    LOCK(GradleCli.GRADLE_LOCK),
    PROJECTS(GradleCli.GRADLE_PROJECTS);

    private String value;

    GradleMvnCommand(String value) {
        this.value = value;
    }

    public String getCommand() {
        return value;
    }
}