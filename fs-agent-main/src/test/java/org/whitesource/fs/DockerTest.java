package org.whitesource.fs;

import org.junit.Ignore;
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
    private static final int ARCHIVE_EXTRACTION_DEPTH = 2;
    private static final String REPOSITORY = "REPOSITORY";
    private static final String SPACES_REGEX = "\\s+";

    @Ignore
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

    @Ignore
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

    @Ignore
    @Test
    public void extractFolder() {

        ArchiveExtractor archiveExtractor = new ArchiveExtractor(ARCHIVE_INCLUDES, ARCHIVE_EXCLUDES, FILE_EXCLUDES);
        String s = archiveExtractor.extractArchives("C:\\Tests\\testDocketAgent", ARCHIVE_EXTRACTION_DEPTH, new ArrayList<>());
        System.out.println("test");
    }


}
