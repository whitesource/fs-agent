package org.whitesource.agent.configuration;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.fs.ConfigurationSerializer;
import org.whitesource.fs.configuration.FSAConfiguration;
import org.whitesource.fs.configuration.FSAScmConfiguration;

import java.io.IOException;
import java.util.ArrayList;

public class FSAConfigurationTest {
    @Test
    public void shouldSerialize() throws IOException {
        String yamlFileIn = "C:\\Users\\eugen\\Downloads\\whitesource-fs-agent.config.yml";

        ConfigurationSerializer configurationSerializer = new ConfigurationSerializer();

        FSAConfiguration config = configurationSerializer.load(yamlFileIn);
        Assert.assertNotNull(config);

//        FSAConfiguration fsaConfiguration = new FSAConfiguration();
//        fsaConfiguration.scms = new ArrayList<>();
//        fsaConfiguration.scms.add(new FSAScmConfiguration("1","1","1","1","1","1","1"));
//        fsaConfiguration.scms.add(new FSAScmConfiguration("2","2","2","1","1","1","1"));
//
//        fsaConfiguration.setIncludes("**/*.c **/*.cc **/*.cp **/*.cpp **/*.cxx **/*.c++ **/*.h **/*.hpp **/*.hxx");

       // boolean result = configurationSerializer.save(fsaConfiguration, yamlFileIn);
        //Assert.assertTrue(result);
    }
}
