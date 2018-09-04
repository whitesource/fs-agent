package org.whitesource.agent.dependency.resolver.npm;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.ResolvedFolder;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.fs.FSAConfigProperties;
import org.whitesource.fs.FileSystemAgent;
import org.whitesource.fs.FSAConfiguration;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
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

    @Ignore
    @Test
    public void shouldEnrichAllDependenciesWithSha1() {
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();
        List<String> dirs = Arrays.asList(TestHelper.FOLDER_WITH_NPN_PROJECTS);

        Stream<DependencyInfo> distinctDependencies = getDependenciesWithFilter(dirs, props);
        List<DependencyInfo> dependencies = distinctDependencies.collect(Collectors.toList());

        List<DependencyInfo> dependenciesWithSha1 = dependencies.stream().filter(dep -> StringUtils.isNotBlank(dep.getSha1())).collect(Collectors.toList());
        Assert.assertEquals(dependenciesWithSha1.size(), dependencies.size());
    }

    @Ignore
    @Test
    public void shouldBeTheSameResultsAsNpmLs() {
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();
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

    @Ignore
    @Test
    public void testPackageJsonOnly() {
        File dir10 = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);

        Arrays.stream(dir10.listFiles()).filter(dir -> dir.isDirectory()).forEach(directory -> {
            FilesScanner fs = new FilesScanner();

            Collection<ResolvedFolder> map = fs.findTopFolders(Arrays.asList(directory.getPath()), new NpmDependencyResolver(true, null,true).getBomPattern(), new LinkedList<>());
            map.forEach((folder) -> Assert.assertTrue(folder.getTopFoldersFound().size() > 0));
        });
    }

    @Ignore
    @Test
    public void shouldReturnTheSameNumberOfDependenciesAsBowerPlugin() {
        File directory = new File(TestHelper.FOLDER_WITH_BOWER_PROJECTS);
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();

        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> {
            // send to server via fs-agent
            //runMainOnDir(dir);

            // send to server via npm-plugin
            String pluginPath = TestHelper.getOsRelativePath("ws-bower\\bin\\ws-bower.js");
            runNpmPluginOnFolder(dir, pluginPath);

            // collect number of dependencies via npm-plugin
            Collection<DependencyInfo> bowerPluginDependencies = readNpmPluginFile(dir, "ws-log-bower-report-post.json");

            // collect number of dependencies via npm-fs-agent
            testResults(props, dir, bowerPluginDependencies);
        });
    }

    @Ignore
    @Test
    public void shouldReturnTheSameNumberOfDependenciesAsNpmPlugin() {
        File directory = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();

        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> {
            // send to server via npm-plugin
            String pluginPath = TestHelper.getOsRelativePath("whitesource/bin/whitesource.js");
            runNpmPluginOnFolder(dir, pluginPath);

            // collect number of dependencies via npm-plugin
            Collection<DependencyInfo> dependencyInfosNPMPLugin = readNpmPluginFile(dir, "ws-log-report-post.json");
            testResults(props, dir, dependencyInfosNPMPLugin);
        });
    }

    private void testResults(FSAConfigProperties props, File dir, Collection<DependencyInfo> dependencyInfosNPMPLugin) {
        // collect number of dependencies via npm-fs-agent
        props.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, dir.getName());
        props.setProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY, "bower_plugin_01");

        FSAConfiguration FSAConfiguration = new FSAConfiguration(props);
        FileSystemAgent fileSystemAgent = new FileSystemAgent(FSAConfiguration, Arrays.asList(dir.getAbsolutePath()));
        Collection<AgentProjectInfo> projects = fileSystemAgent.createProjects().getProjects();
        Collection<DependencyInfo> fsAgentDeps = projects.stream().findFirst().get().getDependencies();

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

    private void runNpmPluginOnFolder(File dir, String plugin) {
        String currentDir = System.getProperty("user.home");
        String currentDirLinux = "/usr/local/lib/node_modules/";

        String path = isWindows() ?
                Paths.get(currentDir, TestHelper.getOsRelativePath("Application Data\\npm\\node_modules\\" + plugin)).toString() :
                Paths.get(currentDirLinux, plugin).toString();

        String[] args = new String[]{"node", path, "run"};

        CommandLineProcess commandLineProcess = new CommandLineProcess(dir.toString(), args);
        try {
            List<String> lines = commandLineProcess.executeProcess();
            //Assert.assertFalse(commandLineProcess.isErrorInProcess());
            //Assert.assertTrue(lines.size() > 0);
        } catch (IOException e) {
            e.printStackTrace();
            Assert.assertNotNull(e);
        }
    }

    public static boolean isWindows() {
        return System.getProperty(Constants.OS_NAME).toLowerCase().contains(Constants.WIN);
    }

    private Collection<DependencyInfo> readNpmPluginFile(File dir, String fileLog) {
        Collection<DependencyInfo> dependenciesInfo = new ArrayList<>();
        String fileName = Paths.get(dir.getAbsolutePath(), "WhiteSource-log-files", fileLog).toString();
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
        JSONArray arr = jsonObj.getJSONArray(Constants.DEPENDENCIES);
        arr.forEach(childDependency -> {
            JSONObject obj = (JSONObject) childDependency;
            DependencyInfo d = new DependencyInfo();
            String artifact = obj.getString("artifactId");
            String version = obj.getString(Constants.VERSION);
            String groupId = obj.getString("groupId");
            if (obj.has(Constants.DEPENDENCIES)) {
                JSONObject child = obj.getJSONObject(Constants.DEPENDENCIES);
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


    private Stream<DependencyInfo> getDependenciesWithFilter(List<String> dirs, FSAConfigProperties props) {
        FSAConfiguration FSAConfiguration = new FSAConfiguration(props);
        FileSystemAgent f = new FileSystemAgent(FSAConfiguration, dirs);

        Collection<AgentProjectInfo> projects = f.createProjects().getProjects();

        Stream<DependencyInfo> dependenciesOrig = projects.stream().map(x -> x.getDependencies()).flatMap(d -> d.stream());
        return dependenciesOrig;
    }
}