package org.whitesource.agent;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.CommandLineArgs;
import org.whitesource.fs.ComponentScan;
import org.whitesource.fs.FSAConfigProperties;

import java.io.File;
import java.nio.file.Paths;

/**
 * Created by anna.rozin
 */
public class ComponentScanTest {

    @Ignore
    @Test
    public void shouldScanComponents() {

        // Arrange
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();
        props.put("includes", "**/*.cs");
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String resolverFolder = Paths.get(config.getParent(), "resolver/nuget").toString();
        props.put(Constants.DIRECTORY, resolverFolder);
//        props.put(ConfigPropertyKeys.SCAN_DOCKER_IMAGES, "true");
        props.put(ConfigPropertyKeys.SCAN_PACKAGE_MANAGER, "true");
        ComponentScan componentScan = new ComponentScan(props);

        // Act
        String scanResult = componentScan.scan();

        // Assert
        Assert.assertTrue(scanResult.length() > 200);
        System.out.printf(scanResult);
    }

    @Ignore
    @Test
    public void testAcceptExtensionsList() {
        FSAConfigProperties props = TestHelper.getPropertiesFromFile();
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String resolverFolder = "C:\\Users\\RazNitzan\\git\\fs-agent\\target\\test-classes\\resolver\\maven";
        props.put("d", resolverFolder);
        props.put("archiveExtractionDepth", 4);
        props.put("includes", "**/**");
        props.put("archiveIncludes", "**/*zip **/*war **/*ear **/*tgz **/*jar **/*sca **/*gem **/*whl **/*egg **/*tar **/*tar.gz **/*rar");
//        props.put("bower.runPreStep", "true");
        String[] acceptExtensions = {"jar", "war", "ear", "aar", "dll", "exe", "msi", "nupkg", "egg", "whl",
                "tar.gz", "gem", "deb", "udeb", "dmg", "drpm", "rpm", "pkg.tar.xz", "swf", "swc", "air", "apk", "zip", "gzip", "tar.bz2",
                "tgz", "c", "cc", "cp", "cpp", "css", "c++", "h", "hh", "hpp", "hxx", "h++", "m", "mm", "pch", "c#", "cs", "csharp", "java",
                "go", "goc", "js", "plx", "pm", "ph", "cgi", "fcgi", "psgi", "al", "perl", "t", "p6m", "p6l", "nqp", "6pl",
                "6pm", "p6", "php", "py", "rb", "swift", "clj", "cljx", "cljs", "cljc"};
//        props.put("logContext", "dasdad");
        props.put(ConfigPropertyKeys.ACCEPT_EXTENSIONS_LIST, acceptExtensions);
        ComponentScan componentScan = new ComponentScan(props);
        String scanResult = componentScan.scan();
    }
}
