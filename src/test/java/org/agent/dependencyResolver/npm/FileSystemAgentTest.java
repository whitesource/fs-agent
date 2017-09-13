package org.agent.dependencyResolver.npm;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.ResolvedFolder;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.fs.Main;
import org.whitesource.fs.StatusCode;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.whitesource.agent.ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY;
import static org.whitesource.agent.ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY;

/**
 * Test class for npm-filter
 *
 * @author eugen.horovitz
 */
public class FileSystemAgentTest {

    /* --- Tests --- */

    @Test
    public void shouldEnrichAllDependenciesWithSha1() {
        Properties props = TestHelper.getPropertiesFromFile();
        List<String> dirs = Arrays.asList(TestHelper.FOLDER_WITH_NPN_PROJECTS);

        Stream<DependencyInfo> distinctDependencies = getDependenciesWithFilter(dirs, props);
        List<DependencyInfo> dependencies = distinctDependencies.collect(Collectors.toList());

        List<DependencyInfo> dependenciesWithSha1 = dependencies.stream().filter(dep -> StringUtils.isNotBlank(dep.getSha1())).collect(Collectors.toList());
        Assert.assertEquals(dependenciesWithSha1.size(), dependencies.size());
    }

    @Test
    public void shouldBeTheSameResultsAsNpmLs() {
        Properties props = TestHelper.getPropertiesFromFile();
        File dir = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        Arrays.stream(dir.listFiles()).filter(child -> child.isDirectory()).forEach((startDirectory) ->
        {
            List<String> dirs = Arrays.asList(startDirectory.getPath());
            Stream<DependencyInfo> dependenciesOrig = getDependenciesWithFilter(dirs, props);

            Stream<String> dependencies = dependenciesOrig.filter(x -> x.getDependencyType() == DependencyType.NPM).map(dependency -> TestHelper.getShortNameByTgz(dependency));
            Stream<String> distinctDependenciesAutoResolver = dependencies.distinct().sorted();
            Stream<String> distinctDependenciesNpmLs = TestHelper.getDependenciesWithNpm(startDirectory.getAbsolutePath());

            List<String> autoResolverResults = distinctDependenciesAutoResolver.collect(Collectors.toList());
            List<String> npmLsResults = distinctDependenciesNpmLs.collect(Collectors.toList());

            //the number of package json files should be the same as the 'npm ls'
            autoResolverResults.removeAll(npmLsResults);
            Assert.assertTrue(autoResolverResults.size() == 0);
        });
    }

    @Test
    public void testPackageJsonOnly() {
        File dir10 = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);

