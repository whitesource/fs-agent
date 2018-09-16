package org.whitesource.agent.dependency.resolver.dotNet;

import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.nuget.NugetDependencyResolver;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetConfigFileType;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author raz.nitzan
 */
public class DotNetDependencyResolver extends NugetDependencyResolver {

    /* --- Members --- */

    private final DotNetRestoreCollector resolveCollector;
    private boolean nugetRestoreDependencies;

    /* --- Constructor --- */

    public DotNetDependencyResolver(String whitesourceConfiguration, NugetConfigFileType nugetConfigFileType, boolean nugetRestoreDependencies, boolean ignoreSourceFiles) {
        super(whitesourceConfiguration, nugetConfigFileType, nugetRestoreDependencies, ignoreSourceFiles);
        this.nugetRestoreDependencies = nugetRestoreDependencies;
        this.resolveCollector = new DotNetRestoreCollector();
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> csprojFiles) {
        if (this.nugetRestoreDependencies) {
            this.resolveCollector.executeRestore(projectFolder, csprojFiles);
            Collection<AgentProjectInfo> projects = this.resolveCollector.collectDependencies(projectFolder);
            Collection<DependencyInfo> dependencies = projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());
            dependencies.addAll(parseNugetPackageFiles(csprojFiles, true));
            return new ResolutionResult(dependencies, new LinkedList<>(), getDependencyType(), topLevelFolder);
        } else {
            return getResolutionResultFromParsing(topLevelFolder, csprojFiles, false);
        }
    }
}
