package org.whitesource.agent.dependency.resolver.docker;

import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.archive.ArchiveExtractor;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.RemoteDockersManager;
import org.whitesource.agent.hash.FileExtensions;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.LoggerFactory;
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

    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + TempFolders.UNIQUE_DOCKER_TEMP_FOLDER;
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
    private static Collection<AgentProjectInfo> projects = new LinkedList<>();

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

        // TODO: Read this before changing RemoteDockersManager location
        // Remote Docker pulling should be enable only if docker.scanImages==true && docker.pull.enable==true
        // Before calling resolveDockerImages() there is a check for isScanDockerImages()
        // If we create RemoteDockersManager outside of resolveDockerImages then we have to check isScanDockerImages()
        RemoteDockersManager remoteDockersManager = new RemoteDockersManager(config.getRemoteDocker());
        remoteDockersManager.pullRemoteDockerImages();

        String line = null;
        Collection<DockerImage> dockerImages = new LinkedList<>();
        Collection<DockerImage> dockerImagesToScan;
        Process process = null;
        try {
            // docker get list of images, use wait to get the whole list
            boolean isTarImages = config.isScanImagesTar();
            if (!isTarImages) {
                process = Runtime.getRuntime().exec(DOCKER_IMAGES);
                InputStream inputStream = process.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                logger.debug("Docker images list from BufferedReader");
                while ((line = br.readLine()) != null) {
                    logger.debug(line);
                    // read all docker images data, skip the first line
                    if (!line.startsWith(REPOSITORY)) {
                        String[] dockerImageString = line.split(SPACES_REGEX);
                        if (dockerImageString.length > 2) {
                            dockerImages.add(new DockerImage(dockerImageString[0], dockerImageString[1], dockerImageString[2]));
                        } else {
                            logger.info("Docker line content is ignored: {}", line);
                        }
                    }
                }
                process.waitFor();
                if (!dockerImages.isEmpty()) {
                    // filter docker images using includes & excludes parameter
                    dockerImagesToScan = filterDockerImagesToScan(dockerImages, config.getAgent().getDockerIncludes(), config.getAgent().getDockerExcludes());
                    if (!dockerImagesToScan.isEmpty()) {
                        saveDockerImages(dockerImagesToScan, projects);
                    }
                }
                br.close();
            } else {
                Collection<File> tarFiles = new HashSet<>();
                FilenameFilter filenameFilter = new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        if (name.endsWith(TAR_SUFFIX)) {
                            return true;
                        }
                        return false;
                    }
                };
                for(String dir: config.getDependencyDirs()) {
                    File tarDir = new File(dir);
                    if (tarDir.isDirectory()) {
                        for (File tarFile: tarDir.listFiles(filenameFilter)) {
                            tarFiles.add(tarFile);
                        }
                    }
                }
                Collection<File> tarImagesToScan = filterTarImagesToScan(tarFiles, config.getAgent().getDockerIncludes(), config.getAgent().getDockerExcludes());
                scanTarList(tarImagesToScan, projects);
            }
        } catch (IOException e) {
            logger.error("IO exception : {}", e.getMessage());
            logger.debug("IO exception : {}", e.getStackTrace());
        } catch (InterruptedException e) {
            logger.error("Interrupted exception : {}", e.getMessage());
            logger.debug("Interrupted exception : {}", e.getStackTrace());
        } catch (Exception e) {
            logger.error("Exception : {}", e.getMessage());
            logger.debug("Resolve Docker Images Exception : {}", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        remoteDockersManager.removePulledRemoteDockerImages();
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
            if (isMatchingPattern(dockerImageString, imageIncludesList)) {
                dockerImagesToScan.add(dockerImage);
            }
            // remove images from scan according to dockerExcludes pattern
            if (isMatchingPattern(dockerImageString, imageExcludesList)) {
                dockerImagesToScan.remove(dockerImage);
            }
        }
        return dockerImagesToScan;
    }

    private Collection<File> filterTarImagesToScan(Collection<File> dockerImages, String[] dockerImageIncludes, String[] dockerImageExcludes) {
        logger.info("Filtering docker images list by includes and excludes lists");
        Collection<File> dockerImagesToScan = new LinkedList<>();
        Collection<String> imageIncludesList = Arrays.asList(dockerImageIncludes);
        Collection<String> imageExcludesList = Arrays.asList(dockerImageExcludes);
        for (File dockerImage : dockerImages) {
            String dockerImageString = dockerImage.getName();
            // add images to scan according to dockerIncludes pattern
            if (isMatchingPattern(dockerImageString, imageIncludesList)) {
                dockerImagesToScan.add(dockerImage);
            }
            // remove images from scan according to dockerExcludes pattern
            if (isMatchingPattern(dockerImageString, imageExcludesList)) {
                dockerImagesToScan.remove(dockerImage);
            }
        }
        return dockerImagesToScan;
    }

    private boolean isMatchingPattern(String dockerImageString, Collection<String> imageIncludesList) {
        for (String imageInclude : imageIncludesList) {
            if (StringUtils.isNotBlank(imageInclude)) {
                Pattern p = Pattern.compile(imageInclude);
                Matcher m = p.matcher(dockerImageString);
                if (m.find()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Save docker images and scan files
     */
    private void saveDockerImages(Collection<DockerImage> dockerImages, Collection<AgentProjectInfo> projects) throws IOException {
        logger.info("Saving {} docker images", dockerImages.size());
        int counter = 1;
        int imagesCount = dockerImages.size();
        for (DockerImage dockerImage : dockerImages) {
            logger.info("Image {} of {} Images", counter, imagesCount);
            //saveDockerImage(dockerImage, projects);
            manageDockerImage(dockerImage, projects);
            counter++;
        }
    }

    private void scanTarList (Collection<File> tarFilesName, Collection<AgentProjectInfo> projects) {
        int i=0;
        for (File tarFile:tarFilesName) {
            String tar = tarFile.getAbsolutePath();
            i++;
            logger.info("file {} : {}", i, tar);
        }
        for (File tarFile:tarFilesName) {
            String tar = tarFile.getAbsolutePath();
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            String tarName = tar.substring(tar.lastIndexOf(BACK_SLASH)+1);
            String[] splitted = tarName.split(WHITESPACE);
            if (splitted.length == 3) {
                String id = splitted[0];
                String repository = splitted[1].replace(String.valueOf(SEMI_COLON), FORWARD_SLASH);
                String tag = splitted[2].replace(String.valueOf(SEMI_COLON), FORWARD_SLASH)
                        .replace(String.valueOf(OPEN_BRACKET), EMPTY_STRING)
                        .replace(String.valueOf(CLOSE_BRACKET) + TAR_SUFFIX, EMPTY_STRING);
                projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, id,
                        repository, tag), null));
                projects.add(projectInfo);
                File imageTarFile = new File(tar);
                File imageExtractionDir = new File(TEMP_FOLDER, imageTarFile.getName());
                imageExtractionDir.mkdirs();
                extractAndBuildImage(imageTarFile, imageExtractionDir, projectInfo, config.deleteTarImages());
                scanImage(imageExtractionDir, projectInfo);
                deleteDockerArchiveFiles(null, imageExtractionDir);
            } else {
                logger.info("file {} name is not in format 'Hash Name (Tag)'", tar);
            }
        }
    }

    private void manageDockerImage(DockerImage dockerImage, Collection<AgentProjectInfo> projects) throws IOException {
        logger.debug("Saving image {} {}", dockerImage.getRepository(), dockerImage.getTag());
        // create agent project info
        AgentProjectInfo projectInfo = new AgentProjectInfo();
        projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, dockerImage.getId(),
                dockerImage.getRepository(), dockerImage.getTag()), null));
        projects.add(projectInfo);

        File imageTarFile = new File(TEMP_FOLDER, dockerImage.getRepository() + TAR_SUFFIX);
        File imageExtractionDir = new File(TEMP_FOLDER, dockerImage.getRepository());
        imageExtractionDir.mkdirs();

        boolean saved = saveImage(dockerImage, imageTarFile);
        if (saved) {
            extractAndBuildImage(imageTarFile, imageExtractionDir, projectInfo, config.deleteTarImages());
            scanImage(imageExtractionDir, projectInfo);
        }

        deleteDockerArchiveFiles(imageTarFile, imageExtractionDir);
    }

    private boolean saveImage(DockerImage dockerImage, File imageTarFile) {
        Process process = null;
        try {
            //Save image as tar file
            process = Runtime.getRuntime().exec(DOCKER_SAVE_IMAGE_COMMAND + Constants.WHITESPACE + dockerImage.getId() +
                    Constants.WHITESPACE + O_PARAMETER + Constants.WHITESPACE + imageTarFile.getPath());
            process.waitFor();
            return true;
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            logger.debug("{}", e.getStackTrace());
        } catch (IOException e) {
            logger.error("Error exporting image {}: {}", dockerImage.getRepository(), e.getMessage());
            logger.debug("Error exporting image {}", dockerImage.getRepository(), e);
        } finally {
            process.destroy();
        }
        return false;
    }

    private void extractAndBuildImage (File imageTarFile, File imageExtractionDir, AgentProjectInfo projectInfo, Boolean fromTarList) {
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(config.getAgent().getArchiveIncludes(), config.getAgent().getArchiveExcludes(), config.getAgent().getIncludes());
        try {
            int megaByte = 1048576; // 1024*1024
            long tarSizeInBytes = imageTarFile.length();
            long tarSizeInMBs = tarSizeInBytes/megaByte;
            long freeDiskSpaceInBytes = imageTarFile.getFreeSpace();
            long freeDiskSpaceInMBs = imageTarFile.getFreeSpace()/megaByte;

            logger.info("Extracting file {} - Size {} Bytes ({} MBs)- Free Space {} Bytes ({} MBs)",
                    imageTarFile.getCanonicalPath(), tarSizeInBytes, tarSizeInMBs, freeDiskSpaceInBytes, freeDiskSpaceInMBs);
        } catch (Exception ex){
            logger.error("Could not get file size - {}", ex);
        }
        archiveExtractor.extractDockerImageLayers(imageTarFile, imageExtractionDir, fromTarList);
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
        RpmParser.findFolder(imageExtractionDir, YUM_DB, yumDbFoldersPath);
        File yumDbFolder = rpmParser.checkFolders(yumDbFoldersPath, RPM_YUM_DB_FOLDER_DEFAULT_PATH);
        int rpmPackages = parseProjectInfo(projectInfo, rpmParser, yumDbFolder);
        logger.info("Found {} Rpm Packages", rpmPackages);
    }

    private void scanImage (File imageExtractionDir, AgentProjectInfo projectInfo) {
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
    }

    /*
    private void saveDockerImage(DockerImage dockerImage, Collection<AgentProjectInfo> projects) throws IOException {
        logger.debug("Saving image {} {}", dockerImage.getRepository(), dockerImage.getTag());
        Process process = null;
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
            try {
                int megaByte = 1048576; // 1024*1024
                long tarSizeInBytes = imageTarFile.length();
                long tarSizeInMBs = tarSizeInBytes / megaByte;
                long freeDiskSpaceInBytes = imageTarFile.getFreeSpace();
                long freeDiskSpaceInMBs = imageTarFile.getFreeSpace() / megaByte;

                logger.info("Extracting file {} - Size {} Bytes ({} MBs)- Free Space {} Bytes ({} MBs)",
                        imageTarFile.getCanonicalPath(), tarSizeInBytes, tarSizeInMBs, freeDiskSpaceInBytes, freeDiskSpaceInMBs);
            } catch (Exception ex) {
                logger.error("Could not get file size - {}", ex);
            }
            archiveExtractor.extractDockerImageLayers(imageTarFile, imageExtractionDir, false);
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
            RpmParser.findFolder(imageExtractionDir, YUM_DB, yumDbFoldersPath);
            File yumDbFolder = rpmParser.checkFolders(yumDbFoldersPath, RPM_YUM_DB_FOLDER_DEFAULT_PATH);
            int rpmPackages = parseProjectInfo(projectInfo, rpmParser, yumDbFolder);
            logger.info("Found {} Rpm Packages", rpmPackages);

            // scan files
            String extractPath = imageExtractionDir.getPath();
            Set<String> setDirs = new HashSet<>();
            setDirs.add(extractPath);
            Map<String, Set<String>> appPathsToDependencyDirs = new HashMap<>();
            appPathsToDependencyDirs.put(FSAConfiguration.DEFAULT_KEY, setDirs);
            ProjectConfiguration projectConfiguration = new ProjectConfiguration(config.getAgent(), Arrays.asList(extractPath), appPathsToDependencyDirs, false);
            Collection<AgentProjectInfo> agentProjectInfos = new FileSystemScanner(config.getResolver(), config.getAgent(), false).createProjects(projectConfiguration).keySet();
            List<DependencyInfo> dependencyInfos = agentProjectInfos.stream().flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList());

            projectInfo.getDependencies().addAll(dependencyInfos);
        } catch (IOException e) {
            logger.error("Error exporting image {}: {}", dockerImage.getRepository(), e.getMessage());
            logger.debug("Error exporting image {}", dockerImage.getRepository(), e);
        } catch (ArchiverException e) {
            logger.error("Error extracting {}: {}", imageTarFile, e.getMessage());
            logger.debug("Error extracting tar archive", e);
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            logger.debug("{}", e.getStackTrace());
        } finally {
            process.destroy();
            deleteDockerArchiveFiles(imageTarFile, imageExtractionDir);
        }
    }*/

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
        archiveExtractor.unXz(file, file.getParent() + File.separator + PACKAGE_LOG_TXT);
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
