package org.whitesource.agent.dependency.resolver.gradle;

import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.go.GoDependencyResolver;

public enum GradleMvnCommand {
    COPY_DEPENDENCIES(Constants.COPY_DEPENDENCIES),
    DEPENDENCIES(Constants.DEPENDENCIES),
    ASSEMBLE(GradleCli.GRADLE_ASSEMBLE),
    LOCK(GoDependencyResolver.GRADLE_LOCK),
    PROJECTS(GradleCli.GRADLE_PROJECTS),
    GO_DEPENDENCIES(GoDependencyResolver.GO_DEPENDENCIES),
    GO_LOCK(GoDependencyResolver.GRADLE_GO_LOCK);

    private String value;

    GradleMvnCommand(String value) {
        this.value = value;
    }

    public String getCommand() {
        return value;
    }
}