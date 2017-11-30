package org.whitesource.agent.dependency.resolver.maven;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import java.util.Collection;

public class MavenTreeDependencyCollectorTest {

    @Test
    public void shouldParseOutput() {

        String currentDirectory = System.getProperty("user.dir");

        MavenTreeDependencyCollector mavenTreeDependencyCollector = new MavenTreeDependencyCollector(false);

        Collection<AgentProjectInfo> projects = mavenTreeDependencyCollector.collectDependencies(currentDirectory);

        Collection<DependencyInfo> deps = projects.stream().findFirst().get().getDependencies();
        Assert.assertTrue(deps.size() > 0);
        deps.stream().allMatch(x->x.getDependencyType().equals(DependencyType.MAVEN));
    }
}
