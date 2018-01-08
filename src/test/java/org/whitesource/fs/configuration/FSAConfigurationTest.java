package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.fs.FSAConfiguration;

import java.io.IOException;

public class FSAConfigurationTest {

    @Test
    public void shouldLoadSave() throws IOException {
        FSAConfiguration fsaConfiguration = new FSAConfiguration();
        ConfigurationSerializer configurationSerializer = new ConfigurationSerializer();

        String configPath = "c:\\temp\\config.yml";
        configurationSerializer.save(fsaConfiguration,configPath);
        FSAConfiguration fsaConfigurationResult = configurationSerializer.load(configPath);

        Assert.assertNotNull(fsaConfigurationResult);
    }
}
