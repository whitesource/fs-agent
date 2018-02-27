package org.whitesource.agent.dependency.resolver.gradle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.Iterator;

public class GradleDependencyResolverTest {

    GradleDependencyResolver gradleDependencyResolver;

    @Before
    public void setUp() throws Exception {
        gradleDependencyResolver = new GradleDependencyResolver();
    }

    @Test
    public void resolveDependencies() {
        ResolutionResult resolutionResult = gradleDependencyResolver.resolveDependencies("C:\\Users\\ErezHuberman\\Documents\\gradle-build-scan-quickstart-master", "C:\\Users\\ErezHuberman\\Documents\\gradle-build-scan-quickstart-master", null, null);

        Assert.assertTrue(resolutionResult.getDependencyType() == DependencyType.GRADLE);
        AgentProjectInfo projectInfo = resolutionResult.getResolvedProjects().keySet().iterator().next();
        Iterator iterator = projectInfo.getDependencies().iterator();
        DependencyInfo guavaInfo = (DependencyInfo) iterator.next();
        Assert.assertTrue(guavaInfo.getVersion().equals("23.0"));
        DependencyInfo isUrlInfo = (DependencyInfo) iterator.next();
        Assert.assertTrue(isUrlInfo.getChildren().size() == 2);

    }

    @Test
    public void resolveDependencies2() {
        ResolutionResult resolutionResult = gradleDependencyResolver.resolveDependencies("C:\\Users\\ErezHuberman\\Documents\\GitHub\\gradle-plugin", "C:\\Users\\ErezHuberman\\Documents\\GitHub\\gradle-plugin", null, null);
        AgentProjectInfo projectInfo = resolutionResult.getResolvedProjects().keySet().iterator().next();
        Iterator iterator = projectInfo.getDependencies().iterator();
        DependencyInfo wssAgentApiClient = (DependencyInfo) iterator.next();
        Assert.assertTrue(wssAgentApiClient.getVersion().equals("2.3.8"));
        DependencyInfo wssAgentReport = (DependencyInfo) iterator.next();
        Assert.assertTrue(wssAgentReport.getChildren().size() == 4);
        Assert.assertTrue(wssAgentReport.getChildren().iterator().next().getVersion().equals("2.3.8"));
    }
}