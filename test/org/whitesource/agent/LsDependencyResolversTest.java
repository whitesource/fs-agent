package org.whitesource.agent;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.dependency.resolver.bower.BowerLsJsonDependencyCollector;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class LsDependencyResolversTest {
    private static String FOLDER_TO_TEST = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_NPN_PROJECTS);

    @Test
    public void shouldReturnDependenciesTreeNpm() {
        Collection<DependencyInfo> dependencies = new NpmLsJsonDependencyCollector(false).collectDependencies(FOLDER_TO_TEST);
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }

    @Test
    public void shouldReturnDependenciesTreeBower() {
        String firstFolder = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_BOWER_PROJECTS);
        Collection<DependencyInfo> dependencies = new BowerLsJsonDependencyCollector().collectDependencies(firstFolder);
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }
}