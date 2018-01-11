package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.fs.FSAConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigurationSerializer <T>{
    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationSerializer.class);

    /* --- Members --- */

    private YAMLFactory yamlFactory = new YAMLFactory();
    private ObjectMapper mapper = new ObjectMapper(yamlFactory);

    /* --- Constructors --- */

    public ConfigurationSerializer() {
        yamlFactory = new YAMLFactory();
        //mapper = new ObjectMapper(yamlFactory);
        //mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    }

    public FSAConfiguration load(String fileInput) throws IOException {
        //todo: add support to load from properties file
        FSAConfiguration config = mapper.readValue(new File(fileInput), FSAConfiguration.class);
        return config;
    }

    public boolean save(T fsaConfiguration, String fileOutput){
        //todo: add support to save to properties file
        try {
            //FileOutputStream fos = new FileOutputStream(fileOutput);
            //yamlFactory.createGenerator(fos).writeObject(fsaConfiguration);
            mapper.writeValue(new File(fileOutput), fsaConfiguration );
            return true;
        } catch (IOException e) {
            logger.error("error saving configuration " ,e);
            return false;
        }
    }

    public String getAsString(T object){
        //todo: add support to save to properties file
        try {
            //FileOutputStream fos = new FileOutputStream(fileOutput);
            //yamlFactory.createGenerator(fos).writeObject(fsaConfiguration);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            logger.error("error saving configuration " ,e);
            return null;
        }
    }
}

