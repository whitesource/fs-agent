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

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * @author yossi.weinberg
 */
public class NugetPackagesConfigXmlParser implements Serializable{

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(NugetPackagesConfigXmlParser.class);

    /* --- Members --- */

    private File xml;

    private NugetConfigFileType nugetConfigFileType;

    /* --- Constructors --- */

    public NugetPackagesConfigXmlParser(File xml, NugetConfigFileType nugetConfigFileType) {
        this.xml = xml;
        this.nugetConfigFileType = nugetConfigFileType;
    }

    /* --- Public methods --- */

    public NugetPackages parsePackagesConfigFile() {
        Persister persister = new Persister();
        NugetPackages packages = null;
        try {
            if (this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE) {
                packages = persister.read(NugetPackages.class, xml);
            } else {
                NugetCsprojPackages csprojPackages = persister.read(NugetCsprojPackages.class, xml);
                packages = getNugetPackagesFromCsproj(csprojPackages);
            }
        } catch (Exception e) {
            logger.warn("Unable to parse suspected Nuget package configuration file {}", xml, e.getMessage());
        }
        return packages;
    }

    private NugetPackages getNugetPackagesFromCsproj(NugetCsprojPackages csprojPackages) {
        List<NugetPackage> nugetPackages = new LinkedList<>();
        for (NugetCsprojItemGroup csprojPackage : csprojPackages.getNugetItemGroups()) {
            for(PackageReference packageReference : csprojPackage.getPackageReference()) {
                if (packageReference != null && packageReference.getPkgName() != null && packageReference.getPkgVersion() != null) {
                    nugetPackages.add(new NugetPackage(packageReference.getPkgName(), packageReference.getPkgVersion()));
                }
            }
        }
        NugetPackages nugetPackagesResult = new NugetPackages();
        nugetPackagesResult.setNugetPackages(nugetPackages);
        return nugetPackagesResult;
    }
}
