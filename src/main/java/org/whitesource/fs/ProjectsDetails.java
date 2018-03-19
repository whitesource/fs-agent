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
import java.util.HashMap;
import java.util.Map;

public class ProjectsDetails {

//    private Collection<AgentProjectInfo> projects;
    private Map<AgentProjectInfo, String> projectToLanguage;
    private String details;
    private StatusCode statusCode;


    public ProjectsDetails(Map<AgentProjectInfo, String> projectToLanguage, StatusCode statusCode , String details) {
        this.statusCode = statusCode;
        this.projectToLanguage = projectToLanguage;
        this.details = details;
    }

    public ProjectsDetails(Collection<AgentProjectInfo> projects, StatusCode statusCode , String details) {
        Map<AgentProjectInfo, String> projectToLanguage = new HashMap<>();
        for (AgentProjectInfo project : projects) {
            projectToLanguage.put(project, null);
        }
        this.statusCode = statusCode;
        this.projectToLanguage = projectToLanguage;
        this.details = details;
//        this(projects.stream().collect(Collectors.toMap(project->project,null)), statusCode, details);
    }

    public ProjectsDetails() {
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getDetails() {
        return details;
    }

    public Map<AgentProjectInfo, String> getProjectToLanguage() {
        return projectToLanguage;
    }

    public void setProjectToLanguage(Map<AgentProjectInfo, String> projectToLanguage) {
        this.projectToLanguage = projectToLanguage;
    }

    public void addOfflineProjects(Collection<AgentProjectInfo> projects) {
        if (projects.size() > 0){
            for (AgentProjectInfo agentProjectInfo : projects) {
                getProjectToLanguage().put(agentProjectInfo, null);
            }
        }
//            setProjectToLanguage(projects.stream().collect(Collectors.toMap(project-> project, null)));
    }

    public Collection<AgentProjectInfo> getProjects() {
        return getProjectToLanguage().keySet();
    }
}
