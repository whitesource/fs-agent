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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackage;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackages;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackagesConfigXmlParser;
import org.whitesource.fs.CommandLineArgs;

import java.io.File;
import java.util.*;

/**
 * @author yossi.weinberg
 */
public class NugetDependencyResolver extends AbstractDependencyResolver{

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(AbstractDependencyResolver.class);

    /* --- Members --- */

    private final String whitesourceConfiguration;

    /* --- Constructor --- */

    public NugetDependencyResolver(String whitesourceConfiguration) {
        super();
        this.whitesourceConfiguration = whitesourceConfiguration;
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, List<String> configFiles) {
        Collection<NugetPackages> allConfigNugetPackages = parseNugetPackageFiles(configFiles);
        Set<DependencyInfo> dependencies = new HashSet<>();

        for (NugetPackages configNugetPackage : allConfigNugetPackages) {
            dependencies.addAll(collectDependenciesFromNugetConfig(configNugetPackage));
        }

        return new ResolutionResult(dependencies, new LinkedList<>(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(".dll", ".exe", ".nupkg", ".cs"));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NUGET;
    }

    @Override
    protected String getBomPattern() {
        return "**/*" + ".config";
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    /* --- Private methods --- */

    private Collection<NugetPackages> parseNugetPackageFiles(List<String> configFilesPath) {
        // get configuration file path
        String whitesourceConfigurationPath = new File(whitesourceConfiguration).getAbsolutePath();

        Collection<NugetPackages> nugetPackages = new ArrayList<>();
        for (String configFilePath : configFilesPath) {
            // don't scan the whitesource configuration file
            if (!whitesourceConfigurationPath.equals(configFilePath)) {
                File configFile = new File(configFilePath);
                // check filename again (just in case)
                if (!configFile.getName().equals(CommandLineArgs.CONFIG_FILE_NAME)) {
                    NugetPackagesConfigXmlParser parser = new NugetPackagesConfigXmlParser(configFile);
                    NugetPackages packagesFromSingleFile = parser.parsePackagesConfigFile();
                    if (packagesFromSingleFile != null) {
                        nugetPackages.add(packagesFromSingleFile);
                    }
                }
            }
        }
        return nugetPackages;
    }

    private Set<DependencyInfo> collectDependenciesFromNugetConfig(NugetPackages configNugetPackage) {
        Set<DependencyInfo> dependencies = new HashSet<>();
        List<NugetPackage> nugetPackages = configNugetPackage.getNugets();
        if (nugetPackages != null) {
            for (NugetPackage nugetPackage : nugetPackages) {
                if (StringUtils.isNotBlank(nugetPackage.getPkgName()) && StringUtils.isNotBlank(nugetPackage.getPkgVersion())) {
                    DependencyInfo dependency = new DependencyInfo();
                    dependency.setGroupId(nugetPackage.getPkgName());
                    dependency.setArtifactId(nugetPackage.getPkgName());
                    dependency.setVersion(nugetPackage.getPkgVersion());
                    dependency.setDependencyType(DependencyType.NUGET);
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies;
    }
}
