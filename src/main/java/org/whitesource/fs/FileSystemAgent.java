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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.CommandLineAgent;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.scm.ScmConnector;

import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * File System Agent.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class FileSystemAgent extends CommandLineAgent {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAgent.class);

    private static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";
    private static final String EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX = ",";
    private static final int DEFAULT_ARCHIVE_DEPTH = 0;

    private static final String AGENT_TYPE = "fs-agent";
    private static final String AGENT_VERSION = "2.4.1";
    private static final String PLUGIN_VERSION = "1.8.9-SNAPSHOT";

    /* --- Members --- */

    private final List<String> dependencyDirs;

    /* --- Constructors --- */

    public FileSystemAgent(Properties config, List<String> dependencyDirs) {
        super(config);
        this.dependencyDirs = dependencyDirs;
    }

    /* --- Overridden methods --- */

    @Override
    protected Collection<AgentProjectInfo> createProjects() {
        AgentProjectInfo projectInfo = new AgentProjectInfo();
        // use token or name + version
        String projectToken = config.getProperty(PROJECT_TOKEN_PROPERTY_KEY);
        if (StringUtils.isNotBlank(projectToken)) {
            projectInfo.setProjectToken(projectToken);
        } else {
            String projectName = config.getProperty(PROJECT_NAME_PROPERTY_KEY);
            String projectVersion = config.getProperty(PROJECT_VERSION_PROPERTY_KEY);
            projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
        }

        projectInfo.setDependencies(getDependencyInfos());

        Collection<AgentProjectInfo> projects = new ArrayList<AgentProjectInfo>();
        // don't use Arrays.asList, might be removed later if no dependencies
        projects.add(projectInfo);
        return projects;
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getAgentVersion() {
        return AGENT_VERSION;
    }

    @Override
    protected String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    /* --- Private methods --- */

    private List<DependencyInfo> getDependencyInfos() {
        List<String> scannerBaseDirs = dependencyDirs;

        // create scm connector
        String scmType = config.getProperty(SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(SCM_URL_PROPERTY_KEY);
        String username = config.getProperty(SCM_USER_PROPERTY_KEY);
        String password = config.getProperty(SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(SCM_TAG_PROPERTY_KEY);
        String privateKey = config.getProperty(SCM_PPK_PROPERTY_KEY);
        ScmConnector scmConnector = ScmConnector.create(scmType, url, privateKey, username, password, branch, tag);
        if (scmConnector != null) {
            logger.info("Connecting to SCM");
            scannerBaseDirs.clear();
            scannerBaseDirs.add(scmConnector.cloneRepository().getPath());
        }

        // read all properties
        final String[] includes = config.getProperty(INCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String[] excludes = config.getProperty(EXCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final int archiveExtractionDepth = getIntProperty(ARCHIVE_EXTRACTION_DEPTH_KEY, DEFAULT_ARCHIVE_DEPTH);
        final String[] archiveIncludes = config.getProperty(ARCHIVE_INCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String[] archiveExcludes = config.getProperty(ARCHIVE_EXCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final boolean archiveFastUnpack = getBooleanProperty(ARCHIVE_FAST_UNPACK_KEY, false);
        boolean followSymlinks = getBooleanProperty(FOLLOW_SYMBOLIC_LINKS, true);
        // check scan partial sha1s (false by default)
        boolean partialSha1Match = getBooleanProperty(PARTIAL_SHA1_MATCH_KEY, false);

        boolean calculateHints = getBooleanProperty(CALCULATE_HINTS, false);
        boolean calculateMd5 = getBooleanProperty(CALCULATE_MD5, false);

        // glob case sensitive
        final String globCaseSensitiveValue = config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY);
        boolean globCaseSensitive = false;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase("true") || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
            } else if (globCaseSensitiveValue.equalsIgnoreCase("false") || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
            } else {
                logger.error("Bad {}. Received {}, required true/false or y/n", CASE_SENSITIVE_GLOB_PROPERTY_KEY, globCaseSensitiveValue);
                if (scmConnector != null) {
                    scmConnector.deleteCloneDirectory();
                }
                System.exit(-1); // TODO this is within a try frame. Throw an exception instead
            }
        }

        final String excludedCopyrightsValue = config.getProperty(EXCLUDED_COPYRIGHT_KEY, "");
        // get excluded copyrights
        Collection<String> excludedCopyrights = new ArrayList<String>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludedCopyrights.remove("");

        boolean showProgressBar = getBooleanProperty(SHOW_PROGRESS_BAR, true);
        return new FileSystemScanner(showProgressBar, new DependencyResolutionService(config)).createDependencies(
                scannerBaseDirs, scmConnector, includes, excludes, globCaseSensitive, archiveExtractionDepth,
                archiveIncludes, archiveExcludes, archiveFastUnpack, followSymlinks, excludedCopyrights,
                partialSha1Match, calculateHints, calculateMd5);
    }
}