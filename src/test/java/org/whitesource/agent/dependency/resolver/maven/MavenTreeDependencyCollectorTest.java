package org.whitesource.agent.dependency.resolver.maven;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MavenTreeDependencyCollectorTest {

    public static final String POM = ".pom";

    @Ignore
    @Test
    public void shouldParseOutput() {
        String currentDirectory = System.getProperty("user.dir");
        Collection<DependencyInfo> deps = testFsa(currentDirectory);

        Assert.assertTrue(deps.size() > 0);
        Assert.assertTrue(deps.stream().allMatch(x->x.getDependencyType().equals(DependencyType.MAVEN)));

        currentDirectory = TestHelper.FOLDER_WITH_MVN_PROJECTS;
        deps = testFsa(currentDirectory);

        List<DependencyInfo> pomDeps = deps.stream()
                .filter(x->x.getFilename().contains(POM))
                .collect(Collectors.toList());

        Assert.assertTrue(pomDeps.size() > 0);
    }

    private Collection<DependencyInfo> testFsa(String currentDirectory) {
        MavenTreeDependencyCollector mavenTreeDependencyCollector = new MavenTreeDependencyCollector(null, true, false,true);

        Collection<AgentProjectInfo> projects = mavenTreeDependencyCollector.collectDependencies(currentDirectory);

        Collection<DependencyInfo> deps = projects.stream().findFirst().get().getDependencies();

        return deps;
    }

    @Ignore
    @Test
    public void shouldFindPomDependencies() {
        String folderParent = TestHelper.FOLDER_WITH_MVN_PROJECTS;
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.MAVEN_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NUGET_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, "**/*.jar");
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);
        TestHelper.testDependencyResult(true, results);
    }
}
