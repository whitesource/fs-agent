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

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class FilesScanner {

    /* --- Public methods --- */

    public String[] getFileNames(String scannerBaseDir, String[] includes, String[] excludes, boolean followSymlinks, boolean globCaseSensitive) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(scannerBaseDir);
        scanner.setIncludes(includes);
        scanner.setExcludes(excludes);
        scanner.setFollowSymlinks(followSymlinks);
        scanner.setCaseSensitive(globCaseSensitive);
        scanner.scan();
        String[] fileNames = scanner.getIncludedFiles();
        return fileNames;
    }

    public Map<String, String[]> findAllFiles(Collection<String> pathsToScan, String includesPattern, Collection<String> excludes) {
        Map pathToIncludedFilesMap = new HashMap();
        pathsToScan.stream().forEach(scanFolder -> {
            String[] includedFiles = getFileNames(new File(scanFolder).getPath(), new String[]{includesPattern},
                    excludes.toArray(new String[excludes.size()]), false, false);
            pathToIncludedFilesMap.put(new File(scanFolder).getAbsolutePath(), includedFiles);
        });
        return pathToIncludedFilesMap;
    }

    public Map<String, List<String>> getTopFoldersWithIncludedFiles(String rootFolder, String[] includedFiles) {
        // collect all full paths
        List<String> fullPaths = Arrays.stream(includedFiles)
                .map(file -> Paths.get(new File(rootFolder).getAbsolutePath(), file).toString())
                .collect(Collectors.toList());

        // get top folders
        Map<Integer, List<String>> foldersGroupedByLengthMap = fullPaths.stream()
                .collect(Collectors.groupingBy(filename -> new File(filename).getParentFile().getParent().length()));
        Optional<Integer> shortestPathLength = foldersGroupedByLengthMap.keySet().stream().min(Integer::compareTo);

        // create result map
        Map<String, List<String>> resultMap = new HashMap<>();
        if (shortestPathLength.isPresent()) {
            Integer length = shortestPathLength.get();
            List<String> topFolders = foldersGroupedByLengthMap.get(length).stream()
                    .map(file -> new File(file).getParent()).collect(Collectors.toList());

            topFolders.forEach(folder -> resultMap.put(folder, fullPaths.stream().filter(fileName -> fileName.contains(folder)).collect(Collectors.toList())));
        }
        return resultMap;
    }
}