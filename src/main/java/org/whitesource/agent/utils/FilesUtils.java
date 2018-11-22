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
import org.whitesource.agent.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author eugen.horovitz
 */
public class FilesUtils {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(FilesUtils.class);
    private final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");


    public String createTmpFolder(boolean addCharToEndOfUrl, String nameOfFolder) {
        String result = getTempDirPackages(addCharToEndOfUrl, nameOfFolder);
        try {
            FileUtils.forceMkdir(new File(result));
        } catch (IOException e) {
            logger.warn("Failed to create temp folder : " + e.getMessage());
            result = null;
        }
        return result;
    }

    private String getTempDirPackages(boolean addCharToEndOfUrl, String nameOfFolder) {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String tempFolder = JAVA_TEMP_DIR.endsWith(File.separator) ? JAVA_TEMP_DIR + nameOfFolder + File.separator + creationDate :
                JAVA_TEMP_DIR + File.separator + nameOfFolder + File.separator + creationDate;
        if (addCharToEndOfUrl) {
            tempFolder = tempFolder + "1";
        }

        return tempFolder;
    }

    public List<Path> getSubDirectories(String directory, String[] includes, String[] excludesExtended, boolean followSymlinks, boolean globCaseSensitive) {
        String[] files;
        FilesScanner filesScanner = new FilesScanner();
        try {
            files = filesScanner.getDirectoryContent(directory, includes, excludesExtended, followSymlinks, globCaseSensitive,true);
        } catch (Exception ex) {
            logger.info("Error getting sub directories from: " + directory, ex.getMessage());
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
                    String[] fileNames = filesScanner.getDirectoryContent(scannerBaseDir, includes, excludesExtended, followSymlinks, globCaseSensitive);
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

    public static String getFileExtension(String fileName) {
        if(fileName == null) fileName = Constants.EMPTY_STRING;
        String extension = Constants.EMPTY_STRING;
        int i = fileName.lastIndexOf(Constants.DOT);
        if (i > 0 && i < fileName.length()-2) {
            extension = fileName.substring(i+1);
        }
        return extension;
    }

    public static String removeTempFiles(String rootDirectory, long creationTime) {
        String errors = "";
        FileTime fileCreationTime = FileTime.fromMillis(creationTime);
        File directory = new File(rootDirectory);
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                try {
                    BasicFileAttributes fileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    if (fileAttributes.creationTime().compareTo(fileCreationTime) > 0){
                        FileUtils.forceDelete(file);
                    } else if (file.isDirectory()) {
                        errors = errors.concat(removeTempFiles(file.getPath(), creationTime));
                    }
                } catch (IOException e) {
                    errors = errors.concat("can't remove " + file.getPath() + ": " + e.getMessage() + '\n');
                }
            }
        }
        return errors;
    }
}