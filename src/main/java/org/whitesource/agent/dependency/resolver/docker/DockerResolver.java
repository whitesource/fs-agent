package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
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
    private static final String DOCKER_SAVE_IMAGE_COMMAND = "docker save";
    private static final String O_PARAMETER = "-o";
    private static final String REPOSITORY = "REPOSITORY";
    private static final String SPACES_REGEX = "\\s+";
    private static final String SPACE = " ";
    private static final String DOCKER_NAME_FORMAT_STRING = "{0} {1} ({2})";
    private static final MessageFormat DOCKER_NAME_FORMAT = new MessageFormat(DOCKER_NAME_FORMAT_STRING);
    private static final String DOCKER_IMAGES = "docker images";
    private static final boolean PARTIAL_SHA1_MATCH = false;
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
    public Collection<AgentProjectInfo> resolveDockerImages() {
        logger.info("Resolving docker images");
        Collection<AgentProjectInfo> projects = new LinkedList<>();
        String line = null;
        Collection<DockerImage> dockerImages = new LinkedList<>();
        Collection<DockerImage> dockerImagesToScan;
        Process process = null;
        try {
            // docker get list of images, use wait to get the whole list
            process = Runtime.getRuntime().exec(DOCKER_IMAGES);
            process.waitFor();
            InputStream inputStream = process.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                // read all docker images data, skip the first line
                if (!line.startsWith(REPOSITORY)) {
                    String[] dockerImageString = line.split(SPACES_REGEX);

                    dockerImages.add(new DockerImage(dockerImageString[0], dockerImageString[1], dockerImageString[2]));

                }
            }
            if (!dockerImages.isEmpty()) {
                // filter docker images using includes & excludes parameter
                dockerImagesToScan = filterDockerImagesToScan(dockerImages, config.getAgent().getDockerIncludes(), config.getAgent().getDockerExcludes());
                if (!dockerImagesToScan.isEmpty()) {
                    saveDockerImages(dockerImagesToScan, projects);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return projects;
    }

    /* --- Private methods --- */

    /**
     * Filter the images using includes and excludes lists
     */
    private Collection<DockerImage> filterDockerImagesToScan(Collection<DockerImage> dockerImages, String[] dockerImageIncludes, String[] dockerImageExcludes) {
        logger.info("Filtering docker image list by includes and excludes lists");
        Collection<DockerImage> dockerImagesToScan = new LinkedList<>();
        Collection<String> imageIncludesList = Arrays.asList(dockerImageIncludes);
        Collection<String> imageExcludesList = Arrays.asList(dockerImageExcludes);
        for (DockerImage dockerImage : dockerImages) {
            String dockerImageString = dockerImage.getRepository() + SPACE + dockerImage.getTag() + SPACE + dockerImage.getId();
            // add images to scan according to dockerIncludes pattern
            for (String imageInclude : imageIncludesList) {
                if (StringUtils.isNotBlank(imageInclude)) {
                    Pattern p = Pattern.compile(imageInclude);
                    Matcher m = p.matcher(dockerImageString);
                    if (m.find()) {
                        dockerImagesToScan.add(dockerImage);
                    }
                }
            }
            // remove images from scan according to dockerExcludes pattern
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
        logger.info("Saving {} docker images", dockerImages.size());
        for (DockerImage dockerImage : dockerImages) {
            logger.debug("Saving image {} {}", dockerImage.getRepository(), dockerImage.getTag());
            // create agent project info
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, dockerImage.getId(),
                    dockerImage.getRepository(), dockerImage.getTag()), null));
            projects.add(projectInfo);

            File containerTarFile = new File(TEMP_FOLDER, dockerImage.getRepository() + TAR_SUFFIX);
            File containerTarExtractDir = new File(TEMP_FOLDER, dockerImage.getRepository());
            containerTarExtractDir.mkdirs();
            File containerTarArchiveExtractDir = new File(ARCHIVE_EXTRACTOR_TEMP_FOLDER);
            containerTarArchiveExtractDir.mkdirs();
            try {
                //Save image as tar file
                process = Runtime.getRuntime().exec(DOCKER_SAVE_IMAGE_COMMAND + SPACE + dockerImage.getId() +
                        SPACE + O_PARAMETER + SPACE + containerTarFile.getPath());
                process.waitFor();

                // extract tar archive
                List<String> archiveDirs = new LinkedList<>();
                archiveDirs.add(containerTarArchiveExtractDir.getPath());
                ArchiveExtractor archiveExtractor = new ArchiveExtractor(config.getAgent().getArchiveIncludes(), config.getAgent().getArchiveExcludes(), config.getAgent().getIncludes());
                archiveExtractor.extractArchives(containerTarFile.getPath(), config.getAgent().getArchiveExtractionDepth(), archiveDirs);

                AbstractParser parser = new DebianParser();
                File file = parser.findFile(containerTarArchiveExtractDir, "available");
                if (file != null) {
                    Collection<DependencyInfo> debianPackages = parser.parse(file);
                    if (!debianPackages.isEmpty()) {
                        projectInfo.getDependencies().addAll(debianPackages);
                        logger.info("Found {} Debian Packages", debianPackages.size());
                    }
                }


                // scan files
                String extractPath = containerTarArchiveExtractDir.getPath();
                List<DependencyInfo> dependencyInfos = new FileSystemScanner(config.getResolver(), config.getAgent()).createProjects(
                        Arrays.asList(extractPath), false, config.getAgent().getIncludes(), config.getAgent().getExcludes(),
                        config.getAgent().getGlobCaseSensitive(), config.getAgent().getArchiveExtractionDepth(), FileExtensions.ARCHIVE_INCLUDES,
                        FileExtensions.ARCHIVE_EXCLUDES, false, config.getAgent().isFollowSymlinks(),
                        new ArrayList<>(), PARTIAL_SHA1_MATCH);

                // modify file paths relative to the container
                for (DependencyInfo dependencyInfo : dependencyInfos) {
                    String systemPath = dependencyInfo.getSystemPath();
                    if (StringUtils.isNotBlank(systemPath)) {
                        String containerRelativePath = systemPath;
                        containerRelativePath.replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
                        containerRelativePath = containerRelativePath.substring(containerRelativePath.indexOf(WHITE_SOURCE_DOCKER +
                                WINDOWS_PATH_SEPARATOR) + WHITE_SOURCE_DOCKER.length() + 1);
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                process.destroy();
                deleteDockerArchiveFiles(containerTarFile, containerTarExtractDir, containerTarArchiveExtractDir);
            }

        }
    }

    private void deleteDockerArchiveFiles(File containerTarFile, File containerTarExtractDir, File containerTarArchiveExtractDir) {
        FileUtils.deleteQuietly(containerTarFile);
        FileUtils.deleteQuietly(containerTarExtractDir);
        FileUtils.deleteQuietly(containerTarArchiveExtractDir);
    }

}
