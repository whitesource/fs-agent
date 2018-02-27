package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.hash.FileExtensions;
import org.whitesource.fs.FSAConfiguration;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.whitesource.agent.archive.ArchiveExtractor.TAR_SUFFIX;

/**
 * @author chen.luigi
 */
public class DockerResolver {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(DockerResolver.class);

    private static final String WHITE_SOURCE_DOCKER = "WhiteSource-Docker";
    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + WHITE_SOURCE_DOCKER;
    private static final String ARCHIVE_EXTRACTOR_TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-ArchiveExtractor";
    private static final String[] FILE_EXCLUDES = {"**/*.class", "**/*.jar"};
    private static final String DOCKER_SAVE_IMAGE_COMMAND = "docker save";
    private static final String O_PARAMETER = "-o";
    private static final String REPOSITORY = "REPOSITORY";
    private static final String SPACES_REGEX = "\\s+";
    private static final String SPACE = " ";
    private static final String DOCKER_NAME_FORMAT_STRING = "{0} {1} ({2})";
    private static final MessageFormat DOCKER_NAME_FORMAT = new MessageFormat(DOCKER_NAME_FORMAT_STRING);
    private static final String DOCKER_IMAGES = "docker images";
    private static final boolean PARTIAL_SHA1_MATCH = false;
    private static final int ARCHIVE_EXTRACTION_DEPTH = 2;
    private static final String WINDOWS_PATH_SEPARATOR = "\\";
    private static final String UNIX_PATH_SEPARATOR = "/";

    /* --- Members --- */

    private FSAConfiguration config;

    /* --- Constructor --- */

    public DockerResolver(FSAConfiguration config) {
        this.config = config;
    }

    /* --- Public methods --- */

