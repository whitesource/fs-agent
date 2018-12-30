package org.whitesource.agent;

import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.UniqueNamesGenerator;

import java.io.File;
import java.nio.file.Paths;


public class TempFolders {

    /* --- Static members --- */

    private static final String PATH_TO_TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String WHITESOURCE_ARCHIVE_EXTRACTOR = "WhiteSource-ArchiveExtractor";
    private static final String WHITE_BUILD_GRADLE_FOLDER = "WhiteSource-Build-Gradle";
    private static final String WHITESOURCE_HTML_RESOLVER = "WhiteSource-html-resolver";
    private static final String WHITESOURCE_DOTNET_RESOLVER = "WhiteSource-DotnetRestore";
    private static final String WHITESOURCE_DOCKER = "WhiteSource-Docker";
    private static final String WHITESOURCE_SCM_CONNECTOR_TMP_DIRECTORY = "WhiteSource-ScmConnector";
    private static final String WHITESOURCE_PLATFORM_DEPENDENT_TMP_DIR = "WhiteSource-PlatformDependentFiles";
    private static final String WHITESOURCE_PYTHON_TEMP_FOLDER = "Whitesource_python_resolver";

    public static final String UNIQUE_HTML_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_HTML_RESOLVER, Constants.EMPTY_STRING);
    public static final String UNIQUE_GRADLE_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITE_BUILD_GRADLE_FOLDER, Constants.EMPTY_STRING);
    public static final String UNIQUE_DOTNET_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_DOTNET_RESOLVER, Constants.EMPTY_STRING);
    public static final String UNIQUE_PYTHON_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_PYTHON_TEMP_FOLDER, Constants.EMPTY_STRING);
    public static final String UNIQUE_DOCKER_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_DOCKER, Constants.EMPTY_STRING);
    public static final String UNIQUE_SCM_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_SCM_CONNECTOR_TMP_DIRECTORY, Constants.EMPTY_STRING);
    public static final String UNIQUE_PLATFORM_DEPENDENT_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_PLATFORM_DEPENDENT_TMP_DIR, Constants.EMPTY_STRING);
    public static final String UNIQUE_WHITESOURCE_ARCHIVE_EXTRACTOR_TEMP_FOLDER = UniqueNamesGenerator.createUniqueName(WHITESOURCE_ARCHIVE_EXTRACTOR, Constants.EMPTY_STRING);

    private static final String PATH_TO_UNIQUE_HTML_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_HTML_TEMP_FOLDER).toString();
    private static final String PATH_TO_UNIQUE_GRADLE_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_GRADLE_TEMP_FOLDER).toString();
    private static final String PATH_TO_UNIQUE_DOTNET_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_DOTNET_TEMP_FOLDER).toString();
    private static final String PATH_TO_UNIQUE_PYTHON_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_PYTHON_TEMP_FOLDER).toString();
    private static final String PATH_TO_UNIQUE_DOCKER_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_DOCKER_TEMP_FOLDER).toString();
    private static final String PATH_TO_SCM_CONNECTOR_TMP_DIRECTORY = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_SCM_TEMP_FOLDER).toString();
    private static final String PATH_TO_UNIQUE_ARCHIVE_EXTRACTOR_TEMP_FOLDER = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_WHITESOURCE_ARCHIVE_EXTRACTOR_TEMP_FOLDER).toString();

    // Agents api temp folder - CheckSumUtils folder :: calculateOtherPlatformSha1 method
    private static final String PATH_TO_PLATFORM_DEPENDENT_TMP_DIR = Paths.get(PATH_TO_TEMP_DIR, UNIQUE_PLATFORM_DEPENDENT_TEMP_FOLDER).toString();

    /* --- Constructors --- */

    public TempFolders() {

    }

    /* --- Methods --- */

    public void deleteTempFolders() {
        deleteTempFoldersHelper(PATH_TO_UNIQUE_HTML_TEMP_FOLDER);
        deleteTempFoldersHelper(PATH_TO_UNIQUE_GRADLE_TEMP_FOLDER);
        deleteTempFoldersHelper(PATH_TO_UNIQUE_DOTNET_TEMP_FOLDER);
        deleteTempFoldersHelper(PATH_TO_UNIQUE_PYTHON_TEMP_FOLDER);
        deleteTempFoldersHelper(PATH_TO_UNIQUE_DOCKER_TEMP_FOLDER);
        deleteTempFoldersHelper(PATH_TO_SCM_CONNECTOR_TMP_DIRECTORY);
        deleteTempFoldersHelper(PATH_TO_PLATFORM_DEPENDENT_TMP_DIR);
        deleteTempFoldersHelper(PATH_TO_UNIQUE_ARCHIVE_EXTRACTOR_TEMP_FOLDER);
    }

    public void deleteTempFoldersHelper(String path) {
        if (path != null) {
            File file = new File(path);
            if (file != null) {
                FilesUtils.deleteDirectory(file);
            }
        }
    }


}
