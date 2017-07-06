import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.fs.Main;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test class for npm-filter
 *
 * @author eugen.horovitz
 */
public class FileSystemAgentTest {

    /* --- Tests --- */

    @Test
    public void shouldFilterOutJsFiles() {
        Properties props = TestHelper.getPropertiesFromFile();
        List<String> dirs = Arrays.asList(TestHelper.FOLDER_TO_TEST);

        Stream<DependencyInfo> distinctDependencies = getDependenciesWithFilter(dirs, props);
        List<String> jsFiles = distinctDependencies.filter(x -> x.getArtifactId() != null && x.getArtifactId().endsWith(".js")).map(x -> x.getArtifactId()).collect(Collectors.toList());
        Assert.assertEquals(0, jsFiles.size());
    }

    //Should reproduce this scenario where there is a optional dependency and not a regular dependency - should be flagged
    //@Test
    //public void testOptionalDependenciesShouldSentWithFlag() {
    //    Properties props = getPropertiesFromFile();
    //    List<String> dirs = Arrays.asList(TestHelper.FOLDER_TO_TEST);
    //    Stream<DependencyInfo> distinctDependencies1 = getDependenciesWithFilter(dirs, props);
    //    Optional<DependencyInfo> found = distinctDependencies1.filter(x -> x.getArtifactId().contains("hello")).findAny();
    //    Assert.assertTrue(found.isPresent());
    //    Assert.assertTrue(found.get().getOptional());
    //}

    @Test
    public void shouldBeTheSameResultsAsNpmLs() {
        Properties props = TestHelper.getPropertiesFromFile();
        File dir = new File("C:\\Users\\eugen\\WebstormProjects\\good2");
        Arrays.stream(dir.listFiles()).forEach((startDirectory) ->
        {
            List<String> dirs = Arrays.asList(startDirectory.getPath());
            Stream<DependencyInfo> dependenciesOrig = getDependenciesWithFilter(dirs, props);

            Stream<String> dependencies = dependenciesOrig.map(dependency -> TestHelper.getShortNameByTgz(dependency));
            Stream<String> distinctDependenciesAutoResolver = dependencies.distinct().sorted();
            Stream<String> distinctDependenciesNpmLs = TestHelper.getDependenciesWithNpm(dirs);

            List<String> autoResolverResults = distinctDependenciesAutoResolver.collect(Collectors.toList());
            List<String> npmLsResults = distinctDependenciesNpmLs.collect(Collectors.toList());

            //the number of package json files should be the same as the 'npm ls'
            autoResolverResults.removeAll(npmLsResults);
            Assert.assertTrue(autoResolverResults.size() == 0);
        });
    }

    @Test
    public void shouldBeTheSameCountAsNpmLs() {
        File folderParent = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        NpmDependencyResolver npmDependencyResolver = new NpmDependencyResolver();

        FilesScanner fs = new FilesScanner();

        Map<String, String[]> map = fs.findAllFiles(Arrays.asList(folderParent.getPath()), new NpmDependencyResolver().getBomPattern(), new String[0]);
        map.forEach((folder, files) -> {
            ResolutionResult resolutionResult = npmDependencyResolver.resolveDependencies(folder, folder,
                    Arrays.stream(files).map(file -> Paths.get(folder, file).toString()).collect(Collectors.toList()));

            Assert.assertTrue(resolutionResult.getResolvedDependencies().size() > 0);
        });
    }

    @Test
    public void testPackageJsonOnly() {
        File dir10 = new File("C:\\Users\\eugen\\WebstormProjects\\top10\\");

        Arrays.stream(dir10.listFiles()).forEach(directory -> {
            FilesScanner fs = new FilesScanner();

            Map<String, String[]> map = fs.findAllFiles(Arrays.asList(directory.getPath()), new NpmDependencyResolver().getBomPattern(), new String[0]);
            map.forEach((folder, fileFiltered) -> {
                Assert.assertTrue(fileFiltered.length > 0);
            });
        });
    }

    @Test
    public void shouldRunMainOnFolder() {
        //File directory = new File("C:\\Users\\eugen\\WebstormProjects\\cody_with_multi_changed");
        //runMainOnDir(directory);
        File directory = new File("C:\\Users\\eugen\\WebstormProjects\\good");
        Arrays.stream(directory.listFiles()).forEach(dir -> runMainOnDir(dir));
    }

    /* --- Private methods --- */

    private void runMainOnDir(File directory) {
        String[] args = ("-d " + directory.getPath() + " -project " + "b" + directory.getName()).split(" ");
        int result = Main.execute(args);
        Assert.assertEquals(result, 0);
    }

    private Stream<DependencyInfo> getDependenciesWithFilter(List<String> dirs, Properties p) {
        FileSystemAgentTesting f = new FileSystemAgentTesting(p, dirs);

        Collection<AgentProjectInfo> projects = f.createProjects();

        Stream<DependencyInfo> dependenciesOrig = projects.stream().map(x -> x.getDependencies()).flatMap(d -> d.stream());
        return dependenciesOrig;
    }

    // Helper class
    private class FileSystemAgentTesting extends FileSystemAgent {
        public FileSystemAgentTesting(Properties config, List<String> dependencyDirs) {
            super(config, dependencyDirs);
        }

        @Override
        public Collection<AgentProjectInfo> createProjects() {
            return super.createProjects();
        }
    }
}