package org.whitesource.agent.archive;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ArchiveExtractorTest {

    @Ignore
    @Test
    public void shouldNotUnpackExcludes() {
        // this test checks that unwanted files ( for example class files are not extracted during the unarchive process)
        String[] archiveIncludes = new String[]{"**/*.jar", "**/*.war"};
        String[] archiveExcludes = new String[0];

        String[] fileExcludes = new String[]{"**/*.class", "**/*.html"};
        int archiveExtractionDepth = 5;

        ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, fileExcludes);
        String scannerFile = Paths.get(Constants.EMPTY_STRING, "C:\\Issues\\bigJar\\wss-server-1.1.0-SNAPSHOT.war").toString();
        String unzipFolderFilter = archiveExtractor.extractArchives(scannerFile, archiveExtractionDepth, new ArrayList<>());

        archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, new String[0]);
        scannerFile = Paths.get(Constants.EMPTY_STRING, "C:\\Issues\\bigJar\\wss-server-1.1.0-SNAPSHOT.war").toString();
        String unzipFolderAll = archiveExtractor.extractArchives(scannerFile, archiveExtractionDepth, new ArrayList<>());

        FilesScanner fs = new FilesScanner();
        String[] filesAll = fs.getDirectoryContent(unzipFolderAll, new String[]{"**/*.*"}, new String[0], false, false);
        String[] filesClass = fs.getDirectoryContent(unzipFolderAll, fileExcludes, new String[0], false, false);

        String[] filesFiltered = fs.getDirectoryContent(unzipFolderFilter, new String[]{"**/*.*"}, new String[0], false, false);
        Assert.assertEquals(filesAll.length, filesClass.length + filesFiltered.length);
    }

    @Ignore
    @Test
    public void shouldWorkWithSingleFile() {
        String[] archiveIncludes = new String[]{"**/*.zip"};
        String[] archiveExcludes = new String[0];
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, new String[]{"*.js"});

        File file = TestHelper.getFileFromResources("dist.zip");
        int archiveExtractionDepth = 4;
        String unzipFolder = archiveExtractor.extractArchives(file.getAbsolutePath(), archiveExtractionDepth, new ArrayList<>());
        Assert.assertNotNull(unzipFolder);

        try {
            Files.delete(Paths.get(unzipFolder));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Ignore
    @Test
    public void shouldCreateTheSameZipStructure() {
        String unzipFolder = getUnzippedFolderFromTest();
        Assert.assertNotNull(unzipFolder);

        File fileDepth3 = new File(Paths.get(unzipFolder, TestHelper.getOsRelativePath(
                "src_depth_2\\test\\resources\\dist\\node_modules\\node_modules\\accepts\\accepts\\index.js")).toString());
        Assert.assertTrue(fileDepth3.exists());

        for (Integer i = 0; i < 10; i++) {
            getUnzippedFolderFromTest();
        }
    }

    @Ignore
    @Test
    public void shouldCreateTheSameZipStructureMultiThreading() {
        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        List<Future<Pair<String, Integer>>> handles = new ArrayList<>();

        List<Integer> unitsOfWork = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            unitsOfWork.add(i);
        }
        List<Callable<Pair<String, Integer>>> callableList = new ArrayList<>();
        unitsOfWork.stream().forEach(unitOfWork -> callableList.add(() -> processUnitOfWork(unitOfWork)));

        for (Callable<Pair<String, Integer>> callable : callableList) {
            Future<Pair<String, Integer>> handle = executorService.submit(callable);
            handles.add(handle);
        }

        Map<String, Integer> results = new HashMap<>();
        for (Future<Pair<String, Integer>> h : handles) {
            try {
                Pair<String, Integer> d = h.get();
                results.put(d.getKey(), d.getValue());
            } catch (InterruptedException e) {

            } catch (ExecutionException e) {

            }
        }
        executorService.shutdownNow();
        Assert.assertEquals(10, results.size());
    }

    private Pair<String, Integer> processUnitOfWork(Integer unitOfWork) throws Exception {
        return new Pair<>(getUnzippedFolderFromTest(), unitOfWork);
    }

    private String getUnzippedFolderFromTest() {
        String currentDirectory = System.getProperty("user.dir");
        String[] archiveIncludes = new String[]{"test/resources/**/*.zip"};
        String[] archiveExcludes = new String[0];
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes, new String[0]);

        String scannerBaseDir = Paths.get(currentDirectory, Constants.SRC).toString();
        int archiveExtractionDepth = 4;
        String unzipFolder = archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth, new ArrayList<>());
        return unzipFolder;
    }
}