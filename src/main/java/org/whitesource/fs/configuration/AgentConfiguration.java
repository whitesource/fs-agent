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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.whitesource.agent.utils.WsStringUtils;
import org.whitesource.fs.FSAConfigProperty;

import java.util.Collection;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class AgentConfiguration {

    public static final String ERROR = "error";
    @FSAConfigProperty
    private final String[] includes;
    @FSAConfigProperty
    private final String[] excludes;
    @FSAConfigProperty
    private final String[] dockerIncludes;
    @FSAConfigProperty
    private final String[] dockerExcludes;
    @FSAConfigProperty
    private final String[] pythonRequirementsFileIncludes;
    @FSAConfigProperty
    private final int archiveExtractionDepth;
    @FSAConfigProperty
    private final String[] archiveIncludes;
    @FSAConfigProperty
    private final String[] archiveExcludes;
    private final boolean archiveFastUnpack;
    @FSAConfigProperty
    private final boolean followSymlinks;
    private final boolean partialSha1Match;
    private final boolean calculateHints;
    private final boolean calculateMd5;
    @FSAConfigProperty
    private final boolean dockerScan;
    private final boolean showProgressBar;
    @FSAConfigProperty
    private final boolean globCaseSensitive;
    private final Collection<String> excludedCopyrights;
    @FSAConfigProperty
    private final String[] projectPerFolderIncludes;
    @FSAConfigProperty
    private final String[] projectPerFolderExcludes;
    private final String error;

    @JsonProperty(ERROR)
    public String getError() {
        return error;
    }

    @JsonCreator
    public AgentConfiguration(@JsonProperty(INCLUDES_PATTERN_PROPERTY_KEY) String[] includes,
                              @JsonProperty(EXCLUDES_PATTERN_PROPERTY_KEY) String[] excludes,
                              @JsonProperty(DOCKER_INCLUDES_PATTERN_PROPERTY_KEY) String[] dockerIncludes,
                              @JsonProperty(DOCKER_EXCLUDES_PATTERN_PROPERTY_KEY) String[] dockerExcludes,
                              @JsonProperty(ARCHIVE_EXTRACTION_DEPTH_KEY) int archiveExtractionDepth,
                              @JsonProperty(ARCHIVE_INCLUDES_PATTERN_KEY) String[] archiveIncludes,
                              @JsonProperty(ARCHIVE_EXCLUDES_PATTERN_KEY) String[] archiveExcludes,
                              @JsonProperty(ARCHIVE_FAST_UNPACK_KEY) boolean archiveFastUnpack,
                              @JsonProperty(FOLLOW_SYMBOLIC_LINKS) boolean followSymlinks,
                              @JsonProperty(PARTIAL_SHA1_MATCH_KEY) boolean partialSha1Match,
                              @JsonProperty(CALCULATE_HINTS) boolean calculateHints,
                              @JsonProperty(CALCULATE_MD5) boolean calculateMd5,
                              @JsonProperty(SHOW_PROGRESS_BAR) boolean showProgressBar,
                              @JsonProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY) boolean globCaseSensitive,
                              @JsonProperty(SCAN_DOCKER_IMAGES) boolean dockerScan,
                              @JsonProperty(EXCLUDED_COPYRIGHT_KEY) Collection<String> excludedCopyrights,
                              @JsonProperty(PROJECT_PER_FOLDER_INCLUDES) String[] projectPerFolderIncludes,
                              @JsonProperty(PROJECT_PER_FOLDER_EXCLUDES) String[] projectPerFolderExcludes,
                              @JsonProperty(PYTHON_REQUIREMENTS_FILE_INCLUDES) String[] pythonRequirementsFileIncludes,
                              @JsonProperty(ERROR) String error) {
        this.includes = includes == null ? new String[0] : includes;
        this.excludes = excludes == null ? new String[0] : excludes;
        this.dockerIncludes = dockerIncludes == null ? new String[0] : dockerIncludes;
        this.dockerExcludes = dockerExcludes == null ? new String[0] : dockerExcludes;
        this.archiveExtractionDepth = archiveExtractionDepth;
        this.archiveIncludes = archiveIncludes == null ? new String[0] : archiveIncludes;
        this.archiveExcludes = archiveExcludes == null ? new String[0] : archiveExcludes;
        this.archiveFastUnpack = archiveFastUnpack;
        this.followSymlinks = followSymlinks;
        this.dockerScan = dockerScan;
        this.partialSha1Match = partialSha1Match;
        this.calculateHints = calculateHints;
        this.calculateMd5 = calculateMd5;
        this.showProgressBar = showProgressBar;
        this.globCaseSensitive = globCaseSensitive;
        this.error = error;
        this.excludedCopyrights = excludedCopyrights;
        this.projectPerFolderIncludes = projectPerFolderIncludes;
        this.projectPerFolderExcludes = projectPerFolderExcludes;
        this.pythonRequirementsFileIncludes = pythonRequirementsFileIncludes == null ? new String[0] : pythonRequirementsFileIncludes;
    }

    @JsonProperty(SHOW_PROGRESS_BAR)
    public boolean isShowProgressBar() {
        return showProgressBar;
    }

    @JsonProperty(EXCLUDED_COPYRIGHT_KEY)
    public Collection<String> getExcludedCopyrights() {
        return excludedCopyrights;
    }

    @JsonProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY)
    public boolean getGlobCaseSensitive() {
        return globCaseSensitive;
    }

    @JsonProperty(INCLUDES_PATTERN_PROPERTY_KEY)
    public String[] getIncludes() {
        return includes;
    }

    @JsonProperty(EXCLUDES_PATTERN_PROPERTY_KEY)
    public String[] getExcludes() {
        return excludes;
    }

    @JsonProperty(ARCHIVE_EXTRACTION_DEPTH_KEY)
    public int getArchiveExtractionDepth() {
        return archiveExtractionDepth;
    }

    @JsonProperty(ARCHIVE_INCLUDES_PATTERN_KEY)
    public String[] getArchiveIncludes() {
        return archiveIncludes;
    }

    @JsonProperty(ARCHIVE_EXCLUDES_PATTERN_KEY)
    public String[] getArchiveExcludes() {
        return archiveExcludes;
    }

    @JsonProperty(PYTHON_REQUIREMENTS_FILE_INCLUDES)
    public String[] getPythonRequirementsFileIncludes() {
        return pythonRequirementsFileIncludes;
    }

    @JsonProperty(ARCHIVE_FAST_UNPACK_KEY)
    public boolean isArchiveFastUnpack() {
        return archiveFastUnpack;
    }

    @JsonProperty(FOLLOW_SYMBOLIC_LINKS)
    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    @JsonProperty(PARTIAL_SHA1_MATCH_KEY)
    public boolean isPartialSha1Match() {
        return partialSha1Match;
    }

    @JsonProperty(CALCULATE_HINTS)
    public boolean isCalculateHints() {
        return calculateHints;
    }

    @JsonProperty(CALCULATE_MD5)
    public boolean isCalculateMd5() {
        return calculateMd5;
    }

    @JsonProperty(DOCKER_INCLUDES_PATTERN_PROPERTY_KEY)
    public String[] getDockerIncludes() {
        return dockerIncludes;
    }

    @JsonProperty(DOCKER_EXCLUDES_PATTERN_PROPERTY_KEY)
    public String[] getDockerExcludes() {
        return dockerExcludes;
    }

    @JsonProperty(SCAN_DOCKER_IMAGES)
    public boolean isDockerScan() {
        return dockerScan;
    }

    @JsonProperty(PROJECT_PER_FOLDER_INCLUDES)
    public String[] getProjectPerFolderIncludes() {
        return projectPerFolderIncludes;
    }

    @JsonProperty(PROJECT_PER_FOLDER_EXCLUDES)
    public String[] getProjectPerFolderExcludes() {
        return projectPerFolderExcludes;
    }


    @Override
    public String toString() {
        return WsStringUtils.toString(this);
    }
}
