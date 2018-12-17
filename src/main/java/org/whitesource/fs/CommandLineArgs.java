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
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.CommaParameterSplitter;
 import org.whitesource.agent.Constants;

import java.util.LinkedList;
import java.util.List;

/**
 * Author: Itai Marko
 */
public class CommandLineArgs {

    /* --- Static members --- */

    public static final String CONFIG_FILE_NAME = "whitesource-fs-agent.config";

    /* --- Parameters --- */

    @Parameter(names = "-c", description = "Config file path")
    String configFilePath = CONFIG_FILE_NAME;

    //TODO use a File converter for dependencyDir and configFilePath
    @Parameter(names = "-d", splitter = CommaParameterSplitter.class, description = "Comma separated list of directories and/or files to scan")
    List<String> dependencyDirs = new LinkedList<>(); // TODO this may be a bad default, consider printing usage instead

    @Parameter(names = "-f", description = "File list path")
    String fileListPath = Constants.EMPTY_STRING;

    @Parameter(names = "-apiKey", description = "Organization api key")
    String apiKey = null;

    @Parameter(names = "-product", description = "Product name or token")
    String product = null;

    @Parameter(names = "-productVersion", description = "Product version")
    String productVersion = null;

    @Parameter(names = "-project", description = "Project name or token")
    String project = null;

    @Parameter(names = "-projectVersion", description = "Project version")
    String projectVersion = null;

    @Parameter(names = "-proxy.host", description = "Proxy Host")
    String proxyHost = null;

    @Parameter(names = "-proxy.port", description = "Proxy Port")
    String proxyPort = null;

    @Parameter(names = "-proxy.user", description = "Proxy User")
    String proxyUser = null;

    @Parameter(names = "-proxy.pass", description = "Proxy Password")
    String proxyPass = null;

    @Parameter(names = "-proxy", description = "Proxy info in format: scheme://<user>:<password>@host:port/")
    String proxy = null;

    @Parameter(names = "-archiveFastUnpack", description = "Fast unpack")
    String archiveFastUnpack = "false";

    @Parameter(names = "-requestFiles", description = "Comma separated list of paths to offline request files")
    List<String> requestFiles = new LinkedList<>();

    @Parameter(names = "-projectPerFolder", description = "Creates a project for each subfolder, the subfolder's name is used as the project name")
    String projectPerFolder = Constants.EMPTY_STRING;

    @Parameter(names = "-updateType", description = "Specify if the project dependencies should be removed before adding the new ones")
    String updateType = Constants.EMPTY_STRING;

    @Parameter(names = "-scm.repositoriesFile", description = "Specify the csv file from which scm repositories should be loaded")
    String repositoriesFile = null;

    @Parameter(names = "-offline", description = "Whether or not to create an offline update request instead of sending one to WhiteSource")
    String offline = null;

    @Parameter(names = "-web", description = "Whether or not to create a web service on startup that receives requests from outside")
    String web = "false";

    @Parameter(names = "-whiteSourceFolderPath", description = "WhiteSource folder path for offlineRequest/checkPolicies")
    String whiteSourceFolder = Constants.EMPTY_STRING;

    @Parameter(names = "-appPath", description = "Impact Analysis application path")
    List<String> appPath = new LinkedList<>();

    @Parameter(names = "-xPaths", description = "Path to impact Analysis application paths and directories")
    String xPaths = null;

    @Parameter(names = "-viaDebug", description = "Impact Analysis debug flag")
    String viaDebug = null;

    @Parameter(names = "-viaLevel", description = "Impact Analysis level")
    String viaLevel = "1";

    @Parameter(names = "-enableImpactAnalysis", description = "Whether or not to enable impact analysis")
    String enableImpactAnalysis = null;

    @Parameter(names = "-iaLanguage", description = "Impact analysis language")
    String iaLanguage = null;

    @Parameter(names = "-userKey", description = "user key uniquely identifying the account at white source")
    String userKey = null;

    @Parameter(names = "-sendLogsToWss", description = "whether to send logs to WhiteSource or not")
    String sendLogsToWss = null;

    @Parameter(names = "-scanComment", description = "scan comment")
    String scanComment = null;


    @Parameter(names = "-projectToken", description = "API token to match an existing WhiteSource project")
    String projectToken = null;

    @Parameter(names = "-productToken", description = "Unique identifier of the product to update")
    String productToken = null;

    @Parameter(names = "-logLevel", description = "log level of the project")
    String logLevel = null;

    @Parameter(names = "-requirementsFileIncludes", description = "List of dependency files split by comma")
    List<String> requirementsFileIncludes = new LinkedList<>();

    @Parameter(names = "-logContext", description = "Context id for logger")
    String logContext = null;

    @Parameter(names = "-requireKnownSha1", description = "User-entry of a flag that overrides default FSA process termination when sha1 is missing in case of via")
    String requireKnownSha1 = null;

    @Parameter(names = "-analyzeMultiModule", description = "The parameter instructs the FSA to inspect the structure of a specified multi-module" +
            " and save the project name for each sub-module in a setup file")
    String analyzeMultiModule = null;

    @Parameter(names = "-xModulePath", description = "The parameter get setup file and read the appPaths and -d parameter for via")
    String xModulePath = null;

    @Parameter(names = "-docker.scanImages", description = "Boolean Parameter, decides if to scan docker images if true or given folder if false")
    String scanDockerImages = null;


    @Parameter(names = "-addSha1", description = "for developement only; false by default")
    String addSha1 = null;

    @Parameter(names = "-wss.url", description = "URL to send the request to")
    String wssUrl = null;

    @Parameter(names = "-noConfig", description = "Run without a config file")
    String noConfig = null;

    /* --- Public methods --- */

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void parseCommandLine(String[] args) {
        JCommander jCommander = new JCommander();
        jCommander.setCaseSensitiveOptions(false);
        jCommander.addObject(this);
        jCommander.parse(args);
    }

}