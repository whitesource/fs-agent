package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.FSAConfiguration;
import org.whitesource.fs.FSAConfigProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

public class FSAConfigurationTest {

    final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Ignore
    @Test
    public void shouldLoadSave() throws IOException {
        ConfigurationSerializer configurationSerializer = new ConfigurationSerializer();

        File file = TestHelper.getFileFromResources("whitesource-fs-agent.config");
        FSAConfigProperties properties = new FSAConfigProperties();

        try (InputStream stream = new FileInputStream(file.toString())) {
            properties.load(stream);
        } catch (IOException e) {
            String error = e.getMessage();
        }

        FSAConfiguration fsaConfiguration = new FSAConfiguration(properties);
        Properties propertiesAfterConfig = ConfigurationSerializer.getAsProperties(fsaConfiguration);

        checkSubsetProperties(properties, propertiesAfterConfig);

        String tempCofig = getTempFile();
        configurationSerializer.save(fsaConfiguration, tempCofig, true);

        FSAConfiguration fsaConfigurationResult = configurationSerializer.load(tempCofig);
        Properties sameProperties = ConfigurationSerializer.getAsProperties(fsaConfigurationResult);

        checkSubsetProperties(propertiesAfterConfig, sameProperties);

        configurationSerializer.saveYaml(fsaConfigurationResult, Paths.get(JAVA_TEMP_DIR,"config2.yml").toString());
    }

    private void checkSubsetProperties(Properties subsetProperties, Properties bigSetProperties) {
        subsetProperties.stringPropertyNames().stream().forEach(prop->{
            Assert.assertTrue(bigSetProperties.containsKey(prop));
            Assert.assertEquals(bigSetProperties.get(prop).toString(), subsetProperties.get(prop).toString());
        });
    }

    public String getTempFile() {
        return Paths.get(JAVA_TEMP_DIR, UUID.randomUUID().toString()).toString();
    }
}
