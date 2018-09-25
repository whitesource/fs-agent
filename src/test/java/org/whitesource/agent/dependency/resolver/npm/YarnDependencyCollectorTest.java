package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

public class YarnDependencyCollectorTest {

    private YarnDependencyCollector yarnDependencyCollector;

    @Before
    public void setup() {
        yarnDependencyCollector = new YarnDependencyCollector(false, 10000, true, true);
    }

    @Ignore
    @Test
    public void collectDependencies() {
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\yarn\\");
        Collection<AgentProjectInfo> agentProjectInfos = yarnDependencyCollector.collectDependencies(folderPath);
        Assert.assertTrue(agentProjectInfos.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()).size() == 4);
    }
}