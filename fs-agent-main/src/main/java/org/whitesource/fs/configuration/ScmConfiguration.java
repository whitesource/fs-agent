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
package org.whitesource.fs.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class ScmConfiguration {

    /* --- Constructors --- */

    @JsonCreator
    public ScmConfiguration(
            @JsonProperty(SCM_TYPE_PROPERTY_KEY) String type,
            @JsonProperty(SCM_USER_PROPERTY_KEY) String user,
            @JsonProperty(SCM_PASS_PROPERTY_KEY) String pass,
            @JsonProperty(SCM_PPK_PROPERTY_KEY) String ppk,
            @JsonProperty(SCM_URL_PROPERTY_KEY) String url,
            @JsonProperty(SCM_BRANCH_PROPERTY_KEY) String branch,
            @JsonProperty(SCM_TAG_PROPERTY_KEY) String tag,
            @JsonProperty(SCM_REPOSITORIES_FILE) String repositoriesPath,
            @JsonProperty(SCM_NPM_INSTALL) boolean npmInstall,
            @JsonProperty(SCM_NPM_INSTALL_TIMEOUT_MINUTES) int npmInstallTimeoutMinutes) {
        this.type = type;
        this.user = user;
        this.pass = pass;
        this.ppk = ppk;
        this.url = url;
        this.branch = branch;
        this.tag = tag;

        //defaults
        this.repositoriesPath = repositoriesPath;
        this.npmInstall = npmInstall;
        this.npmInstallTimeoutMinutes = npmInstallTimeoutMinutes;
    }

    /* --- Members --- */

    private String type;
    private String user;
    private String pass;
    private String ppk;
    private String url;
    private String branch;
    private String tag;

    private String repositoriesPath;
    private boolean npmInstall;
    private int npmInstallTimeoutMinutes;

    /* --- Properties --- */

    @JsonProperty("scm.type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("scm.user")
    public String getUser() {
        return user;
    }


    @JsonProperty(SCM_PASS_PROPERTY_KEY)
    public String getPass() {
        return pass;
    }

    @JsonProperty(SCM_PPK_PROPERTY_KEY)
    public String getPpk() {
        return ppk;
    }

    @JsonProperty(SCM_URL_PROPERTY_KEY)
    public String getUrl() {
        return url;
    }

    @JsonProperty(SCM_BRANCH_PROPERTY_KEY)
    public String getBranch() {
        return branch;
    }

    @JsonProperty(SCM_TAG_PROPERTY_KEY)
    public String getTag() {
        return tag;
    }

    @JsonProperty(SCM_REPOSITORIES_FILE)
    public String getRepositoriesPath() {
        return repositoriesPath;
    }

    @JsonProperty(SCM_NPM_INSTALL)
    public boolean isNpmInstall() {
        return npmInstall;
    }

    @JsonProperty(SCM_NPM_INSTALL_TIMEOUT_MINUTES)
    public int getNpmInstallTimeoutMinutes() {
        return npmInstallTimeoutMinutes;
    }
}