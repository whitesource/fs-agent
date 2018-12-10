/**
 * Copyright (C) 2014 WhiteSource Ltd.
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
package org.whitesource.agent.archive;

import com.github.junrar.testutil.ExtractArchive;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.archiver.tar.TarBZip2UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.archiver.xz.XZUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.redline_rpm.ReadableChannelWrapper;
import org.redline_rpm.Util;
import org.redline_rpm.header.AbstractHeader;
import org.redline_rpm.header.Format;
import org.redline_rpm.header.Header;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.Pair;
import org.whitesource.agent.TempFolders;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author anna.rozin
 */
public class ArchiveExtractor {
    public static final String LAYER_TAR = "**/*layer.tar";

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ArchiveExtractor.class);
    public static final int LONG_BOUND = 100000;
    public static final String DEPTH = "_depth_";
    public static final String DEPTH_REGEX = DEPTH + "[0-9]";
    public static final String GLOB_PREFIX = "glob:";
    public static final String NULL_HEADER = "mainheader is null";

    private final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");


    public static final List<String> ZIP_EXTENSIONS = Arrays.asList("jar", "war", "aar", "ear", "egg", "zip", "whl", "sca", "sda", "nupkg");
    public static final List<String> GEM_EXTENSIONS = Collections.singletonList("gem");
    public static final List<String> TAR_EXTENSIONS = Arrays.asList("tar.gz", "tar", "tgz", "tar.bz2", "tar.xz", "xz");
    public static final List<String> RPM_EXTENSIONS = Collections.singletonList("rpm");
    public static final List<String> RAR_EXTENSIONS = Collections.singletonList("rar");

    public static final String ZIP_EXTENSION_PATTERN;
    public static final String GEM_EXTENSION_PATTERN;
    public static final String TAR_EXTENSION_PATTERN;
    public static final String RPM_EXTENSION_PATTERN;
    public static final String RAR_EXTENSION_PATTERN;
    public static final String RUBY_DATA_FILE = "data.tar.gz";
    public static final String TAR_SUFFIX = ".tar";
    public static final String GZ_SUFFIX = ".gz";
    public static final String BZ_SUFFIX = ".bz2";
    public static final String XZ_SUFFIX = ".xz";
    public static final String LZMA = "lzma";
    public static final String CPIO = ".cpio";
    public static final String TGZ_SUFFIX = ".tgz";

    public static final String TAR_GZ_SUFFIX = TAR_SUFFIX + GZ_SUFFIX;
    public static final String TAR_BZ2_SUFFIX = TAR_SUFFIX + BZ_SUFFIX;

    public static final String UN_ARCHIVER_LOGGER = "unArchiverLogger";
    public static final String GLOB_PATTERN_PREFIX = Constants.PATTERN + Constants.DOT;
    public static final String PATTERN_PREFIX = ".*\\.";
    public static final String XZ_UN_ARCHIVER_FILE_NAME = "compressedFile.tar";

    static {
        ZIP_EXTENSION_PATTERN = initializePattern(ZIP_EXTENSIONS);
        GEM_EXTENSION_PATTERN = initializePattern(GEM_EXTENSIONS);
        TAR_EXTENSION_PATTERN = initializePattern(TAR_EXTENSIONS);
        RPM_EXTENSION_PATTERN = initializePattern(RPM_EXTENSIONS);
        RAR_EXTENSION_PATTERN = initializePattern(RAR_EXTENSIONS);
    }

    private static String initializePattern(List<String> archiveExtensions) {
        StringBuilder sb = new StringBuilder();
        for (String archiveExtension : archiveExtensions) {
            sb.append(PATTERN_PREFIX);
            sb.append(archiveExtension);
            sb.append(Constants.PIPE);
        }
        return sb.toString().substring(0, sb.toString().lastIndexOf(Constants.PIPE));
    }

    /* --- Private members --- */

    private final String[] archiveIncludesPattern;
    private final String[] archiveExcludesPattern;
    private final String[] filesExcludes;
    private String randomString;
    private String tempFolderNoDepth;
    private boolean fastUnpack = false;

    /* --- Constructors --- */

    public ArchiveExtractor(String[] archiveIncludes, String[] archiveExcludes, String[] filesExcludes, boolean fastUnpack) {
        this(archiveIncludes, archiveExcludes, filesExcludes);
        this.fastUnpack = fastUnpack;
    }

    public ArchiveExtractor(String[] archiveIncludes, String[] archiveExcludes, String[] filesExcludes) {
        if (archiveIncludes.length > 0 && StringUtils.isNotBlank(archiveIncludes[0])) {
            this.archiveIncludesPattern = archiveIncludes;
        } else {
            // create ARCHIVE_EXTENSIONS only if archiveIncludes is empty
            this.archiveIncludesPattern = createArchivesArray();
        }
        this.archiveExcludesPattern = archiveExcludes;
        this.filesExcludes = filesExcludes;
    }

    private String getTempFolder(String scannerBaseDir) {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String tempFolder = JAVA_TEMP_DIR.endsWith(File.separator) ? JAVA_TEMP_DIR + TempFolders.UNIQUE_WHITESOURCE_ARCHIVE_EXTRACTOR_TEMP_FOLDER + File.separator + creationDate :
                JAVA_TEMP_DIR + File.separator + TempFolders.UNIQUE_WHITESOURCE_ARCHIVE_EXTRACTOR_TEMP_FOLDER + File.separator + creationDate;
        String destDirectory = tempFolder + Constants.UNDERSCORE + this.randomString;
        int separatorIndex = scannerBaseDir.lastIndexOf(File.separator);

        if (separatorIndex != -1) {
            destDirectory = destDirectory + scannerBaseDir.substring(separatorIndex, scannerBaseDir.length());
            try {
                // this solves the tilda issue in filepath in windows (mangled Windows filenames)
                destDirectory = new File(destDirectory).getCanonicalPath().toString();
            } catch (IOException e) {
                logger.warn("Error getting the absolute file name ", e);
            }
        }
        return destDirectory;
    }

    /* --- Public methods --- */

    /**
     * The Method extracts all the Archive files according to the archiveExtractionDepth.
     * archiveExtractionDepth defined by the user in the configuration file.
     * <p>
     * The archiveExtractionDepth default value is 0 - no archive scanning, the max value is 3.
     * By default the method scans jar/war/ear.
     * If archiveIncludes/archiveExcludes params are defined the method will act accordingly.
     *
     * @param scannerBaseDir         - directory for scanning.
     * @param archiveExtractionDepth - drill down hierarchy level in archive files
     * @param archiveDirectories     list of directories
     * @return the temp directory for the extracted files.
     */
    public String extractArchives(String scannerBaseDir, int archiveExtractionDepth, List<String> archiveDirectories) {
        this.randomString = String.valueOf(ThreadLocalRandom.current().nextLong(0, LONG_BOUND));
        this.tempFolderNoDepth = getTempFolder(scannerBaseDir);
        logger.debug("Base directory is {}, extraction depth is set to {}", scannerBaseDir, archiveExtractionDepth);
        Map<String, Map<String, String>> allFiles = new HashMap<>();
        // Extract again if needed according archiveExtractionDepth parameter
        for (int curLevel = 0; curLevel < archiveExtractionDepth; curLevel++) {
            String folderToScan;
            String folderToExtract;
            if (curLevel == 0) {
                folderToScan = scannerBaseDir;
            } else {
                folderToScan = getDepthFolder(curLevel - 1);
            }
            folderToExtract = getDepthFolder(curLevel);
            Pair<String[], String> retrieveFilesWithFolder = getSearchedFileNames(folderToScan);
            if (retrieveFilesWithFolder == null || retrieveFilesWithFolder.getKey().length <= 0) {
                break;
            } else {
                String[] fileNames = retrieveFilesWithFolder.getKey();
                folderToScan = retrieveFilesWithFolder.getValue();

                Pair<String, Collection<String>> filesFound = new Pair<>(folderToScan, Arrays.stream(fileNames).collect(Collectors.toList()));
                Map<String, String> foundFiles;
                if (fastUnpack) {
                    foundFiles = handleArchiveFilesFast(folderToExtract, filesFound);
                } else {
                    foundFiles = handleArchiveFiles(folderToExtract, filesFound);
                }
                allFiles.put(String.valueOf(curLevel), foundFiles);
            }
        }
        if (!allFiles.isEmpty()) {
            String parentDirectory = new File(this.tempFolderNoDepth).getParent();
            archiveDirectories.add(parentDirectory);
            return parentDirectory;
        } else {
            // if unable to extract, return null
            return null;
        }
    }

    // extract image layers
    public void extractDockerImageLayers(File imageTarFile, File imageExtractionDir, Boolean deleteTarFiles) {
        FilesScanner filesScanner = new FilesScanner();
        boolean success = false;
        // docker layers are saved as TAR file (we save it as TAR)
        if (imageTarFile.getName().endsWith(TAR_SUFFIX)) {
            success = unTar(imageTarFile.getName().toLowerCase(), imageExtractionDir.getAbsolutePath(), imageTarFile.getPath());
            boolean deleted = false;
            if (deleteTarFiles) {
                deleted = imageTarFile.delete();
            }
            if (!deleted) {
                logger.warn("Was not able to delete {} (docker image TAR file)", imageTarFile.getName());
            }
        }
        if (success) {
            String[] fileNames = filesScanner.getDirectoryContent(imageExtractionDir.getAbsolutePath(), new String[]{LAYER_TAR}, new String[]{}, true, false);
            for (String filename : fileNames) {
                File layerToExtract = new File(imageExtractionDir + File.separator + filename);
                extractDockerImageLayers(layerToExtract, layerToExtract.getParentFile(), deleteTarFiles);
            }
        } else {
            logger.warn("Was not able to extract {} (docker image TAR file)", imageTarFile.getName());
        }
    }

    private String getDepthFolder(int depth) {
        return this.tempFolderNoDepth + DEPTH + depth;
    }

    /* --- Private methods --- */

    private String[] createArchivesArray() {
        Collection<String> archiveExtensions = new ArrayList<>();
        archiveExtensions.addAll(ZIP_EXTENSIONS);
        archiveExtensions.addAll(GEM_EXTENSIONS);
        archiveExtensions.addAll(TAR_EXTENSIONS);

        String[] archiveIncludesPattern = new String[archiveExtensions.size()];
        int i = 0;
        for (String extension : archiveExtensions) {
            archiveIncludesPattern[i++] = GLOB_PATTERN_PREFIX + extension;
        }
        return archiveIncludesPattern;
    }

    private Pair<String[], String> getSearchedFileNames(String fileOrFolderToScan) {
        String[] foundFiles = null;
        File file = new File(fileOrFolderToScan);

        String folderToScan;
        if (file.exists()) {
            FilesScanner filesScanner = new FilesScanner();
            if (file.isDirectory()) {
                // scan directory
                foundFiles = filesScanner.getDirectoryContent(fileOrFolderToScan, archiveIncludesPattern, archiveExcludesPattern, false, false);
                folderToScan = fileOrFolderToScan;
                return new Pair<>(foundFiles, folderToScan);
            } else {
                //// handle file passed in -d parameter
                //// check if file matches archive GLOB patterns
                boolean included = filesScanner.isIncluded(file, archiveIncludesPattern, archiveExcludesPattern, false, false);
                if (included) {
                    folderToScan = file.getParent();
                    String relativeFilePath = new File(folderToScan).toURI().relativize(new File(file.getAbsolutePath()).toURI()).getPath();
                    foundFiles = new String[]{relativeFilePath};
                    return new Pair<>(foundFiles, folderToScan);
                }
            }
            filesScanner = null;
        }
        return null;
    }

    private Map<String, String> handleArchiveFiles(String baseFolderToExtract, Pair<String, Collection<String>> fileNames) {
        Map<String, String> founded = new HashMap<>();
        for (String fileName : fileNames.getValue()) {
            String archivePath = Paths.get(fileNames.getKey(), fileName).toString();
            String unpackFolder = Paths.get(baseFolderToExtract, FilenameUtils.removeExtension(fileName)).toString();
            Pair<String, String> dataToUnpack = new Pair<>(archivePath, unpackFolder);
            Pair<String, String> foundArchive = getUnpackedResult(dataToUnpack);
            if (foundArchive != null) {
                founded.put(foundArchive.getKey(), foundArchive.getValue());
            }
        }
        return founded;
    }

    private Map<String, String> handleArchiveFilesFast(String baseFolderToExtract, Pair<String, Collection<String>> fileNames) {
        Collection<Pair> dataToUnpack = fileNames.getValue().stream().map(fileName -> {
            String archivePath = Paths.get(fileNames.getKey(), fileName).toString();
            String unpackFolder = Paths.get(baseFolderToExtract, FilenameUtils.removeExtension(fileName)).toString();
            return new Pair(archivePath, unpackFolder);
        }).collect(Collectors.toList());
        return processCollections(dataToUnpack);
    }

    public Map<String, String> processCollections(Collection<Pair> unitsOfWork) {
        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        List<Future<Pair>> handles = new ArrayList<>();

        List<Callable<Pair>> callableList = new ArrayList<>();
        unitsOfWork.stream().forEach(unitOfWork -> callableList.add(() -> getUnpackedResult(unitOfWork)));

        for (Callable<Pair> callable : callableList) {
            Future<Pair> handle = executorService.submit(callable);
            handles.add(handle);
        }

        Map<String, String> results = new HashMap<>();
        for (Future<Pair> h : handles) {
            try {
                Pair<String, String> dataToUnpack = h.get();
                results.put(dataToUnpack.getKey(), dataToUnpack.getValue());
            } catch (InterruptedException e) {
                logger.warn("Error: {}", e.getMessage());
            } catch (ExecutionException e) {
                logger.warn("Error: {}", e.getMessage());
            }
        }

        executorService.shutdownNow();
        return results;
    }

    private Pair<String, String> getUnpackedResult(Pair<String, String> dataToUnpack) {
        boolean foundArchive = false;
        String innerDir = dataToUnpack.getValue();
        String fileKey = dataToUnpack.getKey();
        String lowerCaseFileName = fileKey.toLowerCase();

        if (lowerCaseFileName.matches(ZIP_EXTENSION_PATTERN)) {
            foundArchive = unZip(innerDir, fileKey);
        } else if (lowerCaseFileName.matches(GEM_EXTENSION_PATTERN)) {
            foundArchive = unTar(lowerCaseFileName, innerDir, fileKey);
            innerDir = innerDir + File.separator + RUBY_DATA_FILE;
            foundArchive = unTar(RUBY_DATA_FILE, innerDir + this.randomString, innerDir);
            innerDir = innerDir + this.randomString;
        } else if (lowerCaseFileName.matches(TAR_EXTENSION_PATTERN)) {
            foundArchive = unTar(lowerCaseFileName, innerDir, fileKey);
            //                        innerDir = innerDir.replaceAll(TAR_SUFFIX, BLANK);
        } else if (lowerCaseFileName.matches(RPM_EXTENSION_PATTERN)) {
            foundArchive = handleRpmFile(innerDir, fileKey);
        } else if (lowerCaseFileName.matches(RAR_EXTENSION_PATTERN)) {
            foundArchive = extractRarFile(innerDir, fileKey);
        } else {
            logger.warn("Error: {} is unsupported archive type", fileKey);
        }
        if (foundArchive) {
            Pair resultArchive = new Pair(lowerCaseFileName, innerDir);
            return resultArchive;
        } else
            return null;
    }

    private boolean extractRarFile(String innerDir, String fileKey) {
        boolean foundArchive;
        File destDir = new File(innerDir);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        try {
            ExtractArchive.extractArchive(fileKey, innerDir);
            foundArchive = true;
        } catch (Exception e) {
            logger.warn("Error extracting file {}: {}", fileKey, e.getMessage());
            try {
                //if the header is missing try to extract the rar file with zip extension - WSE-450
                if (e.getMessage().contains(NULL_HEADER) && new ZipFile(fileKey) instanceof ZipFile) {
                    logger.info("Retrying extraction  {}", fileKey);
                    foundArchive = unZip(innerDir, fileKey);
                }
            } catch (ZipException e1) {
                logger.warn("Error extracting file {}: {}", fileKey, e.getMessage());
                foundArchive = false;
            }
        }
        return true;
    }

    // Open and extract data from zip pattern files
    private boolean unZip(String innerDir, String archiveFile) {
        boolean success = true;
        ZipFile zipFile;
        try {
            zipFile = new ZipFile(archiveFile);
            // Get the list of file headers from the zip file before unpacking
            List fileHeaderList = zipFile.getFileHeaders();

            List<PathMatcher> matchers = Arrays.stream(filesExcludes).map(fileExclude ->
                    FileSystems.getDefault().getPathMatcher(GLOB_PREFIX + fileExclude)).collect(Collectors.toList());
            // Loop through the file headers and extract only files that are not matched by fileExcludes patterns
            for (int i = 0; i < fileHeaderList.size(); i++) {
                FileHeader fileHeader = (FileHeader) fileHeaderList.get(i);
                String fileName = fileHeader.getFileName();
                if (filesExcludes.length > 0) {
                    Predicate<PathMatcher> matchesExcludes = pathMatcher -> pathMatcher.matches(Paths.get(innerDir, fileName));
                    if (matchers.stream().noneMatch(matchesExcludes)) {
                        zipFile.extractFile(fileHeader, innerDir);
                    }
                } else {
                    zipFile.extractFile(fileHeader, innerDir);
                }
            }
        } catch (Exception e) {
            success = false;
            logger.warn("Error extracting file {}: {}", archiveFile, e.getMessage());
            logger.debug("Error extracting file {}: {}", archiveFile, e.getStackTrace());
        } finally {
            // remove reference to zip file
            zipFile = null;
        }
        return success;
    }

    // Open and extract data from Tar pattern files
    private boolean unTar(String fileName, String innerDir, String archiveFile) {
        boolean success = true;
        TarUnArchiver unArchiver = new TarUnArchiver();
        try {
            File destDir = new File(innerDir);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            if (fileName.endsWith(TAR_GZ_SUFFIX) || fileName.endsWith(TGZ_SUFFIX)) {
                unArchiver = new TarGZipUnArchiver();
            } else if (fileName.endsWith(TAR_BZ2_SUFFIX)) {
                unArchiver = new TarBZip2UnArchiver();
            } else if (fileName.endsWith(XZ_SUFFIX)) {
                String destFileUrl = destDir.getCanonicalPath() + Constants.BACK_SLASH + XZ_UN_ARCHIVER_FILE_NAME;
                success = unXz(new File(archiveFile), destFileUrl);
                archiveFile = destFileUrl;
            }
            if (success) {
                unArchiver.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DISABLED, UN_ARCHIVER_LOGGER));
                unArchiver.setSourceFile(new File(archiveFile));
                unArchiver.setDestDirectory(destDir);
                unArchiver.extract();
            }
        } catch (Exception e) {
            success = false;
            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
        }
        return success;
    }

    // extract xz files
    public boolean unXz(File srcFileToArchive, String destFilePath) {
        boolean success = true;
        try {
            XZUnArchiver XZUnArchiver = new XZUnArchiver();
            XZUnArchiver.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DISABLED, UN_ARCHIVER_LOGGER));
            XZUnArchiver.setSourceFile(srcFileToArchive);
            XZUnArchiver.setDestFile(new File(destFilePath));
            XZUnArchiver.extract();
        } catch (Exception e) {
            success = false;
            logger.warn("Failed to extract Xz file : {} - {}", srcFileToArchive.getPath(), e.getMessage());
        }
        return success;
    }

    // Open and extract data from rpm files
    private boolean handleRpmFile(String innerDir, String archiveFile) {
        boolean success = true;
        File rpmFile = new File(archiveFile);
        FileInputStream rpmFIS = null;
        try {
            rpmFIS = new FileInputStream(rpmFile.getPath());
        } catch (FileNotFoundException e) {
            success = false;
            logger.warn("File not found: {}", archiveFile);
        }

        Format format = null;
        ReadableByteChannel channel = Channels.newChannel(rpmFIS);
        ReadableChannelWrapper channelWrapper = new ReadableChannelWrapper(channel);
        try {
            format = new org.redline_rpm.Scanner().run(channelWrapper);
        } catch (IOException e) {
            success = false;
            logger.warn("Error reading RPM file {}: {}", archiveFile, e.getCause());
        }

        if (format != null) {
            Header header = format.getHeader();
            FileOutputStream cpioOS = null;
            FileOutputStream cpioEntryOutputStream = null;
            CpioArchiveInputStream cpioIn = null;
            File cpioFile = null;
            try {
                // extract all .cpio file
                // get input stream according to payload compressor type
                InputStream inputStream;
                AbstractHeader.Entry pcEntry = header.getEntry(Header.HeaderTag.PAYLOADCOMPRESSOR);
                String[] pc = (String[]) pcEntry.getValues();
                if (pc[0].equals(LZMA)) {
                    try {
                        inputStream = new LZMACompressorInputStream(rpmFIS);
                    } catch (Exception e) {
                        throw new IOException("Failed to load LZMA compression stream", e);
                    }
                } else {
                    inputStream = Util.openPayloadStream(header, rpmFIS);
                }

                cpioFile = new File(rpmFile.getPath() + CPIO);
                cpioOS = new FileOutputStream(cpioFile);
                IOUtils.copy(inputStream, cpioOS);
                // extract files from .cpio
                File extractDestination = new File(innerDir);
                extractDestination.mkdirs();
                cpioIn = new CpioArchiveInputStream(new FileInputStream(cpioFile));

                CpioArchiveEntry cpioEntry;
                while ((cpioEntry = (CpioArchiveEntry) cpioIn.getNextEntry()) != null) {
                    String entryName = cpioEntry.getName();
                    String lowercaseName = entryName.toLowerCase();
                    File file = new File(extractDestination, getFileName(entryName));
                    cpioEntryOutputStream = new FileOutputStream(file);
                    IOUtils.copy(cpioIn, cpioEntryOutputStream);
                    String innerExtractionDir;
                    if (lowercaseName.matches(TAR_EXTENSION_PATTERN)) {
                        innerExtractionDir = innerDir + File.separator + entryName + this.randomString;
                        unTar(file.getName(), innerExtractionDir, file.getPath());
                    } else if (lowercaseName.matches(ZIP_EXTENSION_PATTERN)) {
                        innerExtractionDir = innerDir + File.separator + entryName + this.randomString;
                        unZip(innerExtractionDir, file.getPath());
                    }
                    // close
                    closeResource(cpioEntryOutputStream);
                }
            } catch (IOException e) {
                logger.error("Error unpacking rpm file {}: {}", rpmFile.getName(), e.getMessage());
            } finally {
                closeResource(cpioEntryOutputStream);
                closeResource(cpioIn);
                closeResource(cpioOS);
                deleteFile(cpioFile);
            }
        }
        return success;
    }

    private void deleteFile(File cpioFile) {
        try {
            FileUtils.forceDelete(cpioFile);
        } catch (IOException e) {
            logger.warn("Error deleting cpio file {}: {}", cpioFile.getName(), e.getMessage());
        }
    }

    private void closeResource(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                logger.warn("Error closing file {}: {}", resource.toString(), e.getMessage());
            }
        }
    }

    // parse name without directories
    private String getFileName(String name) {
        //check if the environment is linux or windows
        if (name.contains(Constants.FORWARD_SLASH)) {
            name = name.substring(name.lastIndexOf(Constants.FORWARD_SLASH) + 1, name.length());
        } else if (name.contains(Constants.BACK_SLASH)) {
            name = name.substring(name.lastIndexOf(Constants.BACK_SLASH) + 1, name.length());
        }
        return name;
    }


}