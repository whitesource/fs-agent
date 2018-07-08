package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.hash.FileExtensions;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.fs.FSAConfiguration;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.whitesource.agent.Constants.*;
import static org.whitesource.agent.archive.ArchiveExtractor.TAR_SUFFIX;

/**
 * @author chen.luigi
 */
public class DockerResolver {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(DockerResolver.class);

    private static final String WHITE_SOURCE_DOCKER = "WhiteSource-Docker";
    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + WHITE_SOURCE_DOCKER;
    private static final String DOCKER_SAVE_IMAGE_COMMAND = "docker save";
    private static final String O_PARAMETER = "-o";
    private static final String REPOSITORY = "REPOSITORY";
    private static final String SPACES_REGEX = "\\s+";
    private static final String DOCKER_NAME_FORMAT_STRING = "{0} {1} ({2})";
    private static final MessageFormat DOCKER_NAME_FORMAT = new MessageFormat(DOCKER_NAME_FORMAT_STRING);
    private static final String DOCKER_IMAGES = "docker images";
    private static final String DEBIAN_PATTERN = "**/*eipp.log.xz";
    private static final String ARCH_LINUX_PATTERN = "**/*desc";
    private static final String ALPINE_PATTERN = "**/*installed";
    private static final String DEBIAN_PATTERN_AVAILABLE = "**/*available";
    private static final String RPM_PATTERN = "**" + VAR + File.separator + LIB + File.separator + YUM + File.separator + YUM_DB + "/**";
    private static final String[] scanIncludes = {DEBIAN_PATTERN, ARCH_LINUX_PATTERN, ALPINE_PATTERN, RPM_PATTERN, DEBIAN_PATTERN_AVAILABLE};
    private static final String[] scanExcludes = {};
    private static final String ARCH_LINUX_DESC_FOLDERS = VAR + File.separator + LIB + File.separator + "pacman" + File.separator + "local";
    private static final String RPM_YUM_DB_FOLDER_DEFAULT_PATH = VAR + File.separator + LIB + File.separator + YUM + File.separator + YUM_DB;
    private static final String DEBIAN_LIST_PACKAGES_FILE = File.separator + "eipp.log.xz";
    private static final String ALPINE_LIST_PACKAGES_FILE = File.separator + "installed";
    private static final String DEBIAN_LIST_PACKAGES_FILE_AVAILABLE = File.separator + "available";
    private static final String PACKAGE_LOG_TXT = "packageLog.txt";
    private static final boolean PARTIAL_SHA1_MATCH = false;

    /* --- Members --- */

    private FSAConfiguration config;

    /* --- Constructor --- */

    public DockerResolver(FSAConfiguration config) {
        this.config = config;
    }

    /* --- Public methods --- */

