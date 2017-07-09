import org.junit.Assert;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by eugen.horovitz on 6/29/2017.
 */
public class TestHelper {
    //all tests runs on the this sample project : https://github.com/punkave/apostrophe
    /* --- Static Members --- */
    public static final String SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES_UBUNTU = "apostrophe-master/node_modules/chokidar/package.json";
    public static String FOLDER_TO_TEST_UBUNTU = "/home/eugen/Documents/Repositories/fs-agent/toScan/apostrophe-master/";
    public static String FOLDER_WITH_NPN_PROJECTS_UBUNTU = "/home/eugen/Documents/Repositories/fs-agent/toScan/";

    public static final String SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES = "apostrophe-master\\node_modules\\chokidar\\package.json";
    public static String FOLDER_TO_TEST = "C:\\Users\\eugen\\WebstormProjects\\toScan\\apostrophe-master";
    public static String FOLDER_WITH_NPN_PROJECTS = "C:\\Users\\eugen\\WebstormProjects\\toScan\\";

    /* --- Static Methods --- */

    public static Stream<String> getDependenciesWithNpm(String dir) {
        NpmLsJsonDependencyCollector collector = new NpmLsJsonDependencyCollector();
        Collection<DependencyInfo> dependencyInfos = collector.collectDependencies(dir);
        return dependencyInfos.stream().map(dep->getShortNameByTgz(dep)).sorted();
    }

    private static Set<String> getAllDependenciesNames(DependencyInfo dep) {
        Set deps = new HashSet();
        deps.add(TestHelper.getShortNameByTgz(dep));
        dep.getChildren().forEach(child->
        {
            Set<String> depsChild = getAllDependenciesNames(child);
            deps.addAll(depsChild);
        });
        return deps;
    }

    public static void assertListEquals(List<String> results1, List<String> results2) {
        Iterator<?> iterator1 = results1.iterator(), iterator2 = results2.iterator();
        while (iterator1.hasNext() && iterator2.hasNext())
            Assert.assertEquals(iterator1.next(), iterator2.next());
        assert !iterator1.hasNext() && !iterator2.hasNext();
    }

    public static String getShortNameByTgz(DependencyInfo dep) {
        String result = dep.getArtifactId()
                .replace(dep.getVersion(), "")
                .replace("|", "")
                .replace(" ", "")
                .replace("+", "")
                .replace("-", "")
                .replace(".tgz", "")
                + "@" + dep.getVersion().replace("-", "");

        return result;
    }

    public static Properties getPropertiesFromFile() {
        Properties p = new Properties();
        try {
            String currentDir = System.getProperty("user.dir");
            InputStream input1 = new FileInputStream(Paths.get(currentDir, "whitesource-fs-agent.config").toString());
            p.load(input1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //override if needed
        p.setProperty(ConfigPropertyKeys.FORCE_CHECK_ALL_DEPENDENCIES, "false");
        p.setProperty(ConfigPropertyKeys.CHECK_POLICIES_PROPERTY_KEY, "false");
        p.setProperty(ConfigPropertyKeys.PROJECT_VERSION_PROPERTY_KEY, "0");
        p.setProperty(ConfigPropertyKeys.FOLLOW_SYMBOLIC_LINKS, "true");
        p.setProperty(ConfigPropertyKeys.OFFLINE_PROPERTY_KEY, "false");
        p.setProperty(ConfigPropertyKeys.PRODUCT_NAME_PROPERTY_KEY, "NPM Test Pro hierarchy");
        p.setProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY, "**/*sources.jar **/*javadoc.jar");
        p.setProperty(ConfigPropertyKeys.CASE_SENSITIVE_GLOB_PROPERTY_KEY, "false");
        p.setProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, "**/*.m **/*.mm  **/*.js **/*.php");
        p.setProperty(ConfigPropertyKeys.RESOLVE_NPM_DEPENDENCIES, "true");
        p.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "testNpm");
        return p;
    }

    public static void writeToFile(List<String> results, String output) {
        try {
            FileWriter writer = new FileWriter(output);
            for (String str : results) {
                writer.write(str + "\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}