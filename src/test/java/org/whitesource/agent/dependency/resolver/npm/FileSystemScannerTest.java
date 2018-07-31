package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.FilesScanner;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class FileSystemScannerTest {
    private static String FOLDER_TO_TEST = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_NPN_PROJECTS);

    @Ignore
    @Test
    public void shouldRemoveJsFilesFromNpmFolders() {
        FilesScanner f = new FilesScanner();
        Properties p = TestHelper.getPropertiesFromFile();
        String[] filesJSBegin = f.getDirectoryContent(
                FOLDER_TO_TEST,
                p.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(Constants.WHITESPACE),
                p.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(Constants.WHITESPACE),
                false,
                false);

        String[] filesPackageJson = f.getDirectoryContent(
                FOLDER_TO_TEST,
                new String[]{"**/*package.json"},
                p.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(Constants.WHITESPACE),
                false,
                false);


        List<String> excludes = Arrays.stream(filesPackageJson).map(x -> new File(x).getParent() + "\\**\\*.js").collect(Collectors.toList());
        excludes.add("\\**\\*.js");

        String[] stockArr = excludes.toArray(new String[excludes.size()]);

        FilesScanner fs = new FilesScanner();
        String[] filesJSEnd = fs.getDirectoryContent(
                FOLDER_TO_TEST,
                p.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(Constants.WHITESPACE),
                stockArr,
                false,
                false);

        //most of the files should be removed
        Assert.assertTrue(filesJSBegin.length > filesJSEnd.length);
    }
}