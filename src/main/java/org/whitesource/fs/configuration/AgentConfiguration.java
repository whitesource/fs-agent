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
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.FSAConfiguration;

import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.*;
import static org.whitesource.fs.FileSystemAgent.EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX;

public class AgentConfiguration {

    private final String[] includes;
    private final String[] excludes;
    private final int archiveExtractionDepth;
    private final String[] archiveIncludes;
    private final String[] archiveExcludes;
    private final boolean archiveFastUnpack;
    private final boolean followSymlinks;
    private final boolean partialSha1Match;
    private final boolean calculateHints;
    private final boolean calculateMd5;

    private final boolean showProgressBar;
    private final boolean globCaseSensitive;
    private final Collection<String> excludedCopyrights;
    private String error = null;

    public String getError() {
        return error;
    }

    @JsonCreator
    public AgentConfiguration(@JsonProperty(INCLUDES_PATTERN_PROPERTY_KEY) String[] includes,
                              @JsonProperty(EXCLUDES_PATTERN_PROPERTY_KEY) String[] excludes,
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
                              @JsonProperty(EXCLUDED_COPYRIGHT_KEY) Collection<String> excludedCopyrights){
        this.includes = includes;
        this.excludes = excludes;
        this.archiveExtractionDepth = archiveExtractionDepth;
        this.archiveIncludes = archiveIncludes;
        this.archiveExcludes = archiveExcludes;
        this.archiveFastUnpack = archiveFastUnpack;
        this.followSymlinks = followSymlinks;

        this.partialSha1Match = partialSha1Match;
        this.calculateHints = calculateHints;
        this.calculateMd5 = calculateMd5;
        this.showProgressBar = showProgressBar;
        this.globCaseSensitive = globCaseSensitive;
        this.excludedCopyrights = excludedCopyrights;
    }

    public AgentConfiguration(Properties config) {
        this(FSAConfiguration.getIncludes(config),
                config.getProperty(EXCLUDES_PATTERN_PROPERTY_KEY, "").split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX),
                FSAConfiguration.getArchiveDepth(config),
                config.getProperty(ARCHIVE_INCLUDES_PATTERN_KEY, "").split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX),
                config.getProperty(ARCHIVE_EXCLUDES_PATTERN_KEY, "").split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX),
                FSAConfiguration.getBooleanProperty(config, ARCHIVE_FAST_UNPACK_KEY, false),
                FSAConfiguration.getBooleanProperty(config, FOLLOW_SYMBOLIC_LINKS, true),

                FSAConfiguration.getBooleanProperty(config, PARTIAL_SHA1_MATCH_KEY, false),
                FSAConfiguration.getBooleanProperty(config, CALCULATE_HINTS, false),
                FSAConfiguration.getBooleanProperty(config, CALCULATE_MD5, false),
                FSAConfiguration.getBooleanProperty(config, SHOW_PROGRESS_BAR, true),
                getGlobalCaseSensitive(config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY)).getKey(),
                getExcludeCopyrights(config.getProperty(EXCLUDED_COPYRIGHT_KEY, "")));
        error = getGlobalCaseSensitive(config.getProperty(CASE_SENSITIVE_GLOB_PROPERTY_KEY)).getValue();
    }

    private static Collection<String> getExcludeCopyrights(String excludedCopyrightsValue) {
        Collection<String> excludes = new ArrayList<>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludes.remove("");
        return excludes;
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

    private static Pair<Boolean,String> getGlobalCaseSensitive(String globCaseSensitiveValue) {
        boolean globCaseSensitive = false;
        String error = null;
        if (StringUtils.isNotBlank(globCaseSensitiveValue)) {
            if (globCaseSensitiveValue.equalsIgnoreCase("true") || globCaseSensitiveValue.equalsIgnoreCase("y")) {
                globCaseSensitive = true;
                error = null;
            } else if (globCaseSensitiveValue.equalsIgnoreCase("false") || globCaseSensitiveValue.equalsIgnoreCase("n")) {
                globCaseSensitive = false;
                error = null;
            } else {
                error = "Bad " + CASE_SENSITIVE_GLOB_PROPERTY_KEY + ". Received " + globCaseSensitiveValue + ", required true/false or y/n";
            }
        }else{
            error = null;
        }

        return new Pair<>(globCaseSensitive,error);
    }
}
