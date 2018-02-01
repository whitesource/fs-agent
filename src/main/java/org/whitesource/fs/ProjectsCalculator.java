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
package org.whitesource.fs;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class ProjectsCalculator {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ProjectsCalculator.class);
    private static final String UTF_8 = "UTF-8";
    private static final String EMPTY_STRING = "";

    /* --- Public methods --- */

    public ProjectsDetails getAllProjects(FSAConfiguration fsaConfiguration) {
        // read log level from configuration file
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        String logLevel = fsaConfiguration.getLogLevel();
        root.setLevel(Level.toLevel(logLevel, Level.INFO));

        // read directories and files from list-file
        List<String> files = new ArrayList<>();
        if (StringUtils.isNotBlank(fsaConfiguration.getFileListPath())) {
            try {
                File listFile = new File(fsaConfiguration.getFileListPath());
                if (listFile.exists()) {
                    files.addAll(FileUtils.readLines(listFile));
                }
            } catch (IOException e) {
                logger.warn("Error reading list file");
            }
        }

        // read csv directory list
        files.addAll(fsaConfiguration.getDependencyDirs());

        // run the agent
        FileSystemAgent agent = new FileSystemAgent(fsaConfiguration, files);
        //Collection<AgentProjectInfo> projects = agent.createProjects();

        Collection<AgentProjectInfo> projects = getAgentProjectsFromRequests(fsaConfiguration);
        if (fsaConfiguration.getUseCommandLineProjectName()) {
            setProjectNamesFromCommandLine(projects, fsaConfiguration.getRequest().getProjectName());
        }
        // create projects as usual
        ProjectsDetails createdProjects = agent.createProjects();

        List<String> offlineRequestFiles = fsaConfiguration.getOfflineRequestFiles();
        if (offlineRequestFiles ==  null || offlineRequestFiles.size() == 0) {
            projects.addAll(createdProjects.getProjects());

        }

        return new ProjectsDetails(projects, createdProjects.getStatusCode(), createdProjects.getDetails());
    }

    /* --- Private methods --- */

    private void setProjectNamesFromCommandLine(Collection<AgentProjectInfo> projects, String projectName) {
    // change project name from command line in case the user sent name via commandLine
        if(projects.size() == 1 && projectName != null) {
            for (AgentProjectInfo project : projects) {
                project.getCoordinates().setArtifactId(projectName);
            }
        }
    }

    private Collection<AgentProjectInfo> getAgentProjectsFromRequests(FSAConfiguration fsaConfiguration) {
        Collection<AgentProjectInfo> projects = new LinkedList<>();

        List<File> requestFiles = new LinkedList<>();
        List<String> offlineRequestFiles = fsaConfiguration.getOfflineRequestFiles();
        if (offlineRequestFiles != null) {
            for (String requestFilePath : offlineRequestFiles) {
                if (StringUtils.isNotBlank(requestFilePath)) {
                    requestFiles.add(new File(requestFilePath));
                }
            }
        }
        if (!requestFiles.isEmpty()) {
            for (File requestFile : requestFiles) {
                if (!requestFile.isFile()) {
                    logger.warn("'{}' is a folder. Enter a valid file path, folder is not acceptable.", requestFile.getName());
                    continue;
                }
                Gson gson = new Gson();
                UpdateInventoryRequest updateRequest;
                logger.debug("Converting offline request to JSON");
                try {
                    updateRequest = gson.fromJson(new JsonReader(new FileReader(requestFile)), new TypeToken<UpdateInventoryRequest>() {
                    }.getType());
                    logger.info("Reading information from request file {}", requestFile);
                    projects.addAll(updateRequest.getProjects());
                    // updating the product name and version from the offline file
                    if (!fsaConfiguration.getUseCommandLineProductName()) {
                        fsaConfiguration.getRequest().setProductName(updateRequest.product());
                        fsaConfiguration.getRequest().setProductVersion(updateRequest.productVersion());
                    }
                } catch (JsonSyntaxException e) {
                    // try to decompress file content
                    try {
                        logger.debug("Decompressing zipped offline request");
                        String fileContent = decompress(requestFile);
                        logger.debug("Converting offline request to JSON");
                        updateRequest = gson.fromJson(fileContent, new TypeToken<UpdateInventoryRequest>() {}.getType());
                        logger.info("Reading information from request file {}", requestFile);
                        projects.addAll(updateRequest.getProjects());
                    } catch (IOException ioe) {
                        logger.warn("Error parsing request: " + ioe.getMessage());
                    } catch (JsonSyntaxException jse) {
                        logger.warn("Error parsing request: " + jse.getMessage());
                    }
                } catch (FileNotFoundException e) {
                    logger.warn("Error parsing request: " + e.getMessage());
                }
            }
        }
        return projects;
    }

    private static String decompress(File file) throws IOException {
        if (file == null || !file.exists()) {
            return EMPTY_STRING;
        }

        byte[] bytes = Base64.getDecoder().decode(IOUtils.toByteArray(new FileInputStream(file)));
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
        BufferedReader bf = new BufferedReader(new InputStreamReader(gzipInputStream, UTF_8));
        StringBuilder outStr = new StringBuilder(EMPTY_STRING);
        String line;
        while ((line = bf.readLine()) != null) {
            outStr.append(line);
        }
        return outStr.toString();
    }

}
