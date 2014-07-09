package org.whitesource.fs;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

import static org.whitesource.fs.Constants.ORG_TOKEN_PROPERTY_KEY;
import static org.whitesource.fs.Constants.PROJECT_TOKEN_PROPERTY_KEY;
import static org.whitesource.fs.Constants.PROJECT_NAME_PROPERTY_KEY;

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

        Properties configProps = ReadAndValidateConfigFile(commandLineArgs.configFilePath);
        WhitesourceFSAgent whitesourceAgent = new WhitesourceFSAgent(commandLineArgs.dependencyDir, configProps);
        whitesourceAgent.updateWhitesource();
    }

    /* --- Private methods --- */

    private static Properties ReadAndValidateConfigFile(String configFilePath) {
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
