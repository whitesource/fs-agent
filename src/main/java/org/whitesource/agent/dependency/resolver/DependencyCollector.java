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

import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;

import java.util.ArrayList;
import java.util.Collection;
/**
 * @author eugen.horovitz
 */
public abstract class DependencyCollector {

    public static final String C_CHAR_WINDOWS = "/c";

    protected abstract Collection<AgentProjectInfo> collectDependencies(String folder);

    protected Collection<AgentProjectInfo> getSingleProjectList(Collection<DependencyInfo> dependencies) {
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        AgentProjectInfo projectInfo = new AgentProjectInfo();
        dependencies.stream().forEach(dependency -> projectInfo.getDependencies().add(dependency));
        projects.add(projectInfo);
        return projects;
    }

    public static boolean isWindows() {
        return System.getProperty(Constants.OS_NAME).toLowerCase().contains(Constants.WIN);
    }
}