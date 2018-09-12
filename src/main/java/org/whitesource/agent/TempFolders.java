package org.whitesource.agent;

import org.whitesource.agent.utils.FilesUtils;
import java.io.File;
import java.nio.file.Paths;


public class TempFolders {

    /* --- Static members --- */


    public static final String WHITESOURCE_ARCHIVE_EXTRACTOR = "WhiteSource-ArchiveExtractor";
    public static final String WHITE_BUILD_GRADLE_FOLDER = "WhiteSource-Build-Gradle";
    public static final String SCM_CONNECTOR_TMP_DIRECTORY = Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-ScmConnector").toString();
    public static final String PATH_TO_ARCHIVE_EXTRACTOR = Paths.get(System.getProperty("java.io.tmpdir"), WHITESOURCE_ARCHIVE_EXTRACTOR).toString();
    public static final String BUILD_GRADLE_DIRECTORY = System.getProperty("java.io.tmpdir") + WHITE_BUILD_GRADLE_FOLDER;

    // Agents api temp folder - CheckSumUtils folder :: calculateOtherPlatformSha1 method
    public static final String PLATFORM_DEPENDENT_TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-PlatformDependentFiles").toString();

    /* --- Constructors --- */

    public TempFolders() {

    }

    /* --- Methods --- */

    public void deleteTempFolders() {
        deleteTempFoldersHelper(PATH_TO_ARCHIVE_EXTRACTOR);
        deleteTempFoldersHelper(SCM_CONNECTOR_TMP_DIRECTORY);
        deleteTempFoldersHelper(PLATFORM_DEPENDENT_TMP_DIR);
    }

    public void deleteTempFoldersHelper(String path) {
        if (path != null) {
            File file = new File(path);
            if(file != null) {
                FilesUtils.deleteDirectory(file);
            }
        }
    }
    

}
