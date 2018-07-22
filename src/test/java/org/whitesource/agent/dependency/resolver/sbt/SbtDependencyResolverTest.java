package org.whitesource.agent.dependency.resolver.sbt;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class SbtDependencyResolverTest {

    private SbtDependencyResolver sbtDependencyResolver;
    @Before
    public void setUp() throws Exception {
        sbtDependencyResolver = new SbtDependencyResolver(true, true);
    }

    @Test
    public void resolveDependencies() {
        File buildSbtFile = TestHelper.getFileFromResources("resolver/sbt/build.sbt");
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\sbt\\");
        Set<String> bomFiles = Stream.of(buildSbtFile.toString()).collect(Collectors.toSet());
        ResolutionResult resolutionResult = sbtDependencyResolver.resolveDependencies("", folderPath, bomFiles);
        Collection<DependencyInfo> dependencies = resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies();
        Assert.assertTrue(dependencies.size() == 3);
    }
}