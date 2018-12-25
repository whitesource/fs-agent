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
package org.whitesource.agent.dependency.resolver.nuget;

import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetConfigFileType;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackagesConfigXmlParser;
import org.whitesource.fs.CommandLineArgs;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yossi.weinberg
 */
public class NugetDependencyResolver extends AbstractDependencyResolver{

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(NugetDependencyResolver.class);
    public static final String CONFIG = ".config";
    public static final String CSPROJ = ".csproj";
    protected static final String CSHARP = ".cs";

    /* --- Private Members --- */

    private final String whitesourceConfiguration;
    private final String bomPattern;
    private final NugetConfigFileType nugetConfigFileType;
    private boolean runPreStep;
    private boolean ignoreSourceFiles;

    /* --- Constructor --- */

    public NugetDependencyResolver(String whitesourceConfiguration, NugetConfigFileType nugetConfigFileType, boolean runPreStep,boolean ignoreSourceFiles) {
        super();
        this.whitesourceConfiguration = whitesourceConfiguration;
        this.nugetConfigFileType = nugetConfigFileType;
        this.runPreStep = runPreStep;
        this.ignoreSourceFiles=ignoreSourceFiles;
        if (this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE) {
            bomPattern = Constants.PATTERN + CONFIG;
        } else {
            bomPattern = Constants.PATTERN + CSPROJ;
        }


    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> configFiles) {
        if (this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE && this.runPreStep) {
            logger.debug("Trying to run pre step on packages.config files");
            NugetRestoreCollector nugetRestoreCollector = new NugetRestoreCollector();
            nugetRestoreCollector.executeRestore(projectFolder, configFiles);
            Collection<AgentProjectInfo> projects = nugetRestoreCollector.collectDependencies(projectFolder);
            Collection<DependencyInfo> dependencies = projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
            return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
        } else {
            return getResolutionResultFromParsing(topLevelFolder, configFiles, false);
        }
    }

    protected ResolutionResult getResolutionResultFromParsing(String topLevelFolder, Set<String> configFiles, boolean onlyDependenciesFromReferenceTag) {
        Collection<DependencyInfo> dependencies = parseNugetPackageFiles(configFiles, onlyDependenciesFromReferenceTag);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        List<String> excludes = new LinkedList<>();
        excludes.add(CommandLineArgs.CONFIG_FILE_NAME);
        if(ignoreSourceFiles) {
            excludes.add(Constants.PATTERN + CSHARP);
        }
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(Constants.DLL, Constants.EXE, Constants.NUPKG, Constants.CS));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NUGET;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.NUGET.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{this.bomPattern};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE ? CONFIG : CSPROJ);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    protected Collection<DependencyInfo> parseNugetPackageFiles(Set<String> nugetDependencyFiles, boolean getDependenciesFromReferenceTag) {
        // get configuration file path
        Set<DependencyInfo> dependencies = new HashSet<>();
        for (String nugetDependencyFile : nugetDependencyFiles) {
            // don't scan the whitesource configuration file
            // sometimes FSA is called from outside and there is no config file
            if (whitesourceConfiguration == null || !new File(whitesourceConfiguration).getAbsolutePath().equals(nugetDependencyFile)) {
                File configFile = new File(nugetDependencyFile);
                // check filename again (just in case)
                if (!configFile.getName().equals(CommandLineArgs.CONFIG_FILE_NAME)) {
                    NugetPackagesConfigXmlParser parser = new NugetPackagesConfigXmlParser(configFile, this.nugetConfigFileType);
                    Set<DependencyInfo> dependenciesFromSingleFile = parser.parsePackagesConfigFile(getDependenciesFromReferenceTag, nugetDependencyFile);
                    if (!dependenciesFromSingleFile.isEmpty()) {
                        dependencies.addAll(dependenciesFromSingleFile);
                    }
                }
            }
        }
        return dependencies;
    }

    @Override
    protected Collection<String> getRelevantScannedFolders(Collection<String> scannedFolders) {
        // Nuget resolver should scan all folders and should not remove any folder
        return scannedFolders == null ? Collections.emptyList() : scannedFolders;
    }
}
