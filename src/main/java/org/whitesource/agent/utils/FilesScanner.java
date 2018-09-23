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
package org.whitesource.agent.utils;

import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.whitesource.agent.SingleFileScanner;
import org.whitesource.agent.dependency.resolver.ResolvedFolder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public class FilesScanner {

    /* --- Static members --- */

    private Logger logger = LoggerFactory.getLogger(FilesScanner.class);

    /* --- Public methods --- */

    public String[] getDirectoryContent(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        return getDirectoryContent(scannerBaseDir, includes, excludes, followSymlinks, globCaseSensitive, false);
    }

    // get the content of directory by includes, excludes, followSymlinks and globCaseSensitive, the scanDirectories property define if the scanner will scan to find directories
    public String[] getDirectoryContent(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive, boolean scanDirectories) {
        File file = new File(scannerBaseDir);
        String[] fileNames;
        if (file.exists() && file.isDirectory()) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(scannerBaseDir);
            scanner.setIncludes(includes);
            scanner.setExcludes(excludes);
            scanner.setFollowSymlinks(followSymlinks);
            scanner.setCaseSensitive(globCaseSensitive);
            scanner.scan();
            if (!scanDirectories) {
                fileNames = scanner.getIncludedFiles();
            } else {
                fileNames = scanner.getIncludedDirectories();
            }
            return fileNames;
        } else {
            logger.debug("{} is not a folder", scannerBaseDir);
            return new String[0];
        }
    }

    public Collection<ResolvedFolder> findTopFolders(Collection<String> pathsToScan, String[] includesPattern, Collection<String> excludes) {
        Collection<ResolvedFolder> resolvedFolders = new ArrayList<>();
        // get folders containing bom files
        Map<String, String[]> pathToBomFilesMap = findAllFiles(pathsToScan, includesPattern, excludes);

        // resolve dependencies
        pathToBomFilesMap.forEach((folder, bomFile) -> {
            // get top folders with boms (the parent of each project)
            Map<String, Set<String>> topFolders = getTopFoldersWithIncludedFiles(folder, bomFile);
            resolvedFolders.add(new ResolvedFolder(folder, topFolders));
        });
        return resolvedFolders;
    }

    /* --- Private methods --- */

    private Map<String, String[]> findAllFiles(Collection<String> pathsToScan, String[] includesPattern, Collection<String> excludes) {
        Map<String, String[]> pathToIncludedFilesMap = new HashMap<>();
        pathsToScan.stream().forEach(scanFolder -> {
            String[] includedFiles = getDirectoryContent(new File(scanFolder).getPath(), includesPattern,
                    excludes.toArray(new String[excludes.size()]), false, false);
            pathToIncludedFilesMap.put(new File(scanFolder).getAbsolutePath(), includedFiles);
        });
        return pathToIncludedFilesMap;
    }

    private Map<String, Set<String>> getTopFoldersWithIncludedFiles(String rootFolder, String[] includedFiles) {
        // collect all full paths
        List<String> fullPaths = Arrays.stream(includedFiles)
                .map(file -> Paths.get(new File(rootFolder).getAbsolutePath(), file).toString())
                .collect(Collectors.toList());

        // get top folders
        Map<String, List<String>> foldersGroupedByLengthMap = fullPaths.stream()
                .collect(Collectors.groupingBy(filename -> new File(filename).getParentFile().getParent()));

        // create result map with only the top folder and the corresponding bom files
        Map<String, Set<String>> resultMap = new HashMap<>();

        logger.debug("found folders:" + System.lineSeparator());
        foldersGroupedByLengthMap.keySet().forEach(folder -> logger.debug(folder));
        logger.debug(System.lineSeparator());

        while (foldersGroupedByLengthMap.entrySet().size() > 0) {

            String shortestFolder = foldersGroupedByLengthMap.keySet().stream().min(Comparator.comparingInt(String::length)).get();
            List<String> foundShortestFolder = foldersGroupedByLengthMap.get(shortestFolder);
            foldersGroupedByLengthMap.remove(shortestFolder);

            List<String> topFolders = foundShortestFolder.stream()
                    .map(file -> new File(file).getParent()).collect(Collectors.toList());

            topFolders.forEach(folder -> {
                resultMap.put(folder, fullPaths.stream().filter(fileName -> fileName.contains(folder)).collect(Collectors.toSet()));

                // remove from list folders that are children of the one found so they will not be calculated twice
                foldersGroupedByLengthMap.entrySet().removeIf(otherFolder -> {
                    Path otherFolderPath = Paths.get(otherFolder.getKey());
                    Path folderPath = Paths.get(folder);
                    boolean shouldRemove = false;
                    try {
                        shouldRemove = otherFolderPath.toFile().getCanonicalPath().startsWith(folderPath.toFile().getCanonicalPath());
                    } catch (Exception e) {
                        logger.debug("could not get file path " + otherFolderPath + folderPath, e.getStackTrace());
                        logger.warn("could not get file path " + otherFolderPath + folderPath, e.getMessage());
                    }
                    logger.debug(String.join(";", otherFolder.getKey(), folder, Boolean.toString(shouldRemove)));
                    if (shouldRemove) {
                        logger.debug("---> removed: " + otherFolder.getKey());
                        return true;
                    }
                    return false;
                });
            });
        }
        logger.debug(System.lineSeparator());
        return resultMap;
    }

    public boolean isIncluded(File file, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        SingleFileScanner scanner = new SingleFileScanner();
        scanner.setIncludes(includes);
        scanner.setExcludes(excludes);
        scanner.setFollowSymlinks(followSymlinks);
        scanner.setCaseSensitive(globCaseSensitive);
        return scanner.isIncluded(file);
    }
}