package org.whitesource.agent.archive;

import javafx.util.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ArchiveExtractorTest {
    @Test
    public void shouldCreateTheSameZipStructure() {
        String unzipFolder = getUnzippedFolderFromTest();
        Assert.assertNotNull(unzipFolder);

        File fileDepth3 = new File(Paths.get(unzipFolder, "src_depth_2\\test\\resources\\dist\\node_modules\\node_modules\\accepts\\accepts\\index.js").toString());
        Assert.assertTrue(fileDepth3.exists());

        for (Integer i = 0; i < 10; i++) {
            getUnzippedFolderFromTest();
        }
    }

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
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes);

        String scannerBaseDir = Paths.get(currentDirectory, "src").toString();
        int archiveExtractionDepth = 4;
        String unzipFolder = archiveExtractor.extractArchives(scannerBaseDir, archiveExtractionDepth);
        return unzipFolder;
    }
}