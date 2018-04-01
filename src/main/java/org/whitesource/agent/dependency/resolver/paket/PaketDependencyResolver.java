package org.whitesource.agent.dependency.resolver.paket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author raz.nitzan
 */
public class PaketDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final String PAKET_DEPENDENCIES = "paket.dependencies";
    private static final String PAKET_COMMAND = "paket";
    private static final String SHOW_INSTALLED_PACKAGES_COMMAND = "show-installed-packages";
    private static final String DLL = ".dll";
    private static final String EXE = ".exe";
    private static final String NUPKG = ".nupkg";
    private static final String CS = ".cs";
    private static final String JS = ".js";
    private final String PAKET_LOCK = "paket.lock";
    private static final String FORWARD_SLASH = "/";
    private static final String PATTERN = "**/*";


    /* --- Members --- */

    private final Logger logger = LoggerFactory.getLogger(PaketDependencyResolver.class);
    private String[] paketIgnoredScopes;

    /* --- Constructor --- */

    public PaketDependencyResolver(String[] paketIgnoredScopes) {
        super();
        this.paketIgnoredScopes = paketIgnoredScopes;
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> paketDependenciesFiles) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        String pathOfPaketLock = topLevelFolder + FORWARD_SLASH + PAKET_LOCK;
        File paketLockFile = new File(pathOfPaketLock);
        if (paketLockFile.exists()) {
            logger.debug("Find paket.lock file: {}", pathOfPaketLock);
            List<String> linesShowInstalledPackages = executeShowInstalledPackages(topLevelFolder);
            if (linesShowInstalledPackages != null && !linesShowInstalledPackages.isEmpty()) {
                Collection<AbstractPaketDependencyCollector> paketDependencyCollectors = new LinkedList<>();
                paketDependencyCollectors.add(new MainGroupPaketDependencyCollector(linesShowInstalledPackages, this.paketIgnoredScopes));
                paketDependencyCollectors.add(new BuildGroupPaketDependencyCollector(linesShowInstalledPackages, this.paketIgnoredScopes));
                paketDependencyCollectors.add(new TestGroupPaketDependencyCollector(linesShowInstalledPackages, this.paketIgnoredScopes));
                paketDependencyCollectors.forEach(paketDependencyCollector -> {
                            Collection<AgentProjectInfo> prjectInfo = paketDependencyCollector.collectDependencies(topLevelFolder);
                            dependencies.addAll(prjectInfo.iterator().next().getDependencies());
                });
            }
        } else {
            // TODO add preStep
            logger.warn("Could not find paket.lock file in {}. Please execute 'paket install' first.", topLevelFolder);
        }
        List<String> excludes = new LinkedList<>();
        // ignore all the nupkg files in order to not scan them again
        if (!dependencies.isEmpty()) {
            excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, Arrays.asList(PATTERN + NUPKG, PATTERN + DLL, PATTERN + EXE, PATTERN + CS, PATTERN + JS), null));
        }
        return new ResolutionResult(dependencies, excludes, getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(DLL, EXE, NUPKG, CS));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NUGET;
    }

    @Override
    protected String getBomPattern() {
        return "**/*" + PAKET_DEPENDENCIES;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    /* --- Private methods --- */

    private List<String> executeShowInstalledPackages(String topLevelFolder) {
        // TODO CHECK IT!!!
        CommandLineProcess showInstalledPackages = new CommandLineProcess(topLevelFolder, getShowInstalledPackagesArgs(topLevelFolder));
        String commandString = String.join(" ", getShowInstalledPackagesArgs(topLevelFolder));
        List<String> lines;
        try {
            lines = showInstalledPackages.executeProcess();
        } catch (IOException e) {
            logger.warn("Could not run '{}' in folder: {}", commandString, topLevelFolder);
            return null;
        }
        if (showInstalledPackages.isErrorInProcess()) {
            return null;
        }
        return lines;
    }

    private String[] getShowInstalledPackagesArgs(String topLevelFolder) {
        return new String[]{topLevelFolder + FORWARD_SLASH + PAKET_COMMAND, SHOW_INSTALLED_PACKAGES_COMMAND};
    }
}
