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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.StringUtils;

import java.util.List;

import static org.whitesource.agent.ConfigPropertyKeys.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestConfiguration {

    private final String projectVersion;
    private final String projectToken;
    private final boolean projectPerSubFolder;
    private final String apiToken;
    private final String userKey;
    private final String requesterEmail;
    private final String productToken;
    private String productName;
    private String productVersion;
    private final String projectName;
    private final List<String> appPaths;
    private final String viaDebug;
    private final int viaAnalysisLevel;
    private final String iaLanguage;

    public RequestConfiguration(@JsonProperty(ORG_TOKEN_PROPERTY_KEY) String apiToken,
                                @JsonProperty(USER_KEY_PROPERTY_KEY) String userKey,
                                @JsonProperty(REQUESTER_EMAIL) String requesterEmail,
                                @JsonProperty(PROJECT_PER_SUBFOLDER) boolean projectPerSubFolder,
                                @JsonProperty(PROJECT_NAME_PROPERTY_KEY) String projectName,
                                @JsonProperty(PROJECT_TOKEN_PROPERTY_KEY) String projectToken,
                                @JsonProperty(PROJECT_VERSION_PROPERTY_KEY) String projectVersion,
                                @JsonProperty(PRODUCT_NAME_PROPERTY_KEY) String productName,
                                @JsonProperty(PRODUCT_TOKEN_PROPERTY_KEY) String productToken,
                                @JsonProperty(PRODUCT_VERSION_PROPERTY_KEY) String productVersion,
                                @JsonProperty(APP_PATH) List<String> appPaths,
                                @JsonProperty(VIA_DEBUG) String viaDebug,
                                @JsonProperty(VIA_ANALYSIS_LEVEL) int viaAnalysisLevel,
                                @JsonProperty(IA_LANGUAGE) String  iaLanguage) {
        this.apiToken = apiToken;
        this.userKey = userKey;
        this.requesterEmail = requesterEmail;
        this.projectPerSubFolder = projectPerSubFolder;
        this.projectName = projectName;
        this.projectToken = projectToken;
        this.projectVersion = projectVersion;
        this.productName = productName;
        this.productToken = productToken;
        this.productVersion = productVersion;
        this.viaAnalysisLevel = viaAnalysisLevel;
        this.appPaths = appPaths;
        this.viaDebug=viaDebug;
        this.iaLanguage=iaLanguage;
    }

    @JsonProperty(PROJECT_NAME_PROPERTY_KEY)
    public String getProjectName() {
        return projectName;
    }

    @JsonProperty(PROJECT_VERSION_PROPERTY_KEY)
    public String getProjectVersion() {
        return projectVersion;
    }

    @JsonProperty(PROJECT_TOKEN_PROPERTY_KEY)
    public String getProjectToken() {
        return projectToken;
    }

    @JsonProperty(PRODUCT_TOKEN_PROPERTY_KEY)
    public String getProductToken() {
        return productToken;
    }

    @JsonProperty(PRODUCT_NAME_PROPERTY_KEY)
    public String getProductName() {
        return productName;
    }

    @JsonProperty(PRODUCT_VERSION_PROPERTY_KEY)
    public String getProductVersion() {
        return productVersion;
    }

    @JsonProperty(PROJECT_PER_SUBFOLDER)
    public boolean isProjectPerSubFolder() {
        return projectPerSubFolder;
    }

    @JsonProperty(REQUESTER_EMAIL)
    public String getRequesterEmail() {
        return requesterEmail;
    }

    @JsonProperty(ORG_TOKEN_PROPERTY_KEY)
    public String getApiToken() {
        return apiToken;
    }

    @JsonProperty(APP_PATH)
    public List<String> getAppPaths() {
        return appPaths;
    }

    @JsonProperty(VIA_DEBUG)
    public String getViaDebug() {
        return viaDebug;
    }

    @JsonProperty(VIA_ANALYSIS_LEVEL)
    public int getViaAnalysisLevel() {
        return viaAnalysisLevel;
    }

    @JsonProperty(USER_KEY_PROPERTY_KEY)
    public String getUserKey() { return userKey; }

    @JsonProperty(IA_LANGUAGE)
    public String getIaLanguage() {
        return iaLanguage;
    }

    public String getProductNameOrToken() {
        if (StringUtils.isBlank(getProductToken())) {
            return getProductName();
        }
        return getProductToken();
    }

    @Override
    public String toString() {
        return  ", projectVersion='" + projectVersion + '\n' +
                ", projectToken='" + projectToken + '\n' +
                ", projectPerSubFolder=" + projectPerSubFolder + '\n' +
                ", apiToken='" + "*******" + '\n' +
                ", userKey='" + "*******" + '\n' +
                ", requesterEmail='" + requesterEmail + "'" + '\n' +
                ", productToken='" + productToken + '\n' +
                ", productName='" + productName + '\n' +
                ", productVersion='" + productVersion + '\n' +
                ", projectName='" + projectName;
    }
}