package org.agent.archive;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.archive.ArchiveExtractor;

import java.io.File;
import java.nio.file.Paths;

public class ArchiveExtractorTest {
    @Test
    public void shouldCreateTheSameZipStructure() {
        String currentDirectory = System.getProperty("user.dir");
        String[] archiveIncludes = new String[]{"test/resources/**/*.zip"};
        String[] archiveExcludes = new String[0];
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes);

        String scannerBaseDir = Paths.get(currentDirectory,"src").toString();
        int archiveExtractionDepth = 4;
        String unzipFolder = archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth);
        Assert.assertNotNull(unzipFolder);

        File fileDepth3 = new File(Paths.get(unzipFolder,"test\\resources\\node_modules\\accepts\\index.js").toString());
        Assert.assertTrue(fileDepth3.exists());
    }
}
