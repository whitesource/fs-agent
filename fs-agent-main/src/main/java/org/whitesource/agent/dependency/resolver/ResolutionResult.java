/**
 * Copyright (C) 2017 WhiteSource Ltd.
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
package org.whitesource.agent.dependency.resolver;

import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by eugen on 6/21/2017.
 */
public class ResolutionResult {

    /* --- Members --- */

    private Map<AgentProjectInfo, Path> resolvedProjects;
    private Collection<String> excludes;
    private final DependencyType dependencyType;
    private final String topLevelFolder;

    /* --- Constructors --- */

    public ResolutionResult(Map<AgentProjectInfo, Path> resolvedProjects, Collection<String> excludes, DependencyType dependencyType, String topLevelFolder) {
        this.resolvedProjects = resolvedProjects;
        this.excludes = excludes;
        this.dependencyType = dependencyType;
        this.topLevelFolder = topLevelFolder;
    }

    public ResolutionResult(Collection<DependencyInfo> dependencies, Iterable<String> excludes, DependencyType dependencyType, String topLevelFolder) {
        AgentProjectInfo projectInfo = new AgentProjectInfo();
        dependencies.forEach(dependencyInfo -> projectInfo.getDependencies().add(dependencyInfo));

        this.resolvedProjects = new HashMap<>();
        this.resolvedProjects.put(projectInfo, Paths.get(topLevelFolder));
        this.excludes = new ArrayList<>();
        this.dependencyType = dependencyType;
        this.topLevelFolder = topLevelFolder;
        excludes.forEach(exclude -> this.excludes.add(exclude));
    }

    /* --- Getters --- */

    public Collection<String> getExcludes() {
        return excludes;
    }

    public Map<AgentProjectInfo, Path> getResolvedProjects() {
        return resolvedProjects;
    }

    public DependencyType getDependencyType() {
        return dependencyType;
    }

    public String getTopLevelFolder() {
        return topLevelFolder;
    }
}