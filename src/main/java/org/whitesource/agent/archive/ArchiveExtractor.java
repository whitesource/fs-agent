package org.whitesource.agent.archive;

import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.codehaus.plexus.archiver.tar.TarBZip2UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.redline_rpm.*;
import org.redline_rpm.header.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.redline_rpm.header.AbstractHeader;
import org.redline_rpm.header.Header;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The class supports recursive deCompression of compressed files (Java, Python & Ruby types).
 *
 * @author anna.rozin
 */
public class ArchiveExtractor {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractor.class);

    private static final String TEMP_FOLDER;
    private static final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String WHITESOURCE_TEMP_FOLDER = "WhiteSource-ArchiveExtractor";

    public static final List<String> ZIP_EXTENSIONS = Arrays.asList("jar", "war", "ear", "egg", "zip", "whl", "sca", "sda");
    public static final List<String> GEM_EXTENSIONS = Arrays.asList("gem");
    public static final List<String> TAR_EXTENSIONS = Arrays.asList("tar.gz", "tar", "tgz", "tar.bz2");
    public static final List<String> RPM_EXTENSIONS = Arrays.asList("rpm");

    public static final String ZIP_EXTENSION_PATTERN;
    public static final String GEM_EXTENSION_PATTERN;
    public static final String TAR_EXTENSION_PATTERN;
    public static final String RPM_EXTENSION_PATTERN;
    public static final String RUBY_DATA_FILE = "data.tar.gz";
    public static final String TAR_SUFFIX = ".tar";
    public static final String GZ_SUFFIX = ".gz";
    public static final String BZ_SUFFIX = ".bz2";
    public static final String LZMA = "lzma";
    public static final String CPIO = ".cpio";

    public static final String TAR_GZ_SUFFIX = TAR_SUFFIX + GZ_SUFFIX;
    public static final String TAR_BZ2_SUFFIX = TAR_SUFFIX + BZ_SUFFIX;
    public static final String UNIX_FILE_SEPARATOR = "/";
    public static final String WINDOWS_FILE_SEPARATOR = "\\";

    public static final String UN_ARCHIVER_LOGGER = "unArchiverLogger";
    public static final String GLOB_PATTERN_PREFIX =  "**/*.";
    public static final String DOT = ".";
    public static final String BLANK = "";
    public static final String PATTERN_PREFIX = ".*\\.";
    public static final String OR = "|";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    public static final String RANDOM_STRING = "wss" + RandomStringUtils.random(10, true, false) + DATE_FORMAT.format(new Date());

    static {
        ZIP_EXTENSION_PATTERN = initializePattern(ZIP_EXTENSIONS);
        GEM_EXTENSION_PATTERN = initializePattern(GEM_EXTENSIONS);
        TAR_EXTENSION_PATTERN = initializePattern(TAR_EXTENSIONS);
        RPM_EXTENSION_PATTERN = initializePattern(RPM_EXTENSIONS);
        TEMP_FOLDER = JAVA_TEMP_DIR.endsWith(File.separator) ? JAVA_TEMP_DIR + WHITESOURCE_TEMP_FOLDER :
                                                        JAVA_TEMP_DIR + File.separator + WHITESOURCE_TEMP_FOLDER;
    }

    private static String initializePattern(List<String> archiveExtensions) {
        StringBuilder sb = new StringBuilder();
        for (String archiveExtension : archiveExtensions) {
            sb.append(PATTERN_PREFIX);
            sb.append(archiveExtension);
            sb.append(OR);
        }
        return sb.toString().substring(0, sb.toString().lastIndexOf(OR));
    }

    /* --- Private members --- */

    private String[] archiveIncludesPattern;
    private String[] archiveExcludesPattern;

    /* --- Constructors --- */

    public ArchiveExtractor(String[] archiveIncludes, String[] archiveExcludes) {
        if (archiveIncludes.length > 0 && StringUtils.isNotBlank(archiveIncludes[0])) {
            this.archiveIncludesPattern = archiveIncludes;
        } else {
            // create ARCHIVE_EXTENSIONS only if archiveIncludes is empty
            this.archiveIncludesPattern = createArchivesArray();
        }
        this.archiveExcludesPattern = archiveExcludes;
    }

    public String getRandomString(){
        return RANDOM_STRING;
    }

    /* --- Public methods --- */

    /**
     * The Method extracts all the Archive files according to the archiveExtractionDepth.
     * archiveExtractionDepth defined by the user in the configuration file.
     *
     * The archiveExtractionDepth default value is 0 - no archive scanning, the max value is 3.
     * By default the method scans jar/war/ear.
     * If archiveIncludes/archiveExcludes params are defined the method will act accordingly.
     *
     * @param scannerBaseDir - directory for scanning.
     * @param archiveExtractionDepth - drill down hierarchy level in archive files
     * @return the temp directory for the extracted files.
     */
    public String extractArchives(String scannerBaseDir, int archiveExtractionDepth) {
        logger.debug("Base directory is {}, extraction depth is set to {}", scannerBaseDir, archiveExtractionDepth);
        String destDirectory = TEMP_FOLDER;
        int separatorIndex = scannerBaseDir.lastIndexOf(File.separator);
        if (separatorIndex != -1) {
            destDirectory = destDirectory + scannerBaseDir.substring(separatorIndex, scannerBaseDir.length());
        }
        extractArchive(scannerBaseDir, destDirectory, archiveExtractionDepth, 0);
        return destDirectory;
    }

    public void deleteArchiveDirectory() {
        File directory = new File(TEMP_FOLDER);
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            logger.warn("Error deleting archive directory", e);
        }
    }

    /* --- Private methods --- */

    private String[] createArchivesArray() {
        Collection<String> archiveExtensions = new ArrayList<String>();
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

    private void extractArchive(String scannerBaseDir, String destDirectory, int archiveExtractionDepth, int curLevel) {
        File file = new File(scannerBaseDir);
        if (file.exists()) {
            if (file.isDirectory()) {
                // scan directory
                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setBasedir(scannerBaseDir);
                scanner.setIncludes(archiveIncludesPattern);
                scanner.setExcludes(archiveExcludesPattern);
                scanner.setCaseSensitive(false);
                scanner.scan();

                String[] fileNames = scanner.getIncludedFiles();
                for (String fileName : fileNames) {
                    String innerDir = destDirectory + File.separator + fileName + RANDOM_STRING;
                    String archiveFile = scannerBaseDir + File.separator + fileName;
                    String lowerCaseFileName = fileName.toLowerCase();
                    if (lowerCaseFileName.matches(ZIP_EXTENSION_PATTERN)) {
                        unZip(fileName, innerDir, archiveFile);
                    } else if (lowerCaseFileName.matches(GEM_EXTENSION_PATTERN)) {
                        unTar(fileName, innerDir, archiveFile);
                        innerDir = innerDir + File.separator + RUBY_DATA_FILE;
                        unTar(RUBY_DATA_FILE, innerDir + RANDOM_STRING, innerDir);
                        innerDir = innerDir + RANDOM_STRING;
                    } else if (lowerCaseFileName.matches(TAR_EXTENSION_PATTERN)) {
                        unTar(fileName, innerDir, archiveFile);
//                        innerDir = innerDir.replaceAll(TAR_SUFFIX, BLANK);
                    } else if (lowerCaseFileName.matches(RPM_EXTENSION_PATTERN)){
                        handleRpmFile(fileName, innerDir, archiveFile);
                    } else {
                        logger.warn("Error: {} is unsupported archive type", fileName);
                        return;
                    }
                    // Extract again if needed according archiveExtractionDepth parameter
                    if (curLevel < archiveExtractionDepth) {
                        extractArchive(innerDir, innerDir, archiveExtractionDepth, curLevel + 1);
                    }
                }
            }
        }
    }

    // Open and extract data from zip pattern files
    private void unZip(String fileName, String innerDir, String archiveFile) {
        try {
            ZipFile zipFile = new ZipFile(archiveFile);
            zipFile.extractAll(innerDir);
        } catch (Exception e) {
            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
            logger.debug("Error extracting file {}: {}", fileName, e.getStackTrace());
        }
    }

    // Open and extract data from Tar pattern files
    private void unTar(String fileName, String innerDir, String archiveFile) {
        TarUnArchiver unArchiver = new TarUnArchiver();
        try {
            if (fileName.endsWith(TAR_GZ_SUFFIX)) {
                unArchiver = new TarGZipUnArchiver();
            } else if (fileName.endsWith(TAR_BZ2_SUFFIX)) {
                unArchiver = new TarBZip2UnArchiver();
            }
            unArchiver.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DISABLED, UN_ARCHIVER_LOGGER));
            unArchiver.setSourceFile(new File(archiveFile));
            File destDir = new File(innerDir);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            unArchiver.setDestDirectory(destDir);
            unArchiver.extract();
        } catch (Exception e) {
            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
        }
    }

    // Open and extract data from rpm files
    private void handleRpmFile(String fileName, String innerDir, String archiveFile) {
        File rpmFile = new File(archiveFile);
        FileInputStream rpmFIS = null;
        try {
            rpmFIS = new FileInputStream(rpmFile.getPath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Format format = null;
        ReadableByteChannel channel = Channels.newChannel(rpmFIS);
        ReadableChannelWrapper channelWrapper = new ReadableChannelWrapper(channel);
        try {
            format = new org.redline_rpm.Scanner().run(channelWrapper);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                     innerExtractionDir = innerDir + File.separator + entryName + RANDOM_STRING;
                    unTar(file.getName(),innerExtractionDir,file.getPath());
                } else if (lowercaseName.matches(ZIP_EXTENSION_PATTERN)) {
                    innerExtractionDir = innerDir + File.separator + entryName + RANDOM_STRING;
                        unZip(file.getName(),innerExtractionDir,file.getPath());
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
        if (name.contains(UNIX_FILE_SEPARATOR)) {
            name = name.substring(name.lastIndexOf(UNIX_FILE_SEPARATOR) + 1, name.length());
        } else if (name.contains(WINDOWS_FILE_SEPARATOR)) {
            name = name.substring(name.lastIndexOf(WINDOWS_FILE_SEPARATOR) + 1, name.length());
        }
        return name;
    }

}
