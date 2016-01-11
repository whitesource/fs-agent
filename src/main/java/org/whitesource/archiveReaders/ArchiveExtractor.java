package org.whitesource.archiveReaders;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The class supports recursive deCompression of compressed files (Java, Python & Ruby types).
 *
 * @author anna.rozin
 */
public class ArchiveExtractor {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractor.class);

    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + "WhiteSource-ArchiveExtractor";

    public static final List<String> ZIP_EXTENSIONS = Arrays.asList("jar", "war", "ear", "egg", "zip", "whl", "sca", "sda");
    public static final List<String> GEM_EXTENSIONS = Arrays.asList("gem");
    public static final List<String> TAR_EXTENSIONS = Arrays.asList("tar.gz", "tar");

    public static final String ZIP_EXTENSION_PATTERN ;
    public static final String GEM_EXTENSION_PATTERN ;
    public static final String TAR_EXTENSION_PATTERN ;
    public static final String RUBY_DATA_FILE = "data.tar.gz";
    public static final String TAR_SUFFIX = ".tar";
    public static final String TAR_GZ_SUFFIX = TAR_SUFFIX + ".gz";

    public static final String UN_ARCHIVER_LOGGER = "unArchiverLogger";
    public static final String GLOB_PATTERN_PREFIX =  "**/*.";
    public static final String DOT = ".";
    public static final String BLANK = "";
    public static final String PATTERN_PREFIX = ".*\\.";
    public static final String OR = "|";

    static {
        ZIP_EXTENSION_PATTERN = initializePattern(ZIP_EXTENSIONS);
        GEM_EXTENSION_PATTERN = initializePattern(GEM_EXTENSIONS);
        TAR_EXTENSION_PATTERN = initializePattern(TAR_EXTENSIONS);
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
        String destDirectory = TEMP_FOLDER + scannerBaseDir.substring(scannerBaseDir.lastIndexOf(File.separator), scannerBaseDir.length());
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
                    String innerDir = destDirectory + File.separator + fileName.substring(0, fileName.lastIndexOf(DOT));
                    String archiveFile = scannerBaseDir + File.separator + fileName;
                    String lowerCaseFileName = fileName.toLowerCase();
                    if (lowerCaseFileName.matches(ZIP_EXTENSION_PATTERN)) {
                            unZip(fileName, innerDir, archiveFile);
                        } else if (lowerCaseFileName.matches(GEM_EXTENSION_PATTERN)) {
                            unTar(fileName, innerDir, archiveFile);
                            innerDir = innerDir + File.separator + RUBY_DATA_FILE ;
                            unTar(RUBY_DATA_FILE, innerDir.substring(0, innerDir.lastIndexOf(DOT)) , innerDir);
                            innerDir = innerDir.replaceAll(TAR_GZ_SUFFIX, BLANK);
                        } else if (lowerCaseFileName.matches(TAR_EXTENSION_PATTERN)) {
                            unTar(fileName, innerDir, archiveFile);
                            innerDir = innerDir.replaceAll(TAR_SUFFIX, BLANK);
                        } else {
                            logger.warn("Error: {} is unsupported archive type", fileName.substring(fileName.lastIndexOf(DOT)));
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
        } catch (ZipException e) {
            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
        }
    }

    // Open and extract data from Tar pattern files
    private void unTar(String fileName,String innerDir, String archiveFile) {
        TarUnArchiver unArchiver = new TarUnArchiver();
        try {
            if (fileName.endsWith(TAR_GZ_SUFFIX)) {
                innerDir = innerDir.substring(0, innerDir.lastIndexOf(DOT));
                unArchiver = new TarGZipUnArchiver();
            } else if (fileName.endsWith(TAR_SUFFIX) || fileName.endsWith(GEM_EXTENSION_PATTERN)) {
                unArchiver = new TarUnArchiver();
            }
            unArchiver.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DISABLED, UN_ARCHIVER_LOGGER));
            unArchiver.setSourceFile(new File(archiveFile));
            File destDir = new File(innerDir);
            if (!destDir.exists()){
                destDir.mkdirs();
            }
            unArchiver.setDestDirectory(destDir);
            unArchiver.extract();
        } catch (Exception e) {
            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
        }
    }

}
