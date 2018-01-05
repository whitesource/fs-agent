package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * @author eugen.horovitz
 */
public class DependencyResolversTest {
    @Test
    public void shouldReturnDependenciesBower() {
        testBowerResolve(false);
    }

    @Test
    public void shouldReturnDependenciesTreeBower() {
        testBowerResolve(true);
    }

    @Test
    public void shouldReturnDependenciesNpm() {
        testNpmResolve(false);
    }

    @Test
    public void shouldReturnDependenciesTreeNpm() {
        testNpmResolve(true);
    }

    @Test
    public void shouldResolvePackageJson() {
        String folderParent = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\npm\\");

        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderParent));

        DependencyInfo dependencyInfo = results.get(0).getResolvedProjects().keySet().stream().findFirst().get().getDependencies().stream().findFirst().get();
        Assert.assertNotNull(dependencyInfo.getArtifactId());
    }

    private void testNpmResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_NPN_PROJECTS;

        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderParent));

        TestHelper.testDependencyResult(checkChildren, results);
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan) {
        Properties props = new Properties();
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NPM_RUN_PRE_STEP, "true");

        ResolverConfiguration resolverConfiguration = new ResolverConfiguration(props);
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

    private void testBowerResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_BOWER_PROJECTS;
        Properties props = new Properties();
        props.setProperty(ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.BOWER_RUN_PRE_STEP, "true");
        props.setProperty(ConfigPropertyKeys.MAVEN_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NUGET_RESOLVE_DEPENDENCIES, "false");

        ResolverConfiguration resolverConfiguration = new ResolverConfiguration(props);

        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);

        TestHelper.testDependencyResult(checkChildren, results);
    }
}