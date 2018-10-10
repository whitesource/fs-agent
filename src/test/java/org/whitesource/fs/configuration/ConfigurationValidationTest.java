package org.whitesource.fs.configuration;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.CommandLineArgs;
import org.whitesource.fs.FSAConfiguration;

import java.io.*;
import java.util.Properties;

public class ConfigurationValidationTest {

    @Ignore
    @Test
    public void shouldWorkWithProjectPerFolder() {

        File tmpPath = TestHelper.getTempFileWithReplace("#projectPerFolder=true", "projectPerFolder=true");

        // act
        Properties configProperties = FSAConfiguration.readWithError(tmpPath.toString(), new CommandLineArgs()).getKey();

        // assert
        Assert.assertNotNull(configProperties);
    }

    @Ignore
    @Test
    public void shouldNotOverrideParametersFromCommandArgs() {
        // arrange
        File tmpPath = TestHelper.getTempFileWithReplace("#projectPerFolder=true", "projectPerFolder=true");

        // act
        String[] commandLineArgs = new String[]{"-c", tmpPath.getAbsolutePath(), "-d", new File(TestHelper.FOLDER_WITH_MIX_FOLDERS).getAbsolutePath()};
        FSAConfiguration fsaConfiguration = new FSAConfiguration(commandLineArgs);

        // assert
        Assert.assertTrue(fsaConfiguration.getRequest().isProjectPerSubFolder());

        // assert
        Assert.assertTrue(fsaConfiguration.getRequest().isProjectPerSubFolder());

        // assert
        Assert.assertTrue(StringUtils.isEmpty(fsaConfiguration.getRequest().getProductName()));

        // act
        commandLineArgs = new String[]{"-apiKey", "token" , "-product", "productName"};
        fsaConfiguration = new FSAConfiguration(commandLineArgs);

        // assert
        //Assert.assertTrue(fsaConfiguration.getErrors().size()>0);
        // assert
        Assert.assertEquals("productName",fsaConfiguration.getRequest().getProductName());
    }

}
