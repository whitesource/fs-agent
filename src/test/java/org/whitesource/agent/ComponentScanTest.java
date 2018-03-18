package org.whitesource.agent;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.CommandLineArgs;
import org.whitesource.fs.ComponentScan;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by anna.rozin
 */
public class ComponentScanTest {

    @Test
    public void shouldScanComponents() {

        // Arrange
        Properties props = TestHelper.getPropertiesFromFile();
        props.put("includes", "**/*.cs");
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String resolverFolder = Paths.get(config.getParent(), "resolver/nuget").toString();
        props.put("d", resolverFolder);
        ComponentScan componentScan = new ComponentScan(props);

        // Act
        String scanResult = componentScan.scan();

        // Assert
        Assert.assertTrue(scanResult.length() > 200);
        System.out.printf(scanResult);
    }

    @Test
    public void testAcceptExtensionsList() {
        Properties props = TestHelper.getPropertiesFromFile();
//        String resolverFolder = "C:\\Users\\RazNitzan\\Desktop\\NPM-Plugin\\npm-plugin-for-test";
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String resolverFolder = Paths.get(config.getParent(), "resolver/npm").toString();
        props.put("d", resolverFolder);
        String[] acceptExtensions = {"jar", "war"};
        props.put(ConfigPropertyKeys.ACCEPT_EXTENSIONS_LIST, acceptExtensions);
        ComponentScan componentScan = new ComponentScan(props);
        String scanResult = componentScan.scan();
    }
}
