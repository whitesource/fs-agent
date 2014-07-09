package org.whitesource.fs;

import com.beust.jcommander.Parameter;

/**
 * Author: Itai Marko
 */
public class CommandLineArgs {

    private static final String CONFIG_FILE_NAME = "whitesource-fs-agent.config";

    //TODO use a File converter for dependencyDir and configFilePath
    @Parameter(names = "-d", description = "Path to base directory of dependency files to scan")
    String dependencyDir = "."; // TODO this may be a bad default, consider printing usage instead

    @Parameter(names = "-c", description = "Config file path")
    String configFilePath = CONFIG_FILE_NAME;
}
