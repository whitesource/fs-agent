package org.whitesource.agent.dependency.resolver.sbt;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SbtDependencyResolverTest {

    private SbtDependencyResolver sbtDependencyResolver;
//    @Before
    public void setUp() throws Exception {
        sbtDependencyResolver = new SbtDependencyResolver(true, true, true , "");
    }

    @Ignore
//    @Test
    public void resolveDependencies() {
        File buildSbtFile = TestHelper.getFileFromResources("resolver/sbt/build.sbt");
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\sbt\\");
        Set<String> bomFiles = Stream.of(buildSbtFile.toString()).collect(Collectors.toSet());
        ResolutionResult resolutionResult = sbtDependencyResolver.resolveDependencies("", folderPath, bomFiles);
        Collection<DependencyInfo> dependencies = resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies();
        Assert.assertTrue(dependencies.size() == 3);
    }

    //    @Ignore
    @Test
    public void sbtIgnoreSourceFilesTest() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\sbt\\");
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderPath), "false");
        List<ResolutionResult> resultsISF = getResolutionResults(Arrays.asList(folderPath), "true");
        List<List> bothExcludes = TestHelper.getExcludesFromDependencyResult(results, resultsISF, DependencyType.MAVEN);
        String[] includes = new String[]{"**/*.sbt", "**/*.scala"};
        Assert.assertFalse(TestHelper.checkResultOfScanFiles(folderPath, bothExcludes.get(0), bothExcludes.get(1), includes, DependencyType.MAVEN));
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan,String isIgnoreSourceFiles) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.SBT_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.SBT_IGNORE_SOURCE_FILES,isIgnoreSourceFiles );
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

}