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

import java.io.File;
import java.util.*;

/**
 * Created by yossi.weinberg on 7/26/2017.
 */
public class NugetDependencyResolver extends AbstractDependencyResolver{

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(AbstractDependencyResolver.class);

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, List<String> configFiles) {
        Collection<NugetPackages> allConfigNugetPackages = parseNugetPackageFiles(configFiles);
        Set<DependencyInfo> dependencies = new HashSet<>();

        for (NugetPackages configNugetPackage : allConfigNugetPackages) {
            dependencies.addAll(collectDependenciesFromNugetConfig(configNugetPackage));
        }

        return new ResolutionResult(dependencies, new LinkedList<>());
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
        Collection<NugetPackages> nugetPackages = new ArrayList<>();
        NugetPackagesConfigXmlParser parser;

        for (String configFilePath : configFilesPath) {
            File configFile = new File(configFilePath);
            parser = new NugetPackagesConfigXmlParser(configFile);
            NugetPackages packagesFromSingleFile = parser.parsePackagesConfigFile();

            if (packagesFromSingleFile != null) {
                nugetPackages.add(packagesFromSingleFile);
            }
        }
        return nugetPackages;
    }

    private Set<DependencyInfo> collectDependenciesFromNugetConfig(NugetPackages configNugetPackage) {
        Set<DependencyInfo> dependencies = new HashSet<>();
        for (NugetPackage nugetPackage : configNugetPackage.getNugets()) {
            if (StringUtils.isNotBlank(nugetPackage.getPkgName()) && StringUtils.isNotBlank(nugetPackage.getPkgVersion())) {
                DependencyInfo dependency = new DependencyInfo();
                dependency.setGroupId(nugetPackage.getPkgName());
                dependency.setArtifactId(nugetPackage.getPkgName());
                dependency.setVersion(nugetPackage.getPkgVersion());
                dependency.setDependencyType(DependencyType.NUGET);
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }
}
