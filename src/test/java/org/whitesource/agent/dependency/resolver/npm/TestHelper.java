package org.whitesource.agent.dependency.resolver.npm;

import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.api.model.AgentProjectInfo;
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
    public static final File SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES = TestHelper.getFileFromResources("resolver/npm/sample/package.json");

    public static String FOLDER_WITH_BOWER_PROJECTS = TestHelper.getFileFromResources("resolver/bower/angular.js/bower.json")
            .getParentFile().getParentFile().getAbsolutePath();
    public static String FOLDER_WITH_NPN_PROJECTS = SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES
            .getParentFile().getParentFile().getAbsolutePath();
    public static final String FOLDER_WITH_MIX_FOLDERS = new File(FOLDER_WITH_NPN_PROJECTS).getParent();

    /* --- Static Methods --- */

    public static String getFirstFolder(String dir) {
        File file = new File(dir);
        String files = Arrays.stream(file.listFiles()).filter(f -> f.isDirectory()).findFirst().get().getAbsolutePath();
        return files.toString();
    }

    public static Stream<String> getDependenciesWithNpm(String dir) {
        NpmLsJsonDependencyCollector collector = new NpmLsJsonDependencyCollector(false, 60);
        AgentProjectInfo projectInfo = collector.collectDependencies(dir).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        return dependencies.stream().map(dep -> getShortNameByTgz(dep)).sorted();
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
            File file = TestHelper.getFileFromResources("whitesource-fs-agent.config");
            InputStream input1 = new FileInputStream(file);
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

    public static File getFileFromResources(String relativeFilePath) {
        ClassLoader classLoader = TestHelper.class.getClassLoader();
        String osFilePath = getOsRelativePath(relativeFilePath);
        File file = new File(classLoader.getResource(osFilePath).getFile());
        return file;
    }

    public static String getOsRelativePath(String relativeFilePath) {
        return relativeFilePath.replace("\\", String.valueOf(File.separatorChar).replace("/", String.valueOf(File.separatorChar)));
    }
}