    /**
     * Create project for each image
     */
    public Collection<AgentProjectInfo> createProjects() {
        logger.info("Start creating projects");
        Collection<AgentProjectInfo> projects = new LinkedList<>();
        String line = null;
        Collection<DockerImage> dockerImages = new LinkedList<>();
        Collection<DockerImage> dockerImagesToScan;
        try {
            Process process = Runtime.getRuntime().exec(DOCKER_IMAGES);
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
                dockerImagesToScan = getDockerImagesToScan(dockerImages, config.getAgent().getDockerIncludes(), config.getAgent().getDockerExcludes());
                if (!dockerImagesToScan.isEmpty()) {
                    saveDockerImages(dockerImagesToScan, projects);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return projects;
    }

    /* --- Private methods --- */

    /**
     * Filter the images by includes and excludes lists
     */
    private Collection<DockerImage> getDockerImagesToScan(Collection<DockerImage> dockerImages, String[] dockerImageIncludes, String[] dockerImageExcludes) {
        logger.info("Filtering docker image list by includes and excludes lists");
        Collection<DockerImage> dockerImagesToScan = new LinkedList<>();
        Collection<String> imageIncludesList = Arrays.asList(dockerImageIncludes);
        Collection<String> imageExcludesList = Arrays.asList(dockerImageExcludes);
        for (DockerImage dockerImage : dockerImages) {
            String dockerImageString = dockerImage.getRepository() + SPACE + dockerImage.getTag() + SPACE + dockerImage.getId();
            for (String imageInclude : imageIncludesList) {
                if (StringUtils.isNotBlank(imageInclude)) {
                    Pattern p = Pattern.compile(imageInclude);
                    Matcher m = p.matcher(dockerImageString);
                    if (m.find()) {
                        dockerImagesToScan.add(dockerImage);
                    }
                }
            }
            for (String imageExcludes : imageExcludesList) {
                if (StringUtils.isNotBlank(imageExcludes)) {
                    Pattern p = Pattern.compile(imageExcludes);
                    Matcher m = p.matcher(dockerImageString);
                    if (m.find()) {
                        dockerImagesToScan.remove(dockerImage);
                    }
                }
            }
        }
        return dockerImagesToScan;
    }

    /**
     * Save docker images and scan files
     */
    private void saveDockerImages(Collection<DockerImage> dockerImages, Collection<AgentProjectInfo> projects) {
        Process process = null;
        logger.info("Saving the images");
        for (DockerImage dockerImage : dockerImages) {
            // create agent project info
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, dockerImage.getId(), dockerImage.getRepository(), dockerImage.getTag()), null));
            projects.add(projectInfo);

            File containerTarFile = new File(TEMP_FOLDER, dockerImage.getRepository() + TAR_SUFFIX);
            File containerTarExtractDir = new File(TEMP_FOLDER, dockerImage.getRepository());
            containerTarExtractDir.mkdirs();
            File containerTarArchiveExtractDir = new File(ARCHIVE_EXTRACTOR_TEMP_FOLDER, dockerImage.getRepository());
            containerTarArchiveExtractDir.mkdirs();
            try {
                Runtime.getRuntime().exec(DOCKER_SAVE_IMAGE_COMMAND + SPACE + dockerImage.getId() + SPACE + O_PARAMETER + SPACE + containerTarFile.getPath());

                // extract tar archive
                extractTarArchive(containerTarFile, containerTarExtractDir);

                // scan files
                String extractPath = containerTarExtractDir.getPath();
                List<DependencyInfo> dependencyInfos = new FileSystemScanner(config.getResolver(), config.getAgent()).createProjects(
                        Arrays.asList(extractPath), false, config.getAgent().getIncludes(), config.getAgent().getExcludes(),
                        config.getAgent().getGlobCaseSensitive(), ARCHIVE_EXTRACTION_DEPTH, FileExtensions.ARCHIVE_INCLUDES,
                        FileExtensions.ARCHIVE_EXCLUDES, false, config.getAgent().isFollowSymlinks(),
                        new ArrayList<String>(), PARTIAL_SHA1_MATCH);

                // modify file paths relative to the container
                for (DependencyInfo dependencyInfo : dependencyInfos) {
                    String systemPath = dependencyInfo.getSystemPath();
                    if (StringUtils.isNotBlank(systemPath)) {
                        String containerRelativePath = systemPath;
                        containerRelativePath.replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
                        containerRelativePath = containerRelativePath.substring(containerRelativePath.indexOf(WHITE_SOURCE_DOCKER + WINDOWS_PATH_SEPARATOR) + WHITE_SOURCE_DOCKER.length() + 1);
                        containerRelativePath = containerRelativePath.substring(containerRelativePath.indexOf(WINDOWS_PATH_SEPARATOR) + 1);
                        dependencyInfo.setSystemPath(containerRelativePath);
                    }
                }
                projectInfo.getDependencies().addAll(dependencyInfos);
            } catch (IOException e) {
                logger.error("Error exporting image {}: {}", dockerImage.getRepository(), e.getMessage());
                logger.debug("Error exporting image {}", dockerImage.getRepository(), e);
            } catch (ArchiverException e) {
                logger.error("Error extracting {}: {}", containerTarFile, e.getMessage());
                logger.debug("Error extracting tar archive", e);
            } finally {
                FileUtils.deleteQuietly(containerTarFile);
                FileUtils.deleteQuietly(containerTarExtractDir);
                FileUtils.deleteQuietly(containerTarArchiveExtractDir);
            }

        }
    }

    /**
     * Extract matching files from the tar archive.
     */
    private void extractTarArchive(File containerTarFile, File containerTarExtractDir) {
        TarArchiveInputStream tais = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(containerTarFile);
            tais = new TarArchiveInputStream(fis);
            ArchiveEntry entry = tais.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    String lowerCaseName = entryName.toLowerCase();
                    if (lowerCaseName.matches(FileExtensions.SOURCE_FILE_PATTERN) || lowerCaseName.matches(FileExtensions.BINARY_FILE_PATTERN) ||
                            lowerCaseName.matches(FileExtensions.ARCHIVE_FILE_PATTERN)) {
                        File file = new File(containerTarExtractDir, entryName);
                        File parent = file.getParentFile();
                        if (!parent.exists()) {
                            parent.mkdirs();
                        }
                        OutputStream out = new FileOutputStream(file);
                        IOUtils.copy(tais, out);
                        out.close();
                    }
                }
                entry = tais.getNextTarEntry();
            }
        } catch (FileNotFoundException e) {
            logger.warn("Error extracting files from {}: {}", containerTarFile.getPath(), e.getMessage());
        } catch (IOException e) {
            logger.warn("Error extracting files from {}: {}", containerTarFile.getPath(), e.getMessage());
        } finally {
            IOUtils.closeQuietly(tais);
            IOUtils.closeQuietly(fis);
        }
    }

}
