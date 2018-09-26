/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.fs.FSAConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

public class ConfigurationSerializer <T> {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationSerializer.class);

    /* --- Members --- */

    private static ObjectMapper jsonMapper = new ObjectMapper();
    private static ObjectMapper jsonMapperWithoutNulls = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static ObjectMapper yamlMapperWithoutNulls = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /* --- Constructors --- */

    public FSAConfiguration load(String fileInput) throws IOException {
        //todo: add support to load from properties file
        FSAConfiguration config = jsonMapper.readValue(new File(fileInput), FSAConfiguration.class);
        return config;
    }

    public boolean save(T object, String fileOutput, boolean includeNulls) {
        try {
            if (includeNulls) {
                jsonMapper.writeValue(new File(fileOutput), object);
            } else {
                jsonMapperWithoutNulls.writeValue(new File(fileOutput), object);
            }
            return true;
        } catch (IOException e) {
            logger.error("error saving configuration ", e.getStackTrace());
            return false;
        }
    }

    public boolean saveYaml(T object, String fileOutput) {
        try {
            yamlMapperWithoutNulls.writeValue(new File(fileOutput), object);
            return true;
        } catch (IOException e) {
            logger.error("error saving configuration ", e.getStackTrace());
            return false;
        }
    }

    public <T> String getAsString(T object, boolean includeNulls) {
        try {
            if (includeNulls) {
                return jsonMapperWithoutNulls.writeValueAsString(object);
            } else {
                return jsonMapperWithoutNulls.writeValueAsString(object);
            }
        } catch (IOException e) {
            logger.error("error getting configuration ", e.getStackTrace());
            return null;
        }
    }

    public static <T> Properties getAsProperties(T object) {
        Map<String, Object> map = jsonMapper.convertValue(object, Map.class);
        Properties properties = new Properties();
        fillProperties(map, properties);
        return properties;
    }

    private static void fillProperties(Map<String, Object> map, Properties properties) {
        // notice that this is only
        map.entrySet().forEach(entry -> {
            if (entry.getValue() != null)
                if (entry.getValue() instanceof Map) {
                    fillProperties((Map<String, Object>) entry.getValue(), properties);
                } else {
                    if (entry.getValue() instanceof ArrayList) {
                        properties.put(entry.getKey(), String.join(Constants.WHITESPACE, (ArrayList) entry.getValue()));
                    } else {
                        properties.put(entry.getKey(), entry.getValue().toString());
                    }
                }
        });
    }

    public static <T> T getFromString(String json, Class<T> typeParameterClass, boolean includeNulls) {
        try {
            if (includeNulls) {
                T config = jsonMapperWithoutNulls.readValue(json, typeParameterClass);
                return config;
            } else {
                T config = jsonMapper.readValue(json, typeParameterClass);
                return config;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            logger.debug("{}", e.getStackTrace());
            return null;
        }
    }
}