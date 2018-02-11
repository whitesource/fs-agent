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

import org.whitesource.agent.api.model.AgentProjectInfo;

import java.util.Collection;

public class ProjectsDetails {

    private Collection<AgentProjectInfo> projects;
    private String details;
    private StatusCode statusCode;

    public ProjectsDetails(Collection<AgentProjectInfo> projects, StatusCode statusCode , String details) {
        this.statusCode = statusCode;
        this.projects = projects;
        this.details = details;
    }

    public ProjectsDetails() {
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getDetails() {
        return details;
    }

    public void setProjects(Collection<AgentProjectInfo> projects) {
        this.projects = projects;
    }

    public Collection<AgentProjectInfo> getProjects() {
        return projects;
    }
}
