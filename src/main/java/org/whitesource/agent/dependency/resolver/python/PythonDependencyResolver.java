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
package org.whitesource.agent.dependency.resolver.python;

import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class PythonDependencyResolver extends AbstractDependencyResolver {

    /* -- Members -- */

    private final String pythonPath;
    private final String pipPath;
    private final Collection<String> excludes = Arrays.asList(Constants.PATTERN + PY_EXT);
    private boolean ignorePipInstallErrors;
    private boolean installVirutalenv;
    private boolean resolveHierarchyTree;
    private String[] pythonRequirementsFileIncludes;

    /* --- Static members --- */

    //private static final String PYTHON_BOM = "requirements.txt";
    private static final String PY_EXT = ".py";
    public static final String WHITESOURCE_PYTHON_TEMP_FOLDER = "Whitesource_python_resolver";

    /* --- Constructors --- */

    public PythonDependencyResolver(String pythonPath, String pipPath, boolean ignorePipInstallErrors,
                                    boolean installVirtualEnv, boolean resolveHierarchyTree, String[] pythonRequirementsFileIncludes) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.ignorePipInstallErrors = ignorePipInstallErrors;
        this.installVirutalenv = installVirtualEnv;
        this.resolveHierarchyTree = resolveHierarchyTree;
        this.pythonRequirementsFileIncludes = pythonRequirementsFileIncludes;
    }

    @Override
    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> requirementsFiles) {
        Collection<DependencyInfo> resultDependencies = new LinkedList<>();
        for (String requirementsTxtPath : requirementsFiles) {
            FilesUtils filesUtils = new FilesUtils();
            String tempDirVirtualEnv = filesUtils.createTmpFolder(true, WHITESOURCE_PYTHON_TEMP_FOLDER);
            String tempDirPackages = filesUtils.createTmpFolder(false, WHITESOURCE_PYTHON_TEMP_FOLDER);

            Collection<DependencyInfo> dependencies = new LinkedList<>();
            PythonDependencyCollector pythonDependencyCollector;
            if (tempDirVirtualEnv != null && tempDirPackages != null) {
                pythonDependencyCollector = new PythonDependencyCollector(this.pythonPath, this.pipPath, this.installVirutalenv, this.resolveHierarchyTree, this.ignorePipInstallErrors,
                        requirementsTxtPath, tempDirPackages, tempDirVirtualEnv);
                String currentTopLevelFolder = requirementsTxtPath.substring(0, requirementsTxtPath.replaceAll("\\\\",
                        Constants.FORWARD_SLASH).lastIndexOf(Constants.FORWARD_SLASH));
                Collection<AgentProjectInfo> projects = pythonDependencyCollector.collectDependencies(currentTopLevelFolder);
                dependencies = projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
                // delete tmp folders
                FilesUtils.deleteDirectory(new File(tempDirVirtualEnv));
                FilesUtils.deleteDirectory(new File(tempDirPackages));
            }
            resultDependencies.addAll(dependencies);
        }
        return new ResolutionResult(resultDependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(PY_EXT));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.PYTHON;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.PYTHON.name();
    }

    @Override
    public String[] getBomPattern() {
        return pythonRequirementsFileIncludes;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    /* --- Getters / Setters --- */

    public String getPythonPath() {
        return pythonPath;
    }

    public String getPipPath() {
        return pipPath;
    }
}

