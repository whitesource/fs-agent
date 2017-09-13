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
import org.slf4j.LoggerFactory;
import org.whitesource.agent.SingleFileScanner;
import org.whitesource.agent.dependency.resolver.ResolvedFolder;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public class FilesScanner {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(FilesScanner.class);

    /* --- Public methods --- */

    public String[] getFileNames(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        File file = new File(scannerBaseDir);
        if (file.exists() && file.isDirectory()) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(scannerBaseDir);
            scanner.setIncludes(includes);
            scanner.setExcludes(excludes);
            scanner.setFollowSymlinks(followSymlinks);
            scanner.setCaseSensitive(globCaseSensitive);
            scanner.scan();
            String[] fileNames = scanner.getIncludedFiles();
            return fileNames;
        } else {
            logger.debug("{} is not a folder", scannerBaseDir);
            return new String[0];
        }
    }

    public Collection<ResolvedFolder> findTopFolders(Collection<String> pathsToScan, String includesPattern, Collection<String> excludes) {
        Collection<ResolvedFolder> resolvedFolders = new ArrayList<>();
        // get folders containing bom files
        Map<String, String[]> pathToBomFilesMap = findAllFiles(pathsToScan, includesPattern, excludes);

        // resolve dependencies
        pathToBomFilesMap.forEach((folder, bomFile) -> {
            // get top folders with boms (the parent of each project)
            Map<String, List<String>> topFolders = getTopFoldersWithIncludedFiles(folder, bomFile);
            resolvedFolders.add(new ResolvedFolder(folder, topFolders));
        });
        return resolvedFolders;
    }

    private Map<String, String[]> findAllFiles(Collection<String> pathsToScan, String includesPattern, Collection<String> excludes) {
        Map pathToIncludedFilesMap = new HashMap();
        pathsToScan.stream().forEach(scanFolder -> {
            String[] includedFiles = getFileNames(new File(scanFolder).getPath(), new String[]{ includesPattern },
                    excludes.toArray(new String[excludes.size()]), false, false);
            pathToIncludedFilesMap.put(new File(scanFolder).getAbsolutePath(), includedFiles);
        });
        return pathToIncludedFilesMap;
    }

    private Map<String, List<String>> getTopFoldersWithIncludedFiles(String rootFolder, String[] includedFiles) {
        // collect all full paths
        List<String> fullPaths = Arrays.stream(includedFiles)
                .map(file -> Paths.get(new File(rootFolder).getAbsolutePath(), file).toString())
                .collect(Collectors.toList());

        // get top folders
        Map<Integer, List<String>> foldersGroupedByLengthMap = fullPaths.stream()
                .collect(Collectors.groupingBy(filename -> new File(filename).getParentFile().getParent().length()));

        // create result map with only the top folder and the corresponding bom files
        Map<String, List<String>> resultMap = new HashMap<>();
        while (foldersGroupedByLengthMap.entrySet().size() > 0) {
            Optional<Integer> shortestPathLength = foldersGroupedByLengthMap.keySet().stream().min(Integer::compareTo);
            if (shortestPathLength.isPresent()) {
                Integer length = shortestPathLength.get();

                List<String> foundShortestFolder = foldersGroupedByLengthMap.get(length);
                List<String> topFolders = foundShortestFolder.stream()
                        .map(file -> new File(file).getParent()).collect(Collectors.toList());

                topFolders.forEach(folder -> {
                    resultMap.put(folder, fullPaths.stream().filter(fileName -> fileName.contains(folder)).collect(Collectors.toList()));

                    // remove from list folders that are children of the one found so they will not be calculated twice
                    foldersGroupedByLengthMap.entrySet().removeIf(otherFolder -> {
                        if (otherFolder.getValue().get(0).contains(folder)) {
                            return true;
                        }
                        return false;
                    });
                });
            }
        }
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