        Arrays.stream(dir10.listFiles()).filter(dir -> dir.isDirectory()).forEach(directory -> {
            FilesScanner fs = new FilesScanner();

            Collection<ResolvedFolder> map = fs.findTopFolders(Arrays.asList(directory.getPath()), new NpmDependencyResolver().getBomPattern(), new LinkedList<>());
            map.forEach((folder) -> Assert.assertTrue(folder.getTopFoldersFound().size() > 0));
        });
    }

    @Test
    public void shouldRunMixedMainOnFolder() {
        File directory = new File(TestHelper.FOLDER_WITH_MIX_FOLDERS);
        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> runMainOnDir(dir));
    }

    @Test
    public void shouldReturnTheSameNumberOfDependenciesAsBowerPlugin() {
        File directory = new File(TestHelper.FOLDER_WITH_BOWER_PROJECTS);
        Properties props = TestHelper.getPropertiesFromFile();

        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> {
            // send to server via fs-agent
            //runMainOnDir(dir);

            // send to server via npm-plugin
            runNpmPluginOnFolder(dir, new String[]{"node", "C:\\Users\\eugenh\\Application Data\\npm\\node_modules\\ws-bower\\bin\\ws-bower.js", "run"});

            // collect number of dependencies via npm-plugin
            Collection<DependencyInfo> bowerPluginDependencies = readNpmPluginFile(dir, "ws-log-bower-report-post.json");

            // collect number of dependencies via npm-fs-agent
            testResults(props, dir, bowerPluginDependencies);
        });
    }

    @Test
    public void shouldReturnTheSameNumberOfDependenciesAsNpmPlugin() {
        File directory = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        Properties props = TestHelper.getPropertiesFromFile();

        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> {
            // send to server via fs-agent
            //runMainOnDir(dir);

            // send to server via npm-plugin
            runNpmPluginOnFolder(dir, new String[]{"node", "C:\\Users\\eugenh\\Application Data\\npm\\node_modules\\whitesource\\bin\\whitesource.js", "run"});

            // collect number of dependencies via npm-plugin
            Collection<DependencyInfo> dependencyInfosNPMPLugin = readNpmPluginFile(dir, "ws-log-report-post.json");
            testResults(props, dir, dependencyInfosNPMPLugin);
        });
    }

    private void testResults(Properties props, File dir, Collection<DependencyInfo> dependencyInfosNPMPLugin) {
        // collect number of dependencies via npm-fs-agent
        props.setProperty(PROJECT_NAME_PROPERTY_KEY, dir.getName());
        props.setProperty(PRODUCT_NAME_PROPERTY_KEY, "bower_plugin_01");
        FileSystemAgentTesting f = new FileSystemAgentTesting(props, Arrays.asList(dir.getAbsolutePath()));
        Collection<AgentProjectInfo> projects = f.createProjects();
        Collection<DependencyInfo> fsAgentDeps = projects.stream().findFirst().get().getDependencies();
        f.sendRequest(projects);

        int npmPluginCount = getCount(dependencyInfosNPMPLugin);
        int fsAgentCount = getCount(fsAgentDeps);

        Assert.assertTrue(npmPluginCount != 0);
        Assert.assertTrue(fsAgentCount != 0);
        Assert.assertTrue(npmPluginCount <= fsAgentCount);
    }

    private int getCount(Collection<DependencyInfo> dependencies) {
        final int[] totalDependencies = {0};
        totalDependencies[0] += dependencies.size();
        dependencies.forEach(dependency -> increaseCount(dependency, totalDependencies));
        return totalDependencies[0];
    }

    private void increaseCount(DependencyInfo dependency, int[] totalDependencies) {
        totalDependencies[0] += dependency.getChildren().size();
        dependency.getChildren().forEach(dependencyInfo -> increaseCount(dependencyInfo, totalDependencies));
    }

    private void runNpmPluginOnFolder(File dir, String[] args) {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(dir);
        try {
            Process process = pb.start();
            // parse 'npm ls --json' output
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                //output = reader.lines().reduce("", String::concat);
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Collection<DependencyInfo> readNpmPluginFile(File dir, String fileLog) {
        Collection<DependencyInfo> dependenciesInfo = new ArrayList<>();
        String fileName = Paths.get(dir.getAbsolutePath(), fileLog).toString();
        String json;
        try (InputStream is = new FileInputStream(fileName)) {
            json = IOUtils.toString(is);
            JSONObject jsonObj = new JSONObject(json);
            String dependencies = jsonObj.getString("diff");
            dependencies = dependencies.substring(1, dependencies.length());
            dependencies = dependencies.substring(0, dependencies.length() - 1);
            jsonObj = new JSONObject(dependencies);
            addDependencies(dependenciesInfo, jsonObj);

            is.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dependenciesInfo;
    }

    private void addDependencies(Collection<DependencyInfo> dependenciesInfo, JSONObject jsonObj) {
        JSONArray arr = jsonObj.getJSONArray("dependencies");
        arr.forEach(childDependency -> {
            JSONObject obj = (JSONObject) childDependency;
            DependencyInfo d = new DependencyInfo();
            String artifact = obj.getString("artifactId");
            String version = obj.getString("version");
            String groupId = obj.getString("groupId");
            if (obj.has("dependencies")) {
                JSONObject child = obj.getJSONObject("dependencies");
                addDependencies(dependenciesInfo, child);
            }

            URI uri = null;
            try {
                uri = new URI(artifact);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            String path = uri.getPath();
            String idStr = path.substring(path.lastIndexOf('/') + 1);

            d.setArtifactId(idStr);
            d.setGroupId(groupId);
            d.setVersion(version);
            dependenciesInfo.add(d);
        });
    }

    /* --- Private methods --- */

    private void runMainOnDir(File directory) {
        String[] args = ("-d " + directory.getPath() + " -product " + "fsAgentMain" + " -project " + directory.getName()).split(" ");
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

        @Override
        public StatusCode sendRequest(Collection<AgentProjectInfo> projects) {
            return super.sendRequest(projects);
        }
    }
}