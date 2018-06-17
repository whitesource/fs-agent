package org.whitesource.agent.dependency.resolver.sbt;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.nio.file.Paths;
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
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\sbt\\");
        ResolutionResult resolutionResult = sbtDependencyResolver.resolveDependencies("", folderPath, null);
        Collection<DependencyInfo> dependencies = resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies();
        Assert.assertTrue(dependencies.size() == 3);
    }
}