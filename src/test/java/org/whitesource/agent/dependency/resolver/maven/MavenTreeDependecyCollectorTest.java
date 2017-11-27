package org.whitesource.agent.dependency.resolver.maven;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import java.util.Collection;

public class MavenTreeDependecyCollectorTest {

    @Test
    public void shouldParseOutput() {

        String currentDirectory = System.getProperty("user.dir");
        MavenTreeDependencyCollector mavenTreeDependencyCollector = new MavenTreeDependencyCollector(false);

        Collection<DependencyInfo> deps = mavenTreeDependencyCollector.collectDependencies(currentDirectory);

        Assert.assertTrue(deps.size() > 0);
        deps.stream().allMatch(x->x.getDependencyType().equals(DependencyType.MAVEN));
    }
}
