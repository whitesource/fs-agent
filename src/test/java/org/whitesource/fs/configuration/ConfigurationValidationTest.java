package org.whitesource.fs.configuration;
import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.FileSystemAgentConfiguration;

import java.io.*;
import java.util.Properties;

public class ConfigurationValidationTest {

    @Test
    public void shouldWorkWithProjectPerFolder() throws IOException {

        File tmpPath = TestHelper.getTempFileWithReplace("#projectPerFolder=true", "projectPerFolder=true");
        ConfigurationValidation configurationValidation = new ConfigurationValidation();

        // act
        Properties configProperties = configurationValidation.readAndValidateConfigFile(tmpPath.toString(), "");

        // assert
        Assert.assertNotNull(configProperties);
    }

    @Test
    public void shouldNotOverrideParametersFromCommandArgs() throws IOException {
        // arrange
        File tmpPath = TestHelper.getTempFileWithReplace("#projectPerFolder=true", "projectPerFolder=true");

        // act
        String[] commandLineArgs = new String[]{"-c", tmpPath.getAbsolutePath(), "-d", new File(TestHelper.FOLDER_WITH_MIX_FOLDERS).getAbsolutePath()};
        FileSystemAgentConfiguration fileSystemAgentConfiguration = new FileSystemAgentConfiguration(commandLineArgs);

        // assert
        Assert.assertTrue(fileSystemAgentConfiguration.getProperties().getProperty(ConfigPropertyKeys.PROJECT_PER_SUBFOLDER).equals("true"));
    }

}
