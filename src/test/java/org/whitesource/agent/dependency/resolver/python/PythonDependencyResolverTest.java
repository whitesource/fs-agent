package org.whitesource.agent.dependency.resolver.python;

import org.junit.Assert;
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
import org.whitesource.fs.Main;
import org.whitesource.fs.ProjectsDetails;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PythonDependencyResolverTest {

    // requires admin rights to install dependencies in linux
    @Ignore
    @Test
    public void shouldFindDependecies() {
        File setupPyFile = TestHelper.getFileFromResources("resolver/python/sample/test.py");

        PythonDependencyResolver pythonDependencyResolver = new PythonDependencyResolver("python.exe", "pip3.exe", false, false, false, new String[]{Constants.PYTHON_REQUIREMENTS}, true, true, true, true);
        ResolutionResult projectsDetails = pythonDependencyResolver.resolveDependencies(setupPyFile.getParentFile().getParent(), setupPyFile.getParent(), Stream.of(setupPyFile.toString()).collect(Collectors.toSet()));

        Assert.assertNotNull(projectsDetails);
        Assert.assertTrue(projectsDetails.getResolvedProjects().size() == 1);
        Collection<DependencyInfo> dependecies = projectsDetails.getResolvedProjects().keySet().stream().findFirst().get().getDependencies();
        Assert.assertEquals(dependecies.stream().filter(x -> x.getDependencyType() != null && x.getDependencyType().equals(DependencyType.PYTHON)).count(), 6);
    }

    // requires admin rights to install dependencies in linux
    @Ignore
    @Test
    public void shouldWorkEndToEnd() {
        Main main = new Main();

        String config = "{\n" +
                "\"agent\": {\n" +
                "\t\"includes\":[\"**/*.py\"]\n" +
                "},\n" +
                "\"scm\": {\n" +
                "\"scm.type\":\"git\",\n" +
                "\"scm.user\":\"\",\n" +
                "\"scm.pass\":\"\",\n" +
                "\"scm.url\":\"https://github.com/zalandoresearch/fashion-mnist\",\n" +
                "\"scm.type\":\"git\"\n" +
                "},\n" +
                "\"request\":{\n" +
                "\t\"projectName\":\"aaaa_elad-web\",\n" +
                "\t\"apiKey\":\"api-token\"\n" +
                "}\n" +
                "}";

        FSAConfiguration fsaConfiguration = ConfigurationSerializer.getFromString(config, FSAConfiguration.class, false);
        fsaConfiguration.validate();

        ProjectsDetails projectsDetails = main.scanAndSend(fsaConfiguration, false);

        Assert.assertTrue(projectsDetails.getProjects().size() == 1);

        Collection<DependencyInfo> dependecies = projectsDetails.getProjects().stream().findFirst().get().getDependencies();
        Assert.assertTrue(dependecies.stream().filter(x -> x.getDependencyType() != null && x.getDependencyType().equals(DependencyType.PYTHON)).count() == 2);
    }

    //    @Ignore
    @Test
    public void pythonIgnoreSourceFilesTest() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\python\\sample\\");
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderPath), "false");
        List<ResolutionResult> resultsISF = getResolutionResults(Arrays.asList(folderPath), "true");
        List<List> bothExcludes = TestHelper.getExcludesFromDependencyResult(results, resultsISF, DependencyType.PYTHON);
        String[] includes = new String[]{"**/*.py"};
        Assert.assertFalse(TestHelper.checkResultOfScanFiles(folderPath, bothExcludes.get(0), bothExcludes.get(1), includes, DependencyType.PYTHON));
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan,String isIgnoreSourceFiles) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.PYTHON_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.PYTHON_IGNORE_SOURCE_FILES, isIgnoreSourceFiles );
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

}
