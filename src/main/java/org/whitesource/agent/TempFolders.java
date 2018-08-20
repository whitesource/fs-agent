package org.whitesource.agent;

import org.whitesource.agent.utils.FilesUtils;
import java.io.File;
import java.nio.file.Paths;


public class TempFolders {
    public static final String WHITESOURCE_HTML_RESOLVER = "whitesource-html-resolver";
    public static final String WHITESOURCE_ARCHIVE_EXTRACTOR = "WhiteSource-ArchiveExtractor";
    public static final String SCM_CONNECTOR_TMP_DIRECTORY= Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-ScmConnector").toString();
    public static final String PLATFORM_DEPENDEND_TMP_DIR= Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-PlatformDependentFiles").toString();

    public static String pathToArchiveExtractor= Paths.get(System.getProperty("java.io.tmpdir"), WHITESOURCE_ARCHIVE_EXTRACTOR).toString();;
    public static String pathToHTMLResolver=Paths.get(System.getProperty("java.io.tmpdir"), WHITESOURCE_HTML_RESOLVER).toString();;
    //public static String pathToSCMConnector= Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-ScmConnector").toString();;

    public static void deleteTempFolders() {
        deleteTempFoldersHelper(pathToArchiveExtractor);
        deleteTempFoldersHelper(pathToHTMLResolver);
        deleteTempFoldersHelper(SCM_CONNECTOR_TMP_DIRECTORY);
        deleteTempFoldersHelper(PLATFORM_DEPENDEND_TMP_DIR);
    }

    private static void deleteTempFoldersHelper(String path)
    {
        if(path!=null) {
            File file=new File(path);
            if(file!=null) {
                FilesUtils.deleteDirectory(file);
            }
        }

    }
    

}
