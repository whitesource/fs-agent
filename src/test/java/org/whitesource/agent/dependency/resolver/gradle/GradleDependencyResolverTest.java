package org.whitesource.agent.dependency.resolver.gradle;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class GradleDependencyResolverTest {

    //    @Ignore
    @Test
    public void gradleIgnoreSourceFilesTest() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\gradle\\");
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderPath), "false");
        List<ResolutionResult> resultsISF = getResolutionResults(Arrays.asList(folderPath), "true");
        List<List> bothExcludes = TestHelper.getExcludesFromDependencyResult(results, resultsISF, DependencyType.GRADLE);
        String[] includes = new String[]{"**/*.jar", "**/*.java"};
        Assert.assertFalse(TestHelper.checkResultOfScanFiles(folderPath, bothExcludes.get(0), bothExcludes.get(1), includes, DependencyType.GRADLE));
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan,String isIgnoreSourceFiles) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.GRADLE_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.GRADLE_IGNORE_SOURCE_FILES, isIgnoreSourceFiles );
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

}