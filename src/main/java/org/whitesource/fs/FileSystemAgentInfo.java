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
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.contracts.PluginInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class FileSystemAgentInfo implements PluginInfo {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(FileSystemAgentInfo.class);
    private static final String AGENT_TYPE = "fs-agent";
    private static final String AGENTS_VERSION = "agentsVersion";

    /* --- Members --- */

    private Properties artifactProperties;

    /* --- Constructor --- */

    public FileSystemAgentInfo() {
        this.artifactProperties = getArtifactProperties();
    }

    /* --- Getters --- */

    @Override
    public String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    public String getAgentVersion() {
        return getResource(AGENTS_VERSION);
    }

    @Override
    public String getPluginVersion() {
        return getResource(Constants.VERSION);
    }

    private String getResource(String propertyName) {
        String val = (artifactProperties.getProperty(propertyName));
        if (StringUtils.isNotBlank(val)) {
            return val;
        }
        return Constants.EMPTY_STRING;
    }

    /* --- Private members --- */

    private Properties getArtifactProperties() {
        Properties properties = new Properties();
        try (InputStream stream = Main.class.getResourceAsStream("/project.properties")) {
            properties.load(stream);
        } catch (IOException e) {
            logger.error("Failed to get version ", e);
        }
        return properties;
    }
}
