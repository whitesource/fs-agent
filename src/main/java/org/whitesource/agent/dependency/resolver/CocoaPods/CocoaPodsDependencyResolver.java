package org.whitesource.agent.dependency.resolver.CocoaPods;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author raz.nitzan
 */
public class CocoaPodsDependencyResolver extends AbstractDependencyResolver {

    /* --- Static Members --- */

    private static final String PODFILE = "Podfile";
    private static final String PODFILE_LOCK = "Podfile.lock";
    private static final String SWIFT_EXT = ".swift";
    private static final String H_EXT = ".h";
    private static final String M_EXT = ".m";
    private static final String POD = "pod";
    private static final String HPP_EXT = ".hpp";
    private static final String CPP_EXT = ".cpp";
    private static final String CC_EXT = ".cc";
    private static final String C_EXT = ".c";

    /* --- Members --- */

    private boolean runPreStep;
    private boolean ignoreSourceFiles;
    private Collection<String> excludes = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(CocoaPodsDependencyResolver.class);

    /* --- Constructor --- */

    public CocoaPodsDependencyResolver(boolean runPreStep, boolean ignoreSourceFiles) {
        this.runPreStep = runPreStep;
        this.ignoreSourceFiles = ignoreSourceFiles;
    }

    @Override
    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> podFiles) {
        if (ignoreSourceFiles) {
            addExcludesSourcefilesExtenstions();
        }
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        // go over all Pod files, generate Podfile.lock if doesn't exist(if runPreStep=true) and collect dependencies
        for (String podFile : podFiles) {
            logger.debug("Found Podfile: {}", podFile);
            String parentFileOfPodFile = new File(podFile).getParent();
            String podFileLockString = new File(podFile).getParent() + File.separator + PODFILE_LOCK;
            File podFileLock = new File(podFileLockString);
            CocoaPodsDependencyCollector cocoaPodsDependencyCollector = new CocoaPodsDependencyCollector();
            if (podFileLock.exists()) {
                Collection<AgentProjectInfo> projects = cocoaPodsDependencyCollector.collectDependencies(podFileLockString);
                dependencyInfos.addAll(projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()));
            } else if (this.runPreStep) {
                boolean processFailed = executePodInstall(parentFileOfPodFile);
                if (processFailed) {
                    logger.warn("Failed to run 'pod install' in folder: {}", parentFileOfPodFile);
                } else {
                    Collection<AgentProjectInfo> projects = cocoaPodsDependencyCollector.collectDependencies(podFileLockString);
                    dependencyInfos.addAll(projects.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()));
                }
            } else {
                logger.info("Found Podfile, Podfile.lock doesn't exist. Please run 'pod install' or set 'CocoaPods.runPreStep=true'.");
            }
        }
        return new ResolutionResult(dependencyInfos, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private void addExcludesSourcefilesExtenstions() {
        for (String extension : getSourceFileExtensions()) {
            excludes.add(Constants.PATTERN + extension);
        }
    }

    private boolean executePodInstall(String folderToInstall) {
        boolean processFailed = false;
        CommandLineProcess commandLineProcess = new CommandLineProcess(folderToInstall, new String[]{POD, Constants.INSTALL});
        
        try {
            commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                processFailed = true;
            }
        } catch (IOException e) {
            processFailed = true;
        }
        return processFailed;
    }

    @Override
    protected Collection<String> getExcludes() {
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(SWIFT_EXT, H_EXT, M_EXT, HPP_EXT, CPP_EXT, CC_EXT, C_EXT));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.COCOAPODS;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.COCOAPODS.name();
    }

    @Override
    public String[] getBomPattern() {
        return new String[]{Constants.GLOB_PATTERN_PREFIX + PODFILE};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(PODFILE);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getRelevantScannedFolders(Collection<String> scannedFolders) {
        // CocoaPods resolver should scan all folders and should not remove any folder
        return scannedFolders == null ? Collections.emptyList() : scannedFolders;
    }
}
