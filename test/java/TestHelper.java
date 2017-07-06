import org.junit.Assert;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Created by eugen.horovitz on 6/29/2017.
 */
public class TestHelper {
    /* --- Static Members --- */

    public static String FOLDER_TO_TEST = "C:\\Users\\eugen\\Downloads\\apostrophe-master\\";
    public static String FOLDER_WITH_NPN_PROJECTS = "C:\\Users\\eugen\\WebstormProjects\\good\\";

    /* --- Static Methods --- */

    public static Stream<String> getDependenciesWithNpm(List<String> dirs) {
        String[] cmd = {
                "npm.cmd",
                "ls"};

        Stream<String> distinctLines = null;

        try {
            Process process;
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(dirs.get(0)));
            process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            Stream<String> lines = reader.lines();
            distinctLines = lines.map(s ->
                    s.replace("|", "")
                            .replace(" ", "")
                            .replace("+", "")
                            .replace("-", "")
                            .replace("`", "")
                            .replace("UNMETPEERDEPENDENCY", "")//this if for unmet dependencies
            ).distinct().filter(x ->
                    !x.contains("\\")
                            && !x.equals("")
                            && !x.contains("OPTIONAL")
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return distinctLines.sorted();
    }

    public static void assertListEquals(List<String> results1, List<String> results2) {
        Iterator<?> iterator1 = results1.iterator(), iterator2 = results2.iterator();
        while (iterator1.hasNext() && iterator2.hasNext())
            Assert.assertEquals(iterator1.next(), iterator2.next());
        assert !iterator1.hasNext() && !iterator2.hasNext();
    }

    public static String getShortNameByTgz(DependencyInfo s) {
        String result = s.getArtifactId()
                .replace(s.getVersion(), "")
                .replace("|", "")
                .replace(" ", "")
                .replace("+", "")
                .replace("-", "")
                .replace(".tgz", "")
                + "@" + s.getVersion().replace("-", "");

        return result;
    }

    public static Properties getPropertiesFromFile() {
        Properties p = new Properties();
        try {
            InputStream input1 = new FileInputStream("C:\\Users\\eugen\\Repositories\\fs-agent\\whitesource-fs-agent.config");
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