package org.whitesource.agent.dependency.resolver;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * @author raz.nitzan
 */
public class ViaMultiModuleAnalyzer {

    /* --- Static Members --- */

    private final Collection<String> buildExtensions = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(ViaMultiModuleAnalyzer.class);

    /* --- Members --- */

    private AbstractDependencyResolver dependencyResolver;
    private String suffixOfBuild;
    private String scanDirectory;
    private String contentFileAppPaths;

    /* --- Constructor --- */

    public ViaMultiModuleAnalyzer(String scanDirectory, AbstractDependencyResolver dependencyResolver, String suffixOfBuild, String contentFileAppPaths) {
        this.buildExtensions.add(".jar");
        this.buildExtensions.add(".war");
        this.buildExtensions.add(".zip");
        this.dependencyResolver = dependencyResolver;
        this.suffixOfBuild = suffixOfBuild;
        this.scanDirectory = scanDirectory;
        this.contentFileAppPaths = contentFileAppPaths;
    }

    private Collection<String> findBomFiles() {
        Collection<String> bomFiles = new HashSet<>();
        Collection<String> scanDirectoryCollection = new LinkedList<>();
        scanDirectoryCollection.add(scanDirectory);
        Collection<ResolvedFolder> topFolders = new FilesScanner().findTopFolders(scanDirectoryCollection, dependencyResolver.getBomPattern(), dependencyResolver.getExcludes());
        topFolders.forEach(topFolder -> topFolder.getTopFoldersFound().forEach((folder, bomFilesFound) -> bomFiles.addAll(bomFilesFound)));
        return bomFiles;
    }

    public void writeFile() {
        try {
            File outputFile = new File(this.contentFileAppPaths);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
            bufferedWriter.write(this.scanDirectory);
            bufferedWriter.write(System.lineSeparator());
            // TODO CHANGE THE NAME OF THE PROJECT
            bufferedWriter.write(this.scanDirectory);
            bufferedWriter.write(System.lineSeparator());
            Collection<String> bomFiles = findBomFiles();
            bomFiles.forEach(bomFile -> {
                File parentFileOfBom = new File(bomFile).getParentFile();
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
                    if (!filesWithBuildExtensions.isEmpty()) {
                        File smallestFile = findSmallestFile(filesWithBuildExtensions);
                        try {
                            bufferedWriter.write(parentFileOfBom.getName() + Constants.COMMA + smallestFile.getAbsolutePath());
                            bufferedWriter.write(System.lineSeparator());
                        } catch (IOException e) {
                            logger.warn("Failed to write to file: {}", this.contentFileAppPaths);
                        }
                    }
                }});
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (IOException e) {
            logger.warn("Failed to write to file: {}", this.contentFileAppPaths);
        }
    }

    private File findSmallestFile(Collection<File> files) {
        File smallestFile = null;
        long smallestSize = Long.MAX_VALUE;
        for (File file : files) {
            if (file.length() < smallestSize) {
                smallestSize = file.length();
                smallestFile = file;
            }
        }
        return smallestFile;
    }
}
