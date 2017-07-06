import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.utils.FilesScanner;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Created by eugen.horovitz on 7/2/2017.
 */
public class FileSystemScannerTest {

    @Test
    public void shouldRemoveJsFilesFromNpmFolders() {
        FilesScanner f = new FilesScanner();
        Properties p = TestHelper.getPropertiesFromFile();
        String[] filesJSBegin = f.getFileNames(
                TestHelper.FOLDER_TO_TEST,
                p.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(" "),
                p.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(" "),
                false,
                false);

        String[] filesPackageJson = f.getFileNames(
                TestHelper.FOLDER_TO_TEST,
                new String[]{"**/*package.json"},
                p.getProperty(ConfigPropertyKeys.EXCLUDES_PATTERN_PROPERTY_KEY).split(" "),
                false,
                false);


        List<String> excludes = Arrays.stream(filesPackageJson).map(x -> new File(x).getParent() + "\\**\\*.js").collect(Collectors.toList());
        excludes.add("\\**\\*.js");

        String[] stockArr = excludes.toArray(new String[excludes.size()]);

        FilesScanner fs = new FilesScanner();
        String[] filesJSEnd = fs.getFileNames(
                TestHelper.FOLDER_TO_TEST,
                p.getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY).split(" "),
                stockArr,
                false,
                false);

        //most of the files should be removed
        Assert.assertTrue(filesJSBegin.length > filesJSEnd.length);
    }
}