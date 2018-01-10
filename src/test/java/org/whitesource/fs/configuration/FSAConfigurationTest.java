package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.fs.FSAConfiguration;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

public class FSAConfigurationTest {

    final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Test
    public void shouldLoadSave() throws IOException {
        ConfigurationSerializer configurationSerializer = new ConfigurationSerializer();

        FSAConfiguration fsaConfiguration = new FSAConfiguration();

        String tempCofig = getTempFile();
        configurationSerializer.save(fsaConfiguration, tempCofig);
        FSAConfiguration fsaConfigurationResult = configurationSerializer.load(tempCofig);

//        Assert.assertTrue(fsaConfigurationResult.getResolver().isDependencyResolverNpmRunPreStep());
//        fsaConfiguration = new FSAConfiguration();
//        Path tmpPath = Paths.get(JAVA_TEMP_DIR, tempCofig);
//        configurationSerializer.save(fsaConfiguration,tmpPath.toString());
//        fsaConfigurationResult = configurationSerializer.load(tmpPath.toString());

        Assert.assertNotNull(fsaConfigurationResult);
    }

    public String getTempFile() {
        return Paths.get(JAVA_TEMP_DIR, UUID.randomUUID().toString()).toString();
    }
}
