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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.FSAConfiguration;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class ConfigurationValidation {
    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidation.class);
    private static final int MAX_EXTRACTION_DEPTH = 7;

    public Pair<Properties, List<String>> readWithError(String configFilePath, String projectName) {
        Properties configProps = new Properties();
        List<String> errors = new ArrayList<>();
        try {
            try (FileInputStream inputStream = new FileInputStream(configFilePath)) {
                try {
                    configProps.load(inputStream);
                } catch (FileNotFoundException e) {
                    logger.error("Failed to open " + configFilePath + " for reading", e);
                } catch (IOException e) {
                    logger.error("Error occurred when reading from " + configFilePath, e);
                }
                errors.addAll(getConfigurationErrors(configProps, configFilePath, projectName));
                errors.forEach(error -> logger.error(error));
            }
        } catch (IOException e) {
            logger.error("Error occurred when reading from " + configFilePath, e);
        }
        return new Pair<>(configProps, errors);
    }

    public List<String> getConfigurationErrors(Properties configProps, String configFilePath, String project) {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isBlank(configProps.getProperty(ORG_TOKEN_PROPERTY_KEY))) {
            String error = "Could not retrieve " + ORG_TOKEN_PROPERTY_KEY + "property from " + configFilePath;
            errors.add(error);
        }

        String projectToken = configProps.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        String projectName = !StringUtils.isBlank(project) ? project : configProps.getProperty(PROJECT_NAME_PROPERTY_KEY);
        boolean noProjectToken = StringUtils.isBlank(projectToken);
        boolean noProjectName = StringUtils.isBlank(projectName);
        boolean projectPerFolder = FSAConfiguration.getBooleanProperty(configProps, PROJECT_PER_SUBFOLDER, false);
        if (noProjectToken && noProjectName && !projectPerFolder) {
            String error = "Could not retrieve properties " + PROJECT_NAME_PROPERTY_KEY + " and " + PROJECT_TOKEN_PROPERTY_KEY + " from " + configFilePath;
            errors.add(error);
        } else if (!noProjectToken && !noProjectName) {
            String error = "Please choose just one of either " + PROJECT_NAME_PROPERTY_KEY + " or " + PROJECT_TOKEN_PROPERTY_KEY + " (and not both)";
            errors.add(error);
        }

        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(configProps);
        String[] includes = FSAConfiguration.getIncludes(configProps);

        if (archiveExtractionDepth < 0 || archiveExtractionDepth > MAX_EXTRACTION_DEPTH) {
            errors.add("Error: archiveExtractionDepth value should be greater than 0 and less than " + MAX_EXTRACTION_DEPTH);
        }
        if (includes.length < 1 || StringUtils.isBlank(includes[0])) {
            errors.add("Error: includes parameter must have at list one scanning pattern");
        }
        return errors;
    }
}