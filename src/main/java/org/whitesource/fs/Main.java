/**
 * Copyright (C) 2014 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.whitesource.fs.Constants.*;

/**
 * Author: Itai Marko
 */
public class Main {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final CommandLineArgs commandLineArgs = new CommandLineArgs();
    private static JCommander jCommander;

    /* --- Main --- */

    public static void main(String[] args) {
        jCommander = new JCommander(commandLineArgs, args);
        // validate args // TODO use jCommander validators
        // TODO add usage command

        Properties configProps = readAndValidateConfigFile(commandLineArgs.configFilePath);
        WhitesourceFSAgent whitesourceAgent = new WhitesourceFSAgent(configProps, commandLineArgs.dependencyDirs);
        whitesourceAgent.sendRequest();
    }

    /* --- Private methods --- */

    private static Properties readAndValidateConfigFile(String configFilePath) {
        Properties configProps = new Properties();
        InputStream inputStream = null;
        boolean foundError = false;
        try {
            inputStream = new FileInputStream(configFilePath);
            configProps.load(inputStream);
            foundError = validateConfigProps(configProps, configFilePath);
        } catch (FileNotFoundException e) {
            logger.error("Failed to open " + configFilePath + " for reading", e);
            foundError = true;
        } catch (IOException e) {
            logger.error("Error occurred when reading from " + configFilePath , e);
            foundError = true;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.warn("Failed to close " + configFilePath + "InputStream", e);
                }
            }
            if (foundError) {
                System.exit(-1); // TODO this may throw SecurityException. Return null instead
            }
        }
        return configProps;
    }

    private static boolean validateConfigProps(Properties configProps, String configFilePath) {
        boolean foundError = false;
        if (StringUtils.isBlank(configProps.getProperty(ORG_TOKEN_PROPERTY_KEY))) {
            foundError = true;
            logger.error("Could not retrieve {} property from {}", ORG_TOKEN_PROPERTY_KEY, configFilePath);
        }

        String projectToken = configProps.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        String projectName = configProps.getProperty(PROJECT_NAME_PROPERTY_KEY);
        boolean noProjectToken = StringUtils.isBlank(projectToken);
        boolean noProjectName = StringUtils.isBlank(projectName);
        if (noProjectToken && noProjectName) {
            foundError = true;
            logger.error("Could not retrieve properties {} and {}  from {}",
                    PROJECT_NAME_PROPERTY_KEY, PROJECT_TOKEN_PROPERTY_KEY, configFilePath);
        } else if (!noProjectToken && !noProjectName) {
            foundError = true;
            logger.error("Please choose {} or {}", PROJECT_NAME_PROPERTY_KEY, PROJECT_TOKEN_PROPERTY_KEY);
        }

        return foundError;
    }


}
