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

import org.whitesource.agent.AppPathLanguageDependenciesToVia;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.utils.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class ProjectsDetails {

//    private Collection<AgentProjectInfo> projects;
    private Map<AgentProjectInfo, LinkedList<AppPathLanguageDependenciesToVia>> projectToAppPathAndLanguage;
    private String details;
    private StatusCode statusCode;


    public ProjectsDetails(Map<AgentProjectInfo, LinkedList<AppPathLanguageDependenciesToVia>> projectToAppPathAndLanguage, StatusCode statusCode , String details) {
        this.statusCode = statusCode;
        this.projectToAppPathAndLanguage = projectToAppPathAndLanguage;
        this.details = details;
    }

    public ProjectsDetails(Collection<AgentProjectInfo> projects, StatusCode statusCode , String details) {
        Map<AgentProjectInfo, LinkedList<AppPathLanguageDependenciesToVia>> projectToAppPathAndLanguage = new HashMap<>();
        for (AgentProjectInfo project : projects) {
            projectToAppPathAndLanguage.put(project, new LinkedList<>());
        }
        this.statusCode = statusCode;
        this.projectToAppPathAndLanguage = projectToAppPathAndLanguage;
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

    public Map<AgentProjectInfo, LinkedList<AppPathLanguageDependenciesToVia>> getProjectToAppPathAndLanguage() {
        return projectToAppPathAndLanguage;
    }

    public void setProjectToAppPathAndLanguage(Map<AgentProjectInfo, LinkedList<AppPathLanguageDependenciesToVia>> projectToAppPathAndLanguage) {
        this.projectToAppPathAndLanguage = projectToAppPathAndLanguage;
    }

    public void addOfflineProjects(Collection<AgentProjectInfo> projects) {
        if (projects.size() > 0){
            for (AgentProjectInfo agentProjectInfo : projects) {
                getProjectToAppPathAndLanguage().put(agentProjectInfo, null);
            }
        }
//            setProjectToLanguage(projects.stream().collect(Collectors.toMap(project-> project, null)));
    }

    public Collection<AgentProjectInfo> getProjects() {
        return getProjectToAppPathAndLanguage().keySet();
    }
}