    /**
     * Create project for each image
     *
     * @return list of projects for all docker images
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
            logger.error("IO exception : ", e.getMessage());
            logger.debug("IO exception : ", e.getStackTrace());
        } catch (InterruptedException e) {
            logger.error("Interrupted exception : ", e.getMessage());
            logger.debug("Interrupted exception : ", e.getStackTrace());
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
        logger.info("Filtering docker images list by includes and excludes lists");
        Collection<DockerImage> dockerImagesToScan = new LinkedList<>();
        Collection<String> imageIncludesList = Arrays.asList(dockerImageIncludes);
        Collection<String> imageExcludesList = Arrays.asList(dockerImageExcludes);
        for (DockerImage dockerImage : dockerImages) {
            String dockerImageString = dockerImage.getRepository() + Constants.WHITESPACE + dockerImage.getTag() + Constants.WHITESPACE + dockerImage.getId();
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
    private void saveDockerImages(Collection<DockerImage> dockerImages, Collection<AgentProjectInfo> projects) throws IOException {
        Process process = null;
        logger.info("Saving {} docker images", dockerImages.size());
        //String osName = System.getProperty(Constants.OS_NAME);
        for (DockerImage dockerImage : dockerImages) {
            logger.debug("Saving image {} {}", dockerImage.getRepository(), dockerImage.getTag());
            // create agent project info
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, dockerImage.getId(),
                    dockerImage.getRepository(), dockerImage.getTag()), null));
            projects.add(projectInfo);

            File imageTarFile = new File(TEMP_FOLDER, dockerImage.getRepository() + TAR_SUFFIX);
            File imageExtractionDir = new File(TEMP_FOLDER, dockerImage.getRepository());
            imageExtractionDir.mkdirs();
            try {
                //Save image as tar file
                process = Runtime.getRuntime().exec(DOCKER_SAVE_IMAGE_COMMAND + Constants.WHITESPACE + dockerImage.getId() +
                        Constants.WHITESPACE + O_PARAMETER + Constants.WHITESPACE + imageTarFile.getPath());
                process.waitFor();

                // extract tar archive
                ArchiveExtractor archiveExtractor = new ArchiveExtractor(config.getAgent().getArchiveIncludes(), config.getAgent().getArchiveExcludes(), config.getAgent().getIncludes());
                archiveExtractor.extractDockerImageLayers(imageTarFile, imageExtractionDir);
                FilesScanner filesScanner = new FilesScanner();
                String[] fileNames = filesScanner.getDirectoryContent(imageExtractionDir.getParent(), scanIncludes, scanExcludes, true, false);

                // build the full path correctly
                for (int i = 0; i < fileNames.length; i++) {
                    fileNames[i] = imageExtractionDir.getParent() + File.separator + fileNames[i];
                }

                // check for dependencies for each docker operating system (Debian,Arch-Linux,Alpine,Rpm)
                AbstractParser parser = new DebianParser();
                File file = parser.findFile(fileNames, DEBIAN_LIST_PACKAGES_FILE);

                // extract .xz file to read the package log file
                if (file != null) {
                    file = getPackagesLogFile(file, archiveExtractor);
                }
                parseProjectInfo(projectInfo, parser, file);
                file = parser.findFile(fileNames, DEBIAN_LIST_PACKAGES_FILE_AVAILABLE);
                if (file != null) {
                    parseProjectInfo(projectInfo, parser, file);
                }

                // try to find duplicates and clear them
                Collection<DependencyInfo> debianDependencyInfos = mergeDependencyInfos(projectInfo);
                if (debianDependencyInfos != null && !debianDependencyInfos.isEmpty()) {
                    projectInfo.getDependencies().clear();
                    projectInfo.getDependencies().addAll(debianDependencyInfos);
                }
                logger.info("Found {} Debian Packages", debianDependencyInfos.size());

                parser = new ArchLinuxParser();
                file = parser.findFile(fileNames, ARCH_LINUX_DESC_FOLDERS);
                int archLinuxPackages = parseProjectInfo(projectInfo, parser, file);
                logger.info("Found {} Arch linux Packages", archLinuxPackages);

                parser = new AlpineParser();
                file = parser.findFile(fileNames, ALPINE_LIST_PACKAGES_FILE);
                int alpinePackages = parseProjectInfo(projectInfo, parser, file);
                logger.info("Found {} Alpine Packages", alpinePackages);

                RpmParser rpmParser = new RpmParser();
                Collection<String> yumDbFoldersPath = new LinkedList<>();
                rpmParser.findFolder(imageExtractionDir, YUM_DB, yumDbFoldersPath);
                File yumDbFolder = rpmParser.checkFolders(yumDbFoldersPath, RPM_YUM_DB_FOLDER_DEFAULT_PATH);
                int rpmPackages = parseProjectInfo(projectInfo, rpmParser, yumDbFolder);
                logger.info("Found {} Rpm Packages", rpmPackages);

                // scan files
                String extractPath = imageExtractionDir.getPath();
                Set<String> setDirs = new HashSet<>();
                setDirs.add(extractPath);
                Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
                appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);
                List<DependencyInfo> dependencyInfos = new FileSystemScanner(config.getResolver(), config.getAgent(), false).createProjects(
                        Arrays.asList(extractPath), appPathsToDependencyDirs, false, config.getAgent().getIncludes(), config.getAgent().getExcludes(),
                        config.getAgent().getGlobCaseSensitive(), config.getAgent().getArchiveExtractionDepth(), FileExtensions.ARCHIVE_INCLUDES,
                        FileExtensions.ARCHIVE_EXCLUDES, false, config.getAgent().isFollowSymlinks(), config.getAgent().getExcludedCopyrights(), PARTIAL_SHA1_MATCH, config.getAgent().getPythonRequirementsFileIncludes());

                projectInfo.getDependencies().addAll(dependencyInfos);
            } catch (IOException e) {
                logger.error("Error exporting image {}: {}", dockerImage.getRepository(), e.getMessage());
                logger.debug("Error exporting image {}", dockerImage.getRepository(), e);
            } catch (ArchiverException e) {
                logger.error("Error extracting {}: {}", imageTarFile, e.getMessage());
                logger.debug("Error extracting tar archive", e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                process.destroy();
                deleteDockerArchiveFiles(imageTarFile, imageExtractionDir);
            }
        }
    }

    private Collection<DependencyInfo> mergeDependencyInfos(AgentProjectInfo projectInfo) {
        Map<String, DependencyInfo> infoMap = new HashedMap();
        Collection<DependencyInfo> dependencyInfos = new LinkedList<>();
        if (projectInfo != null) {
            Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
            for (DependencyInfo dependencyInfo : dependencies) {
                infoMap.putIfAbsent(dependencyInfo.getArtifactId(), dependencyInfo);
            }
        }
        for (Map.Entry<String, DependencyInfo> entry : infoMap.entrySet()) {
            if (entry.getValue() != null) {
                dependencyInfos.add(entry.getValue());
            }
        }
        return dependencyInfos;
    }

    private File getPackagesLogFile(File file, ArchiveExtractor archiveExtractor) {
        archiveExtractor.unXz(file, File.separator + PACKAGE_LOG_TXT);
        return new File(file.getParent() + File.separator + PACKAGE_LOG_TXT);
    }

    private int parseProjectInfo(AgentProjectInfo projectInfo, AbstractParser parser, File file) {
        if (file != null) {
            Collection<DependencyInfo> packageManagerPackages = parser.parse(file);
            if (!packageManagerPackages.isEmpty()) {
                projectInfo.getDependencies().addAll(packageManagerPackages);
            }
            return packageManagerPackages.size();
        }
        return 0;
    }

    private void deleteDockerArchiveFiles(File imageTarFile, File imageTarExtractDir) {
        FileUtils.deleteQuietly(imageTarFile);
        FileUtils.deleteQuietly(imageTarExtractDir);
    }
}
