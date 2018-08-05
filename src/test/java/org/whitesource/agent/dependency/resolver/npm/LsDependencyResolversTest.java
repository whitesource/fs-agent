package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.bower.BowerLsJsonDependencyCollector;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class LsDependencyResolversTest {
    private static String FOLDER_TO_TEST = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_NPN_PROJECTS);

    @Ignore
    @Test
    public void shouldReturnDependenciesTreeNpm() {
        AgentProjectInfo projectInfo = new NpmLsJsonDependencyCollector(false,
                60, false, false).collectDependencies(FOLDER_TO_TEST).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }

    @Ignore
    @Test
    public void shouldReturnDependenciesTreeBower() {
        String firstFolder = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_BOWER_PROJECTS);
        AgentProjectInfo projectInfo = new BowerLsJsonDependencyCollector(60).collectDependencies(firstFolder).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }
}