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

/**
 * Author: Eugen.Horovitz
 */
public class ScmConfiguration {

    /* --- Constructors --- */

    public ScmConfiguration() {
    }

    public ScmConfiguration(String type, String user, String pass, String ppk, String url, String branch, String tag) {
        this.type = type;
        this.user = user;
        this.pass = pass;
        this.ppk = ppk;
        this.url = url;
        this.branch = branch;
        this.tag = tag;
    }

    /* --- Members --- */

    private String type;
    private String user;
    private String pass;
    private String ppk;
    private String url;
    private String branch;
    private String tag;

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

}