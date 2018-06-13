package org.whitesource.agent.dependency.resolver.sbt;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.Collection;

import static org.junit.Assert.*;

public class SbtDependencyResolverTest {

    private SbtDependencyResolver sbtDependencyResolver;
    @Before
    public void setUp() throws Exception {
        sbtDependencyResolver = new SbtDependencyResolver();
    }

    @Test
    public void resolveDependencies() {
        ResolutionResult resolutionResult = sbtDependencyResolver.resolveDependencies("", "C:\\Users\\ErezHuberman\\Documents\\sbt\\hello-world", null);
        Collection<DependencyInfo> dependencies = resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies();
        Assert.assertTrue(dependencies.size() == 95);
    }
}