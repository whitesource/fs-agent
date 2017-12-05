package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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

    private void testNpmResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_NPN_PROJECTS;

        Properties props = new Properties();
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, "false");

        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(props);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);

        testDependencyResult(checkChildren, results);
    }

    private void testDependencyResult(boolean checkChildren, List<ResolutionResult> results) {
        results.forEach(resolutionResult -> {
            Assert.assertTrue(resolutionResult.getResolvedProjects().size() > 0);
            if (!checkChildren) {
                return;
            }
            List<DependencyInfo> dependencyInformation = resolutionResult
                    .getResolvedProjects().keySet().stream().findFirst().get().getDependencies().stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
            Assert.assertTrue(dependencyInformation.size() > 0);
        });
    }

    private void testBowerResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_BOWER_PROJECTS;
        Properties props = new Properties();
        props.setProperty(ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, "true");
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(props);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);

        testDependencyResult(checkChildren, results);
    }
}