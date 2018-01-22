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
import org.whitesource.fs.FSAConfiguration;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

public class OfflineConfiguration {

    public static final String WHITE_SOURCE_DEFAULT_FOLDER_PATH = ".";

    private final boolean enabled;
    private final boolean zip;
    private final boolean prettyJson;
    private final String whiteSourceFolderPath;

    @JsonCreator
    public OfflineConfiguration(
            @JsonProperty(OFFLINE_PROPERTY_KEY) boolean enabled,
            @JsonProperty(OFFLINE_ZIP_PROPERTY_KEY) boolean zip,
            @JsonProperty(OFFLINE_PRETTY_JSON_KEY) boolean prettyJson,
            @JsonProperty(WHITESOURCE_CONFIGURATION) String whiteSourceFolderPath) {
        this.enabled = enabled;
        this.zip = zip;
        this.prettyJson = prettyJson;
        this.whiteSourceFolderPath = whiteSourceFolderPath;
    }

    public OfflineConfiguration(Properties config) {
        enabled = FSAConfiguration.getBooleanProperty(config, OFFLINE_PROPERTY_KEY, false);
        zip = FSAConfiguration.getBooleanProperty(config, OFFLINE_ZIP_PROPERTY_KEY, false);
        prettyJson = FSAConfiguration.getBooleanProperty(config, OFFLINE_PRETTY_JSON_KEY, false);

        String wsFolder = config.getProperty(WHITESOURCE_FOLDER_PATH);
        if (StringUtils.isBlank(wsFolder)) {
            whiteSourceFolderPath = WHITE_SOURCE_DEFAULT_FOLDER_PATH;
        } else {
            whiteSourceFolderPath = wsFolder;
        }
    }

    @JsonProperty(OFFLINE_PROPERTY_KEY)
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty(OFFLINE_PRETTY_JSON_KEY)
    public boolean isPrettyJson() {
        return prettyJson;
    }

    @JsonProperty(OFFLINE_ZIP_PROPERTY_KEY)
    public boolean isZip() {
        return zip;
    }

    @JsonProperty(WHITESOURCE_CONFIGURATION)
    public String getWhiteSourceFolderPath() {
        return whiteSourceFolderPath;
    }
}
