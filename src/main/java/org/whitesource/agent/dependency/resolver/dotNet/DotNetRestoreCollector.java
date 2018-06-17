package org.whitesource.agent.dependency.resolver.dotNet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author raz.nitzan
 */
public class DotNetRestoreCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String DOTNET_RESTORE_TMP_DIRECTORY = Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-DotnetRestore").toString();
    public static final String DOTNET_COMMAND = "dotnet";
    public static final String RESTORE = "restore";
    public static final String PACKAGES = "--packages";
    public static final String DOT_NET_RESTORE_WS = "DotNetRestoreWS";
    public static final String NUPKG = ".nupkg";
    public static final String BACK_SLASH = isWindows() ? Constants.BACK_SLASH : Constants.FORWARD_SLASH;
    private static String[] includes = {"**/*" + NUPKG};
    private static String[] excludes = {};

    /* --- Members --- */

    private int serialNumber = 0;
    private Collection<String> pathsToScan = new LinkedList<>();

    /* --- Constructors --- */

    public DotNetRestoreCollector() {
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        Map<File, Collection<String>> folderMapToFiles = new FilesUtils().fillFilesMap(this.pathsToScan, this.includes,
                this.excludes,true, false);
        for (File file : folderMapToFiles.keySet()) {
            for (String shortPath : folderMapToFiles.get(file)) {
                String nugetFilePath = file.getAbsolutePath() + BACK_SLASH + shortPath;
                DependencyInfo dependency = getDependency(nugetFilePath, shortPath);
                dependencies.add(dependency);
            }
        }
        deleteDirectories();
        logger.debug("Finish deleting directories of {} {}", DOTNET_COMMAND, RESTORE);
        return getSingleProjectList(dependencies);
    }

    /**
     * @param folder - top folder
     * @param csprojFiles - paths to csproj files
     * Get all paths to collect dependencies after executing 'dotnet restore'
     */

    public void executeDotNetRestore(String folder, Set<String> csprojFiles) {
        for (String csprojFile : csprojFiles) {
            String pathToDownloadPackages = DOTNET_RESTORE_TMP_DIRECTORY + BACK_SLASH + getNameOfFolderPackages(csprojFile) + this.serialNumber;
            this.serialNumber++;
            String[] command = getInstallParams(pathToDownloadPackages, csprojFile);
            String commandString = String.join(Constants.WHITESPACE, command);
            logger.debug("Running command : '{}'", commandString);
            CommandLineProcess dotNetRestore = new CommandLineProcess(folder, command);
            try {
                dotNetRestore.executeProcess();
            } catch (IOException e) {
                logger.warn("Could not run '{}' in folder: {}", commandString, folder);
            }
            if (!dotNetRestore.isErrorInProcess()) {
                logger.debug("Finish to run '{}'", commandString);
                this.pathsToScan.add(pathToDownloadPackages);
            } else {
                logger.warn("Could not run '{}' in folder: {}", commandString, folder);
            }
        }
    }

    /* --- Protected methods --- */

    protected String[] getInstallParams(String pathToDownloadPackages, String csprojFile) {
        return new String[]{DOTNET_COMMAND, RESTORE, csprojFile, PACKAGES, pathToDownloadPackages};
    }

    /* --- Private methods --- */

    private String getNameOfFolderPackages(String csprojFile) {
        Pattern pattern = Pattern.compile(".*[/\\\\](.*)\\.csproj");
        Matcher matcher = pattern.matcher(csprojFile);
        matcher.find();
        return matcher.group(1) + DOT_NET_RESTORE_WS;
    }

    private void deleteDirectories() {
        File mainDirectory = new File(DOTNET_RESTORE_TMP_DIRECTORY);
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

    private DependencyInfo getDependency(String nugetFilePath, String shortPath) {
        DependencyInfo dependency = new DependencyInfo();
        // TODO to fix the issue with the dependency type
        String name = getPackageName(shortPath);
//        dependency.setGroupId(name);
//        dependency.setArtifactId(name);
        dependency.setArtifactId(name + Constants.DOT + getVersion(shortPath) + NUPKG);
//        dependency.setVersion(getVersion(shortPath));
//        dependency.setDependencyType(DependencyType.NUGET);
        String sha1 = getSha1(nugetFilePath);
        dependency.setSha1(sha1);
        return dependency;
    }

    private String getPackageName(String path) {
        int indexBackslash = path.indexOf(BACK_SLASH);
        return path.substring(0, indexBackslash);
    }

    private String getVersion(String path) {
        int indexBackslash = path.indexOf(BACK_SLASH);
        int indexSecondBackslash = path.indexOf(BACK_SLASH, indexBackslash + 1);
        return path.substring(indexBackslash + 1, indexSecondBackslash);
    }
}
