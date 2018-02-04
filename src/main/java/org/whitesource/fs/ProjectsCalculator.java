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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectsCalculator {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ProjectsCalculator.class);

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

        OfflineReader offlineReader = new OfflineReader();
        Collection<AgentProjectInfo> projects = offlineReader.getAgentProjectsFromRequests(fsaConfiguration);
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
}
