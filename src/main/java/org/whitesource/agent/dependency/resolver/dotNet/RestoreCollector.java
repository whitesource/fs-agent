package org.whitesource.agent.dependency.resolver.dotNet;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author raz.nitzan
 */
public abstract class RestoreCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String NUPKG = ".nupkg";
    public static final String RESTORE = "restore";
    public static final String BACK_SLASH = isWindows() ? Constants.BACK_SLASH : Constants.FORWARD_SLASH;
    private static String[] includes = {"**/*" + NUPKG};
    private static String[] excludes = {};

    /* --- Members --- */

    private int serialNumber = 0;
    private Map<String, String> tempPathToPackagesFile = new HashMap<>();
    private String tempDirectory;
    private String command;

    /* --- Constructors --- */

    public RestoreCollector(String tempDirectory, String command) {
        this.tempDirectory = tempDirectory;
        this.command = command;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        Map<File, Collection<String>> folderMapToFiles = new FilesUtils().fillFilesMap(this.tempPathToPackagesFile.keySet(), this.includes,
                this.excludes,true, false);
        for (File file : folderMapToFiles.keySet()) {
            for (String shortPath : folderMapToFiles.get(file)) {
                String nugetFilePath = file.getAbsolutePath() + BACK_SLASH + shortPath;
                DependencyInfo dependency = getDependency(nugetFilePath, this.tempPathToPackagesFile.get(file.getPath()));
                dependencies.add(dependency);
            }
        }
        deleteDirectories();
        logger.debug("Finish deleting directories of {} {}", this.command, RESTORE);
        return getSingleProjectList(dependencies);
    }

    public void executeRestore(String folder, Set<String> files) {
        for (String file : files) {
            String pathToDownloadPackages = tempDirectory + BACK_SLASH + getNameOfFolderPackages(file) + this.serialNumber;
            this.serialNumber++;
            String[] command = getInstallParams(pathToDownloadPackages, file);
            String commandString = String.join(Constants.WHITESPACE, command);
            logger.debug("Running command : '{}'", commandString);
            CommandLineProcess restoreCommandLine = new CommandLineProcess(folder, command);
            try {
                restoreCommandLine.executeProcess();
            } catch (IOException e) {
                logger.warn("Could not run '{}' in folder: {}", commandString, folder);
            }
            if (!restoreCommandLine.isErrorInProcess()) {
                logger.debug("Finish to run '{}'", commandString);
                this.tempPathToPackagesFile.put(pathToDownloadPackages, file);
            } else {
                logger.warn("Could not run '{}' in folder: {}", commandString, folder);
            }
        }
    }

    public String getCommand() {
        return this.command;
    }

    /* --- abstract methods --- */

    protected abstract String[] getInstallParams(String pathToDownloadPackages, String csprojFile);

    /* --- Private methods --- */

    private String getNameOfFolderPackages(String filePath) {
        File file = new File(filePath);
        String nameWithExtension = file.getName();
        int indexLastDot = nameWithExtension.indexOf(Constants.DOT);
        if (indexLastDot > -1) {
            return nameWithExtension.substring(0, indexLastDot);
        } else {
            return nameWithExtension;
        }
    }

    private void deleteDirectories() {
        File mainDirectory = new File(this.tempDirectory);
        FilesUtils.deleteDirectory(mainDirectory);
    }

    private String getSha1(String filePath) {
        try {
            return ChecksumUtils.calculateSHA1(new File(filePath));
        } catch (IOException e) {
            logger.info("Failed getting " + filePath + ". File will not be send to WhiteSource server.");
            return Constants.EMPTY_STRING;
        }
    }

    private DependencyInfo getDependency(String nugetFilePath, String systemPath) {
        DependencyInfo dependency = new DependencyInfo();
        // TODO to fix the issue with the dependency type
        // dependency.setDependencyType(DependencyType.NUGET);
        dependency.setArtifactId(nugetFilePath.substring(nugetFilePath.lastIndexOf(BACK_SLASH) + 1));
        if (StringUtils.isNotEmpty(systemPath)) {
            dependency.setSystemPath(systemPath);
        }
        String sha1 = getSha1(nugetFilePath);
        dependency.setSha1(sha1);
        return dependency;
    }
}
