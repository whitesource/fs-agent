package org.whitesource.archiveReaders;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 *
 * The class supports recursive deCompression of compressed files (Java types - jar, ear and war).
 * @author anna.rozin
 */
public class ArchiveExtractor {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractor.class);

    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + "WhiteSource-ArchiveExtractor";

    public static final String DOT = ".";
    public static final String[] JAVA_EXTENSION = {"**/*.jar", "**/*.war", "**/*.ear"};
    public static final String JAVA_EXTENSION_PATTERN = ".*\\.jar|.*\\.ear|.*\\.war";

    /* --- Private members --- */

    private String[] javaIncludesPattern;
    private String[] javaExcludesPattern;

    /* --- Constructors --- */

    public ArchiveExtractor(String[] archiveIncludes, String[] archiveExcludes) {
        if (archiveIncludes.length > 0 && StringUtils.isNotBlank(archiveIncludes[0])) {
            this.javaIncludesPattern = archiveIncludes;
        } else {
            this.javaIncludesPattern = JAVA_EXTENSION;
        }
        this.javaExcludesPattern = archiveExcludes;
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

    private void extractArchive(String scannerBaseDir, String destDirectory, int archiveExtractionDepth, int curLevel) {
        File file = new File(scannerBaseDir);
        if (file.exists()) {
            if (file.isDirectory()) {
                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setBasedir(scannerBaseDir);
                scanner.setIncludes(javaIncludesPattern);
                scanner.setExcludes(javaExcludesPattern);
                scanner.scan();
                String[] fileNames = scanner.getIncludedFiles();
                for (String fileName : fileNames) {
                    if (fileName.matches(JAVA_EXTENSION_PATTERN)) {
                        try {
                            String innerDir = destDirectory + File.separator +
                                    fileName.substring(0, fileName.lastIndexOf(DOT));
                            File unzipDestination = new File(innerDir);
                            ZipFile zipFile = new ZipFile(scannerBaseDir + File.separator + fileName);
                            zipFile.extractAll(unzipDestination.getPath());
                            if (curLevel < archiveExtractionDepth) {
                                extractArchive(innerDir, innerDir, archiveExtractionDepth, curLevel + 1);
                            }
                        } catch (ZipException e) {
                            logger.warn("Error extracting file {}: {}", fileName, e.getMessage());
                        }
                    } else {
                        logger.warn("Error: {} is unsupported archive type", fileName.substring(fileName.lastIndexOf(DOT)));
                    }
                }
            }
        }
    }
}
