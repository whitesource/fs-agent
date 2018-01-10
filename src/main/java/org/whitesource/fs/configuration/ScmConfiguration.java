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
import org.whitesource.fs.FSAConfiguration;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class ScmConfiguration {

    /* --- Constructors --- */

    @JsonCreator
    public ScmConfiguration(
            @JsonProperty("type") String type,
            @JsonProperty("user") String user,
            @JsonProperty("pass") String pass,
            @JsonProperty("ppk") String ppk,
            @JsonProperty("url") String url,
            @JsonProperty("branch") String branch,
            @JsonProperty("tag") String tag) {
        this.type = type;
        this.user = user;
        this.pass = pass;
        this.ppk = ppk;
        this.url = url;
        this.branch = branch;
        this.tag = tag;

        //defaults
        this.repositoriesfile = null;
        this.npmInstall = false;
        this.npmInstallTimeoutMinutes = 1;
    }

    public ScmConfiguration(Properties config) {
        this.type = config.getProperty(SCM_TYPE_PROPERTY_KEY);
        this.url = config.getProperty(SCM_URL_PROPERTY_KEY);
        this.user = config.getProperty(SCM_USER_PROPERTY_KEY);
        this.pass = config.getProperty(SCM_PASS_PROPERTY_KEY);
        this.branch = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        this.tag = config.getProperty(SCM_TAG_PROPERTY_KEY);
        this.ppk = config.getProperty(SCM_BRANCH_PROPERTY_KEY);

        //defaults
        this.repositoriesfile = config.getProperty(SCM_REPOSITORIES_FILE);
        npmInstall = FSAConfiguration.getBooleanProperty(config, SCM_NPM_INSTALL, true);
        npmInstallTimeoutMinutes = FSAConfiguration.getIntProperty(config, SCM_NPM_INSTALL_TIMEOUT_MINUTES, 15);
    }

    /* --- Members --- */

    private String type;
    private String user;
    private String pass;
    private String ppk;
    private String url;
    private String branch;
    private String tag;

    private String repositoriesfile;
    private boolean npmInstall;
    private int npmInstallTimeoutMinutes;

    /* --- Properties --- */

    //@JsonProperty("scm.type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    //@JsonProperty("scm.user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    //@JsonProperty("scm.pass")
    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    //@JsonProperty("scm.ppk")
    public String getPpk() {
        return ppk;
    }

    public void setPpk(String ppk) {
        this.ppk = ppk;
    }

    //@JsonProperty("scm.url")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    //@JsonProperty("scm.branch")
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    //@JsonProperty("scm.tag")
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getRepositoriesfile() {
        return repositoriesfile;
    }

    //@JsonProperty("scm.npmInstall")
    public boolean isNpmInstall() {
        return npmInstall;
    }

    //@JsonProperty("scm.npmInstallTimeoutMinutes")
    public int getNpmInstallTimeoutMinutes() {
        return npmInstallTimeoutMinutes;
    }
}