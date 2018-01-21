package org.whitesource.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.fs.configuration.ConfigurationSerializer;

import java.util.*;


/**
 * Created by anna.rozin
 */
public class ComponentScan {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ComponentScan.class);
    public static final String DIRECTORY_NOT_SET = "Directory parameter 'd' is not set" + StatusCode.ERROR;
    public static final String EMPTY_PROJECT_TOKEN = "";
    public static final String SPACE = " ";

    /* --- Members --- */
    private  Properties config;

    /* --- Constructors --- */


    /* --- Methods --- */

    public ComponentScan(Properties config) {
        this.config = config;
    }

    public String scan() {
        logger.info("Starting Analysis - component scan has started");
        String directory = config.getProperty("d");
        String[] directories = directory.split(SPACE);
        ArrayList<String> scannerBaseDirs = new ArrayList<>(Arrays.asList(directories));
        if (!scannerBaseDirs.isEmpty()) {
            logger.info("Getting properties");
            // configure properties
//            List<String> scannerBaseDirs = Collections.singletonList(directory);
            FSAConfiguration fsaConfiguration = new FSAConfiguration(config);
            Collection<AgentProjectInfo> projects = new FileSystemScanner(fsaConfiguration.getResolver(), fsaConfiguration.getAgent())
                    .createProjects(scannerBaseDirs,false);
            logger.info("Finished dependency resolution");
            for (AgentProjectInfo project : projects) {
//                project.setCoordinates(new Coordinates());
                project.setProjectToken(EMPTY_PROJECT_TOKEN);
            }
//             Return dependencies
            String jsonString = ConfigurationSerializer.getAsString(projects,true);
            return jsonString;
        } else
            return "";// new ConfigurationSerializer<>().getAsString(new Collection<AgentProjectInfo>);
    }
}

