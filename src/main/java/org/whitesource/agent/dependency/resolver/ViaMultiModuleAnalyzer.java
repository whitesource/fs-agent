package org.whitesource.agent.dependency.resolver;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author raz.nitzan
 */
public class ViaMultiModuleAnalyzer {

    /* --- Static Members --- */

    private static final String APP_PATH = "AppPath";
    private static final String DEPENDENCY_MANAGER_PATH = "DependencyManagerFilePath";
    private static final String PROJECT_FOLDER_PATH = "ProjectFolderPath";
    private static final String DEFAULT_NAME = "defaultName";
    private static final String ALT_NAME = "altName";

    /* --- Members --- */

    private final Logger logger = LoggerFactory.getLogger(ViaMultiModuleAnalyzer.class);
    private final Collection<String> buildExtensions = new HashSet<>(Arrays.asList(".jar", ".war", ".zip"));
    private AbstractDependencyResolver dependencyResolver;
    private String suffixOfBuild;
    private String scanDirectory;
    private String contentFileAppPaths;
    private Collection<String> bomFiles = new HashSet<>();

    /* --- Constructor --- */

    public ViaMultiModuleAnalyzer(String scanDirectory, AbstractDependencyResolver dependencyResolver, String suffixOfBuild, String contentFileAppPaths) {
        this.dependencyResolver = dependencyResolver;
        this.suffixOfBuild = suffixOfBuild;
        this.scanDirectory = scanDirectory;
        this.contentFileAppPaths = contentFileAppPaths;
        findBomFiles();
    }

    private void findBomFiles() {
        Collection<String> scanDirectoryCollection = new LinkedList<>();
        scanDirectoryCollection.add(scanDirectory);
        Collection<ResolvedFolder> topFolders = new FilesScanner().findTopFolders(scanDirectoryCollection, dependencyResolver.getBomPattern(), dependencyResolver.getExcludes());
        topFolders.forEach(topFolder -> topFolder.getTopFoldersFound().forEach((folder, bomFilesFound) -> this.bomFiles.addAll(bomFilesFound)));
    }

    public void writeFile() {
        try {
            File outputFile = new File(this.contentFileAppPaths);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
            bufferedWriter.write(replaceAllSlashes(DEPENDENCY_MANAGER_PATH + Constants.EQUALS + this.scanDirectory));
            bufferedWriter.write(System.lineSeparator());
            int counter = 1;
            boolean printMessageAppPath = true;
            HashMap<String, Integer> folderNameCounter = new HashMap<>();
            for (String bomFile : bomFiles) {
                File parentFileOfBom = new File(bomFile).getParentFile();
                String parentFileName = parentFileOfBom.getName();
                File buildFolder = new File(parentFileOfBom.getPath() + File.separator + this.suffixOfBuild);
                if (buildFolder.exists() && buildFolder.isDirectory() && buildFolder.listFiles() != null) {
                    Collection<File> filesWithBuildExtensions = Arrays.stream(buildFolder.listFiles()).filter(file -> {
                        for (String extension : buildExtensions) {
                            if (file.getName().endsWith(extension)) {
                                return true;
                            }
                        }
                        return false;
                    }).collect(Collectors.toList());
                    try {
                        if (filesWithBuildExtensions.size() >= 1) {
                            bufferedWriter.write(replaceAllSlashes(PROJECT_FOLDER_PATH + counter + Constants.EQUALS + parentFileOfBom.getAbsolutePath()));
                            bufferedWriter.write(System.lineSeparator());
                            String appPathProperty = APP_PATH + counter + Constants.EQUALS;
                            if (filesWithBuildExtensions.size() == 1) {
                                File appPath = filesWithBuildExtensions.stream().findFirst().get();
                                appPathProperty += appPath.getAbsolutePath();
                            } else if (printMessageAppPath) {
                                logger.warn("Analysis found multiple candidates for one or more appPath settings that are listed in the multi-module analysis setup file. Please review the setup file and set the appropriate appPath parameters.");
                                printMessageAppPath = false;
                            }
                            bufferedWriter.write(replaceAllSlashes(appPathProperty));
                            bufferedWriter.write(System.lineSeparator());
                            bufferedWriter.write(replaceAllSlashes(DEFAULT_NAME + counter + Constants.EQUALS + parentFileName));
                            bufferedWriter.write(System.lineSeparator());
                            if (folderNameCounter.get(parentFileName) == null){
                                folderNameCounter.put(parentFileName,0);
                                bufferedWriter.write(replaceAllSlashes(ALT_NAME + counter + Constants.EQUALS + parentFileName));
                            } else {
                                int i = folderNameCounter.get(parentFileName) + 1;
                                folderNameCounter.put(parentFileName,i);
                                bufferedWriter.write(replaceAllSlashes(ALT_NAME + counter + Constants.EQUALS + parentFileName + Constants.UNDERSCORE + i));
                            }
                            bufferedWriter.write(System.lineSeparator());
                            counter++;
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to write to file: {}", this.contentFileAppPaths);
                    }
                }
            }
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (IOException e) {
            logger.warn("Failed to write to file: {}", this.contentFileAppPaths);
        }
    }

    public Collection<String> getBomFiles() {
        return this.bomFiles;
    }

    private String replaceAllSlashes(String line) {
        return line.replaceAll("\\\\", "/");
    }
}
