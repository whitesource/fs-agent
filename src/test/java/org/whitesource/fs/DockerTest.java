package org.whitesource.fs;

import org.junit.Test;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.docker.DockerImage;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import static org.whitesource.agent.hash.FileExtensions.ARCHIVE_EXCLUDES;
import static org.whitesource.agent.hash.FileExtensions.ARCHIVE_INCLUDES;

/**
 * @author chen.luigi
 */
public class DockerTest {

    private static final String DOCKER_SAVE_IMAGE_COMMAND = "docker save -o";
    private static final String O_PARAMETER = "-o";
    public static final String[] FILE_EXCLUDES = {"**/*.class", "**/*.html"};

    public static final String WHITE_SOURCE_DOCKER = "WhiteSource-Docker";
    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + WHITE_SOURCE_DOCKER;
    private static final String ARCHIVE_EXTRACTOR_TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-ArchiveExtractor";
    private static final String TAR_SUFFIX = ".tar";
    private static final int ARCHIVE_EXTRACTION_DEPTH = 2;
    private static final boolean PARTIAL_SHA1_MATCH = false;
    private static final String WINDOWS_PATH_SEPARATOR = "\\";
    private static final String UNIX_PATH_SEPARATOR = "/";
    private static final String NEW_LINE = "\\r?\\n";
    private static final String REPOSITORY = "REPOSITORY";
    private static final String SPACES_REGEX = "\\s+";


    @Test
    public void getDockerImages() {
        String line = null;
        Collection<DockerImage> dockerImages = new LinkedList<>();
        try {
            Process process = Runtime.getRuntime().exec("docker images");
            process.waitFor();
            InputStream inputStream = process.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                if (!line.startsWith(REPOSITORY)) {
                    String[] dockerImageString = line.split(SPACES_REGEX);
                    dockerImages.add(new DockerImage(dockerImageString[0], dockerImageString[1], dockerImageString[2]));
                }
            }
            if (!dockerImages.isEmpty()) {
                System.out.println("test");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void saveImages() {
        //ProcessBuilder pb = new ProcessBuilder("C:/Tests/testDocketAgent/test.bat");
        try {
            Runtime.getRuntime().exec(DOCKER_SAVE_IMAGE_COMMAND + "3fd9065eaf02" + O_PARAMETER + TEMP_FOLDER).waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        extractFolder();
    }

    @Test
    public void extractFolder() {

        ArchiveExtractor archiveExtractor = new ArchiveExtractor(ARCHIVE_INCLUDES, ARCHIVE_EXCLUDES, FILE_EXCLUDES);
        String s = archiveExtractor.extractArchives("C:\\Tests\\testDocketAgent", ARCHIVE_EXTRACTION_DEPTH, new ArrayList<>());
        System.out.println("test");
    }

    @Test
    public void createProjects() {
    /*    File containerTarFile = new File(TEMP_FOLDER, containerName + TAR_SUFFIX);
        File containerTarExtractDir = new File(TEMP_FOLDER, containerName);
        containerTarExtractDir.mkdir();
        File containerTarArchiveExtractDir = new File(ARCHIVE_EXTRACTOR_TEMP_FOLDER, containerName);
        containerTarArchiveExtractDir.mkdir();

        //logger.info("Exporting Container to {} (may take a few minutes)", containerTarFile.getPath());
        SaveImageCmd exportContainerCmd = dockerClient.saveImageCmd(container.getImageId());
        InputStream is = exportContainerCmd.exec();
        try {
            // copy input stream to tar archive
            *//*ExtractProgressIndicator progressIndicator = new ExtractProgressIndicator(containerTarFile, container.getSizeRootFs());
            new Thread(progressIndicator).start();
            FileUtils.copyInputStreamToFile(is, containerTarFile);
            progressIndicator.finished();*//*
            //logger.info("Successfully Exported Container to {}", containerTarFile.getPath());

            // extract tar archive
            extractTarArchive(containerTarFile, containerTarExtractDir);

            // scan files
            String extractPath = containerTarExtractDir.getPath();
            List<DependencyInfo> dependencyInfos = new FileSystemScanner(fsaConfiguration.getResolver(), fsaConfiguration.getAgent()).createProjects(
                    Arrays.asList(extractPath), false,fsaConfiguration.getAgent().getIncludes(), fsaConfiguration.getAgent().getExcludes(),
                    fsaConfiguration.getAgent().getGlobCaseSensitive(), ARCHIVE_EXTRACTION_DEPTH, ARCHIVE_INCLUDES,
                    ARCHIVE_EXCLUDES, false, fsaConfiguration.getAgent().isFollowSymlinks(),
                    new ArrayList<String>(), PARTIAL_SHA1_MATCH);

            // modify file paths relative to the container
            for (DependencyInfo dependencyInfo : dependencyInfos) {
                String systemPath = dependencyInfo.getSystemPath();
                if (StringUtils.isNotBlank(systemPath)) {
                    String containerRelativePath = systemPath;
                    containerRelativePath.replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
                    containerRelativePath = containerRelativePath.substring(containerRelativePath.indexOf(WHITE_SOURCE_DOCKER + WINDOWS_PATH_SEPARATOR) + WHITE_SOURCE_DOCKER.length() + 1);
                    containerRelativePath = containerRelativePath.substring(containerRelativePath.indexOf(WINDOWS_PATH_SEPARATOR) +1);
                    dependencyInfo.setSystemPath(containerRelativePath);
                }
            }
            projectInfo.getDependencies().addAll(dependencyInfos);
        } catch (IOException e) {
            logger.error("Error exporting container {}: {}", containerId, e.getMessage());
            logger.debug("Error exporting container {}", containerId, e);
        } catch (ArchiverException e) {
            logger.error("Error extracting {}: {}", containerTarFile, e.getMessage());
            logger.debug("Error extracting tar archive", e);
        } finally {
            IOUtils.closeQuietly(is);
            FileUtils.deleteQuietly(containerTarFile);
            FileUtils.deleteQuietly(containerTarExtractDir);
            FileUtils.deleteQuietly(containerTarArchiveExtractDir);
        }
    }*/
    }
}
