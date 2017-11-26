package org.whitesource.agent.dependency.resolver.maven;

import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;

import java.util.Collection;

public class MavenTreeDependecyCollectorTest {

    @Test
    public void shouldParseOutput(){
        MavenTreeDependencyCollector mavenTreeDependencyCollector = new MavenTreeDependencyCollector(false);

        Collection<DependencyInfo> deps = mavenTreeDependencyCollector.collectDependencies("C:\\Users\\eugen\\Repositories\\app\\wss-parent");
    }
}
