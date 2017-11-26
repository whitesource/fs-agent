package org.whitesource.agent.dependency.resolver.npm;

import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;

import java.io.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * All tests runs on the this sample project : https://github.com/punkave/apostrophe
 *
 * @author eugen.horovitz
 */
public class TestHelper {

    /* --- Static Members --- */
    public static final String SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES = "\\node_modules\\chokidar\\package.json";

    public static final String FOLDER_WITH_MIX_FOLDERS = "C:\\Dev\\ws_mix\\ws_mix3\\ws_mix2_separated";
    public static String FOLDER_WITH_BOWER_PROJECTS = "C:\\Dev\\ws_mix\\ws_mix1\\bower_samples";
    public static String FOLDER_WITH_NPN_PROJECTS = "C:\\Dev\\ws_mix\\ws_mix1\\npm_samples";
    //public static String FOLDER_WITH_NPN_PROJECTS_UBUNTU = "/home/eugen/Documents/Repositories/fs-agent/toScan/";

    /* --- Static Methods --- */

    public static String getFirstFolder(String dir) {
        File file = new File(dir);
        String files = Arrays.stream(file.listFiles()).filter(f -> f.isDirectory()).findFirst().get().getAbsolutePath();
        return files.toString();
    }

    public static Stream<String> getDependenciesWithNpm(String dir) {
        NpmLsJsonDependencyCollector collector = new NpmLsJsonDependencyCollector(false, 60);
        Collection<DependencyInfo> dependencyInfos = collector.collectDependencies(dir);
        return dependencyInfos.stream().map(dep -> getShortNameByTgz(dep)).sorted();
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
        p.setProperty(ConfigPropertyKeys.NPM_RESOLVE_DEPENDENCIES, "true");
        p.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "testNpm");
        return p;
    }
}