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
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.dotNet.RestoreCollector;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class PythonDependencyResolver extends AbstractDependencyResolver {



    /* -- Members -- */

    private final String pythonPath;
    private final String pipPath;
    private final boolean ignoreSourceFiles;
    private final boolean ignorePipEnvInstallErrors;
    private final boolean runPipenvPreStep;
    private final boolean pipenvInstallDevDependencies;
    private Collection<String> excludes = new ArrayList<>();
    private boolean ignorePipInstallErrors;
    private boolean installVirutalenv;
    private boolean resolveHierarchyTree;
    private String[] pythonRequirementsFileIncludes;
    public String PYTHON_REGEX = "\\\\";
    /* --- Static members --- */

    //private static final String PYTHON_BOM = "requirements.txt";
    private static final String PY_EXT = ".py";
    public static final String DIRECT = "_direct";

    /* --- Constructors --- */

    public PythonDependencyResolver(String pythonPath, String pipPath, boolean ignorePipInstallErrors,
                                    boolean installVirtualEnv, boolean resolveHierarchyTree, String[] pythonRequirementsFileIncludes, boolean ignoreSourceFiles, boolean ignorePipEnvInstallErrors, boolean runPipenvPreStep, boolean pipenvInstallDevDependencies) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.ignorePipInstallErrors = ignorePipInstallErrors;
        this.installVirutalenv = installVirtualEnv;
        this.resolveHierarchyTree = resolveHierarchyTree;
        this.pythonRequirementsFileIncludes = pythonRequirementsFileIncludes;
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.ignorePipEnvInstallErrors = ignorePipEnvInstallErrors;
        this.runPipenvPreStep = runPipenvPreStep;
        this.pipenvInstallDevDependencies = pipenvInstallDevDependencies;
    }

    @Override
    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> dependenciesFiles) {

        if (ignoreSourceFiles) {
            this.excludes = Arrays.asList(Constants.PATTERN + PY_EXT);
        }
        FilesUtils filesUtils = new FilesUtils();
        Collection<DependencyInfo> resultDependencies = new LinkedList<>();
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        String pipFilePath = projectFolder + RestoreCollector.BACK_SLASH + Constants.PIPFILE;
        //check if Pipfile exists, then use pipenv, else use pip
        if (Paths.get(pipFilePath).toFile().exists()) {
            resultDependencies = runPipEnvAlgorithm(filesUtils, pipFilePath);
        } else {
            dependencyInfos = runPipAlgorithm(filesUtils, dependenciesFiles);
            resultDependencies.addAll(dependencyInfos);
        }
        return new ResolutionResult(resultDependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private Collection<DependencyInfo> runPipAlgorithm(FilesUtils filesUtils, Set<String> dependenciesFiles) {
        LinkedList<DependencyInfo> resultDependencies = new LinkedList<>();
        for (String dependencyFile : dependenciesFiles) {
            String tempDirVirtualEnv = filesUtils.createTmpFolder(true, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER);
            String tempDirPackages = filesUtils.createTmpFolder(false, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER);
            String tempDirDirectPackages = filesUtils.createTmpFolder(false, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER + DIRECT);
            PythonDependencyCollector pythonDependencyCollector;
            Collection<DependencyInfo> dependencies = new LinkedList<>();
            if (tempDirVirtualEnv != null && tempDirPackages != null) {
                pythonDependencyCollector = new PythonDependencyCollector(this.pythonPath, this.pipPath, this.installVirutalenv, this.resolveHierarchyTree, this.ignorePipInstallErrors,
                        dependencyFile, tempDirPackages, tempDirVirtualEnv, tempDirDirectPackages);
                String currentTopLevelFolder = dependencyFile.substring(0, dependencyFile.replaceAll(PYTHON_REGEX,
                        Constants.FORWARD_SLASH).lastIndexOf(Constants.FORWARD_SLASH));
                Collection<AgentProjectInfo> projects = pythonDependencyCollector.collectDependencies(currentTopLevelFolder);
                dependencies = projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
                // delete tmp folders
                FilesUtils.deleteDirectory(new File(tempDirVirtualEnv));
                FilesUtils.deleteDirectory(new File(tempDirPackages));
                FilesUtils.deleteDirectory(new File(tempDirDirectPackages));
            }
            resultDependencies.addAll(dependencies);
        }
        return resultDependencies;
    }

    private Collection<DependencyInfo> runPipEnvAlgorithm(FilesUtils filesUtils, String pipfilePath) {
        String tempDirPackages = null;
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            tempDirPackages = filesUtils.createTmpFolder(true, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER);
            String dependencyFile = pipfilePath;
            PythonDependencyCollector pythonDependencyCollector;
            pythonDependencyCollector = new PythonDependencyCollector(ignorePipEnvInstallErrors, runPipenvPreStep, tempDirPackages, pythonPath, pipPath, pipenvInstallDevDependencies);
            String currentTopLevelFolder = dependencyFile.substring(0, dependencyFile.replaceAll(PYTHON_REGEX, Constants.FORWARD_SLASH).lastIndexOf(Constants.FORWARD_SLASH));
            Collection<AgentProjectInfo> projects = pythonDependencyCollector.collectDependencies(currentTopLevelFolder);
            dependencies = projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
        } finally {
            if (tempDirPackages != null) {
                FilesUtils.deleteDirectory(new File(tempDirPackages));
            }
        }
        return dependencies;
    }

    @Override
    protected Collection<String> getExcludes() {
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        List<String> stringList = Arrays.asList(pythonRequirementsFileIncludes);
        stringList.stream().forEach(s -> s = Constants.PATTERN + s);
        return stringList;
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
        List<String> stringList = new ArrayList<>(Arrays.asList(pythonRequirementsFileIncludes));
        for (int i = 0; i < stringList.size(); i++) {
            stringList.set(i, Constants.PATTERN + stringList.get(i));
        }
        return stringList.toArray(new String[0]);
    }

    @Override
    public Collection<String> getManifestFiles() {
        return Arrays.asList(pythonRequirementsFileIncludes);
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

    @Override
    protected Collection<String> getRelevantScannedFolders(Collection<String> scannedFolders) {
        // Python resolver should scan all folders and should not remove any folder
        return scannedFolders == null ? Collections.emptyList() : scannedFolders;
    }
}

