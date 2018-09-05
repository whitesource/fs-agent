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
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProjectsCalculator {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ProjectsCalculator.class);

    /* --- Public methods --- */

    public ProjectsDetails getAllProjects(FSAConfiguration fsaConfiguration) {

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

        // add directory list to appPath map - defaultKey
        fsaConfiguration.getAppPathsToDependencyDirs().get(FSAConfiguration.DEFAULT_KEY).addAll(files);

        // run the agent
        FileSystemAgent agent = new FileSystemAgent(fsaConfiguration, files);
        // create projects as usual
        return agent.createProjects();
    }
}
