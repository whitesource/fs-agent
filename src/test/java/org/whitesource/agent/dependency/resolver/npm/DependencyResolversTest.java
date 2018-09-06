package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.configuration.ResolverConfiguration;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * @author eugen.horovitz
 */
public class DependencyResolversTest {

    @Ignore
//    @Test
    public void shouldReturnDependenciesBower() {
        testBowerResolve(false);
    }

    @Ignore
//    @Test
    public void shouldReturnDependenciesTreeBower() {
        testBowerResolve(true);
    }

    @Ignore
//    @Test
    public void shouldReturnDependenciesNpm() {
        testNpmResolve(false);
    }


//    @Ignore
    @Test
    public void npmIgnoreSourceFilesTest() {
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\npm\\");
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderPath), "false");
        List<ResolutionResult> resultsISF = getResolutionResults(Arrays.asList(folderPath), "true");
        List<List> bothExcludes = TestHelper.getExcludesFromDependencyResult(results, resultsISF, DependencyType.NPM);
        String[] includes = new String[]{"**/*.jar", "**/*.js", "**/*.tgz"};
        Assert.assertFalse(TestHelper.checkResultOfScanFiles(folderPath, bothExcludes.get(0), bothExcludes.get(1), includes, DependencyType.NPM));
    }

    @Ignore
//    @Test
    public void shouldReturnDependenciesTreeNpm() {
        testNpmResolve(true);
    }

    @Ignore
//    @Test
    public void shouldResolvePackageJson() {
        String folderParent = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\npm\\");

        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderParent));

        DependencyInfo dependencyInfo = results.get(0).getResolvedProjects().keySet().stream().findFirst().get().getDependencies().stream().findFirst().get();
        Assert.assertNotNull(dependencyInfo.getArtifactId());
    }
    @Ignore
//    @Test
    public void testRegexInNpmResolver() {
        String problematicLine = " ├── UNMET PEER DEPENDENCY watchify@>=3 <4";
        NpmLsJsonDependencyCollector npmLsJsonDependencyCollector = new NpmLsJsonDependencyCollector(false, 60, false, false);
        Assert.assertEquals("watchify", npmLsJsonDependencyCollector.getTheNextPackageNameFromNpmLs(problematicLine));
    }

    private void testNpmResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_NPN_PROJECTS;
        List<ResolutionResult> results = getResolutionResults(Arrays.asList(folderParent));
        TestHelper.testDependencyResult(checkChildren, results);
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NPM_RUN_PRE_STEP, "true");

        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

    private List<ResolutionResult> getResolutionResults(List<String> pathsToScan,String isIgnoreSourceFiles) {
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.NPM_INCLUDE_DEV_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NPM_IGNORE_SOURCE_FILES,isIgnoreSourceFiles );
        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        return dependencyResolutionService.resolveDependencies(pathsToScan, new String[0]);
    }

    private void testBowerResolve(boolean checkChildren) {
        String folderParent = TestHelper.FOLDER_WITH_BOWER_PROJECTS;
        FSAConfigProperties props = new FSAConfigProperties();
        props.setProperty(ConfigPropertyKeys.BOWER_RESOLVE_DEPENDENCIES, "true");
        props.setProperty(ConfigPropertyKeys.BOWER_RUN_PRE_STEP, "true");
        props.setProperty(ConfigPropertyKeys.MAVEN_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "false");
        props.setProperty(ConfigPropertyKeys.NUGET_RESOLVE_DEPENDENCIES, "false");

        ResolverConfiguration resolverConfiguration = new FSAConfiguration(props).getResolver();

        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(resolverConfiguration);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);

        TestHelper.testDependencyResult(checkChildren, results);
    }
}