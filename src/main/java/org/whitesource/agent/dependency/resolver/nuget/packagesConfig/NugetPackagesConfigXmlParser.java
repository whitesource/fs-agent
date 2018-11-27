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
package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.apache.commons.lang.StringUtils;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.DependencyInfoFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.io.File;
import java.io.Serializable;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author yossi.weinberg
 */
public class NugetPackagesConfigXmlParser implements Serializable {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(NugetPackagesConfigXmlParser.class);

    /* --- Members --- */

    private File xml;

    private NugetConfigFileType nugetConfigFileType;

    /* --- Constructors --- */

    public NugetPackagesConfigXmlParser(File xml, NugetConfigFileType nugetConfigFileType) {
        this.xml = xml;
        this.nugetConfigFileType = nugetConfigFileType;
    }

    /* --- Public methods --- */

    /**
     * Parse packages.config or csproj file
     *
     * @param getDependenciesFromReferenceTag - flag to indicate weather to get dependencies form reference tag or not
     * @return Set of DependencyInfos
     */
    public Set<DependencyInfo> parsePackagesConfigFile(boolean getDependenciesFromReferenceTag, String nugetDependencyFile) {
        Persister persister = new Persister();
        Set<DependencyInfo> dependencies = new HashSet<>();
        try {
            // case of packages.config file
            if (this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE) {
                NugetPackages packages = persister.read(NugetPackages.class, xml);
                if (!getDependenciesFromReferenceTag) {
                    dependencies.addAll(collectDependenciesFromNugetConfig(packages, nugetDependencyFile));
                }
                // case of csproj file
            } else {
                NugetCsprojPackages csprojPackages = persister.read(NugetCsprojPackages.class, xml);
                NugetPackages packages = getNugetPackagesFromCsproj(csprojPackages);
                if (!getDependenciesFromReferenceTag) {
                    dependencies.addAll(collectDependenciesFromNugetConfig(packages, nugetDependencyFile));
                }
                dependencies.addAll(getDependenciesFromReferencesTag(csprojPackages));
            }
            dependencies.stream().forEach(dependencyInfo -> dependencyInfo.setSystemPath(this.xml.getPath()));
        } catch (Exception e) {
            logger.warn("Unable to parse suspected Nuget package configuration file {}", xml, e.getMessage());
        }
        return dependencies;
    }

    private NugetPackages getNugetPackagesFromCsproj(NugetCsprojPackages csprojPackages) {
        List<NugetPackage> nugetPackages = new LinkedList<>();
        for (NugetCsprojItemGroup csprojPackage : csprojPackages.getNugetItemGroups()) {
            for (PackageReference packageReference : csprojPackage.getPackageReference()) {
                if (packageReference != null && packageReference.getPkgName() != null && packageReference.getPkgVersion() != null) {
                    nugetPackages.add(new NugetPackage(packageReference.getPkgName(), packageReference.getPkgVersion()));
                }
            }
        }
        NugetPackages nugetPackagesResult = new NugetPackages();
        nugetPackagesResult.setNugetPackages(nugetPackages);
        return nugetPackagesResult;
    }

    private Set<DependencyInfo> getDependenciesFromReferencesTag(NugetCsprojPackages csprojPackages) {
        Set<DependencyInfo> dependencies = new HashSet<>();
        DependencyInfoFactory dependencyInfoFactory = new DependencyInfoFactory();
        for (NugetCsprojItemGroup csprojPackage : csprojPackages.getNugetItemGroups()) {
            for (ReferenceTag referenceTag : csprojPackage.getReferences()) {
                // Ignore the dependency if the hint path is blank
                if (StringUtils.isNotEmpty(referenceTag.getHintPath())) {
                    Path basePath = FileSystems.getDefault().getPath(this.xml.getPath());
                    Path hintParentResolvedPath = basePath.getParent().resolve(referenceTag.getHintPath());
                    String hintAbsolutePath = hintParentResolvedPath.normalize().toAbsolutePath().toString();
                    File fileFromHintPath = new File(hintAbsolutePath);
                    DependencyInfo dependency = dependencyInfoFactory.createDependencyInfo(fileFromHintPath.getParentFile(), fileFromHintPath.getName());
                    if (dependency != null) {
                        if (StringUtils.isNotEmpty(referenceTag.getVersion())) {
                            dependency.setVersion(referenceTag.getVersion());
                        }
                        dependencies.add(dependency);
                    }
                }
            }
        }
        return dependencies;
    }

    private Set<DependencyInfo> collectDependenciesFromNugetConfig(NugetPackages configNugetPackage, String nugetDependencyFile) {
        Set<DependencyInfo> dependencies = new HashSet<>();
        List<NugetPackage> nugetPackages = configNugetPackage.getNugetPackages();
        if (nugetPackages != null) {
            for (NugetPackage nugetPackage : nugetPackages) {
                if (StringUtils.isNotBlank(nugetPackage.getPkgName()) && StringUtils.isNotBlank(nugetPackage.getPkgVersion())) {
                    DependencyInfo dependency = new DependencyInfo();
                    dependency.setGroupId(nugetPackage.getPkgName());
                    dependency.setArtifactId(nugetPackage.getPkgName());
                    dependency.setVersion(nugetPackage.getPkgVersion());
                    dependency.setDependencyType(DependencyType.NUGET);
                    dependency.setDependencyFile(nugetDependencyFile);
                    dependency.setSystemPath(nugetDependencyFile);
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies;
    }
}
