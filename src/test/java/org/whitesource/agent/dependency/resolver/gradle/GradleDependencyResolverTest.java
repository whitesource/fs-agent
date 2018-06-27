package org.whitesource.agent.dependency.resolver.gradle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.nio.file.Paths;
import java.util.Iterator;

public class GradleDependencyResolverTest {

    GradleDependencyResolver gradleDependencyResolver;

    @Before
    public void setUp() throws Exception {
        gradleDependencyResolver = new GradleDependencyResolver(true, true, true);
    }

    @Test
    public void resolveDependencies() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(
                "\\src\\test\\resources\\resolver\\gradle\\sample\\");
        ResolutionResult resolutionResult = gradleDependencyResolver.resolveDependencies(folderPath, folderPath, null);

        Assert.assertTrue(resolutionResult.getDependencyType() == DependencyType.GRADLE);
        AgentProjectInfo projectInfo = resolutionResult.getResolvedProjects().keySet().iterator().next();
        Iterator iterator = projectInfo.getDependencies().iterator();
        DependencyInfo guavaInfo = (DependencyInfo) iterator.next();
        Assert.assertTrue(guavaInfo.getVersion().equals("23.0"));
        DependencyInfo isUrlInfo = (DependencyInfo) iterator.next();
        Assert.assertTrue(isUrlInfo.getChildren().size() == 1);

    }
}