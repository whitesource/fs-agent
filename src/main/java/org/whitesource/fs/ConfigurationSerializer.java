package org.whitesource.fs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ConfigurationSerializer {
    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationSerializer.class);

    /* --- Members --- */

    private YAMLFactory yamlFactory = new YAMLFactory();
    private ObjectMapper mapper = new ObjectMapper(yamlFactory);

    /* --- Constructors --- */

    public ConfigurationSerializer() {
        yamlFactory = new YAMLFactory();
        mapper = new ObjectMapper(yamlFactory);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    }

    public FSAConfiguration load(String fileInput) throws IOException {
        //todo: add support to load from properties file
        FSAConfiguration config = mapper.readValue(new File(fileInput), FSAConfiguration.class);
        return config;
    }

    public boolean save(FSAConfiguration fsaConfiguration, String fileOutput){
        //todo: add support to save to properties file
        try {
            FileOutputStream fos = new FileOutputStream(fileOutput);
            yamlFactory.createGenerator(fos).writeObject(fsaConfiguration);
            return true;
        } catch (IOException e) {
            logger.error("error saving configuration " ,e);
            return false;
        }
    }
}

