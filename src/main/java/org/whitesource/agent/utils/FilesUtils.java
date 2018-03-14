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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public class FilesUtils {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(FilesUtils.class);

    public List<Path> getSubDirectories(String directory) {
        String[] files;
        try {
            File file = new File(directory);
            files = file.list((current, name) -> new File(current, name).isDirectory());
            if (files == null) {
                logger.info("Error getting sub directories from: " + directory);
                files = new String[0];
            }
        } catch (Exception ex) {
            logger.info("Error getting sub directories from: " + directory, ex);
            files = new String[0];
        }
        return Arrays.stream(files).map(subDir -> Paths.get(directory, subDir)).collect(Collectors.toList());
    }

    public Map<File, Collection<String>> fillFilesMap(Collection<String> pathsToScan, String[] includes, String[] excludesExtended,
                                                       boolean followSymlinks, boolean globCaseSensitive) {
        Map<File, Collection<String>> fileMap = new HashMap<>();
        for (String scannerBaseDir : pathsToScan) {
            File file = new File(scannerBaseDir);
            logger.debug("Scanning {}", file.getAbsolutePath());
            if (file.exists()) {
                FilesScanner filesScanner = new FilesScanner();
                if (file.isDirectory()) {
                    File basedir = new File(scannerBaseDir);
                    String[] fileNames = filesScanner.getFileNames(scannerBaseDir, includes, excludesExtended, followSymlinks, globCaseSensitive);
                    // convert array to list (don't use Arrays.asList, might be added to later)
                    List<String> fileNameList = Arrays.stream(fileNames).collect(Collectors.toList());
                    fileMap.put(basedir, fileNameList);
                } else {
                    // handle single file
                    boolean included = filesScanner.isIncluded(file, includes, excludesExtended, followSymlinks, globCaseSensitive);
                    if (included) {
                        Collection<String> files = fileMap.get(file.getParentFile());
                        if (files == null) {
                            files = new ArrayList<>();
                        }
                        files.add(file.getName());
                        fileMap.put(file.getParentFile(), files);
                    }
                }
            } else {
                logger.info(MessageFormat.format("File {0} doesn\'t exist", scannerBaseDir));
            }
        }
        return fileMap;
    }

    /* --- Static methods --- */

    public static void deleteDirectory(File directory) {
        if (directory != null) {
            try {
                FileUtils.forceDelete(directory);
            } catch (IOException e) {
                // do nothing
            }
        }
    }
}
