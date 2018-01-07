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
import java.io.InputStream;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class ConfigurationValidation {
    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidation.class);
    private static final int MAX_EXTRACTION_DEPTH = 7;

    public Pair<Properties,Boolean> readWithError(String configFilePath, String projectName) {
        Properties configProps = new Properties();
        InputStream inputStream = null;
        boolean foundError = false;
        try {
            inputStream = new FileInputStream(configFilePath);
            configProps.load(inputStream);
            foundError = isConfigurationInError(configProps, configFilePath, projectName);
        } catch (FileNotFoundException e) {
            logger.error("Failed to open " + configFilePath + " for reading", e);
            foundError = true;
        } catch (IOException e) {
            logger.error("Error occurred when reading from " + configFilePath, e);
            foundError = true;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.warn("Failed to close " + configFilePath + "InputStream", e);
                }
            }
        }
        return new Pair<>(configProps, foundError);
    }

    public boolean isConfigurationInError(Properties configProps, String configFilePath, String project) {
        boolean foundError = false;
        if (StringUtils.isBlank(configProps.getProperty(ORG_TOKEN_PROPERTY_KEY))) {
            foundError = true;
            logger.error("Could not retrieve {} property from {}", ORG_TOKEN_PROPERTY_KEY, configFilePath);
        }

        String projectToken = configProps.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        String projectName = !StringUtils.isBlank(project) ? project : configProps.getProperty(PROJECT_NAME_PROPERTY_KEY);
        boolean noProjectToken = StringUtils.isBlank(projectToken);
        boolean noProjectName = StringUtils.isBlank(projectName);
        boolean projectPerFolder = FSAConfiguration.getBooleanProperty(configProps, PROJECT_PER_SUBFOLDER, false);
        if (noProjectToken && noProjectName && !projectPerFolder) {
            foundError = true;
            logger.error("Could not retrieve properties {} and {} from {}",
                    PROJECT_NAME_PROPERTY_KEY, PROJECT_TOKEN_PROPERTY_KEY, configFilePath);
        } else if (!noProjectToken && !noProjectName) {
            foundError = true;
            logger.error("Please choose just one of either {} or {} (and not both)", PROJECT_NAME_PROPERTY_KEY, PROJECT_TOKEN_PROPERTY_KEY);
        }

        int archiveExtractionDepth = FSAConfiguration.getArchiveDepth(configProps);
        String[] includes = FSAConfiguration.getIncludes(configProps);
        return foundError || shouldShutDown(archiveExtractionDepth, includes);
    }


    private boolean shouldShutDown(int archiveExtractionDepth, String[] includes) {
        boolean isShutDown = false;
        if (archiveExtractionDepth < 0 || archiveExtractionDepth > MAX_EXTRACTION_DEPTH) {
            logger.warn("Error: archiveExtractionDepth value should be greater than 0 and less than " + MAX_EXTRACTION_DEPTH);
            isShutDown = true;
        }
        if (includes.length < 1 || StringUtils.isBlank(includes[0])) {
            logger.warn("Error: includes parameter must have at list one scanning pattern");
            isShutDown = true;
        }
        if (isShutDown) {
            logger.warn("Exiting");
        }
        return isShutDown;
    }
}
