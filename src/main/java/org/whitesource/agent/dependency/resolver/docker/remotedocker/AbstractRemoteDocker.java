package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.RemoteDockerConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class AbstractRemoteDocker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteDocker.class);
    private static String DOCKER_CLI_VERSION = "docker --version";
    static String DOCKER_CLI_LOGIN  = "docker login";
    static String DOCKER_CLI_REMOVE_IMAGE = "docker rmi ";
    private static String DOCKER_CLI_PULL = "docker pull ";
    static String LINUX_PREFIX_SUDO = "sudo ";
    private static final String WS_SCANNED_TAG = "WS.Scanned";

    // This is a set of the pulled images only - Users may require to pull existing images - but they are not saved
    // in this set because we will remove the images that we pulled here (we don't want to remove the existing images
    // of the users)
    private Set<AbstractRemoteDockerImage> imagesPulled;

    private Set<AbstractRemoteDockerImage> imagesFound;

    protected RemoteDockerConfiguration config;

    private int pulledImagesCount;
    private int existingImagesCount;
    private int maxScanImagesCount;
    private int scannedImagesCount;

    /* --- Constructors --- */

    public AbstractRemoteDocker(RemoteDockerConfiguration config) {
        this.config = config;
        pulledImagesCount = 0;
        existingImagesCount = 0;
        maxScanImagesCount = config.getMaxScanImages();
        scannedImagesCount = 0;
    }

    /* --- Public methods --- */

    public Set<AbstractRemoteDockerImage> pullRemoteDockerImages() {
        if (isAllSoftwareRequiredInstalled()) {
             if (loginToRemoteRegistry()) {
                 imagesFound = listImagesOnRemoteRegistry();
                 if (imagesFound != null && !imagesFound.isEmpty()) {
                     imagesPulled = pullImagesFromRemoteRegistry();
                 }
                 logger.info("{} New images were pulled", pulledImagesCount);
                 logger.info("{} Images are up to date (not pulled)", existingImagesCount);
             }
        }
        return imagesPulled;
    }

    public void removePulledRemoteDockerImages() {
        if (imagesPulled != null) {
            for(AbstractRemoteDockerImage image: imagesPulled) {
                String command = DOCKER_CLI_REMOVE_IMAGE;
                // Use force delete
                if (config.isForceDelete()) {
                    command += "-f ";
                }
                executeCommand(command + image.getImageSha256());
            }
        }
    }

    protected abstract boolean loginToRemoteRegistry();

    /*
        TODO: this function should return a collection of manifest image object
        TODO: DockerImage object does not include all the required data (like date, tags list, etc...)
     */
    protected abstract Set<AbstractRemoteDockerImage> listImagesOnRemoteRegistry();

    protected abstract boolean isRequiredRegistryManagerInstalled();

    protected abstract String getImageFullURL(AbstractRemoteDockerImage image);

    /*
    private boolean isNamePullRequired();
    private boolean isTagPullRequired();
    private boolean isDigestPullRequired();
    private boolean isDatePullRequired();
*/

    private Set<AbstractRemoteDockerImage> pullImagesFromRemoteRegistry() {
        Set<AbstractRemoteDockerImage> pulledImagesList = new HashSet<>();
        int maxPullImages = config.getMaxPullImages();
        int pullImagesCounter = 0;
        if (maxPullImages < 1) {
            logger.info("No images will be pull - Configuration 'docker.pull.maxImages' is equal to {} ", maxPullImages);
            return pulledImagesList;
        }
        for (AbstractRemoteDockerImage image : imagesFound) {
            // Check if image meets the required name/tag/digest
            if (isImagePullRequired(image)) {
                String imageURL = getImageFullURL(image);
                if (pullImageWithFullUrl(imageURL)) {
                    pulledImagesList.add(image);
                    pullImagesCounter++;
                }
            }
            if (pullImagesCounter >= maxPullImages) {
                logger.info("Reached maximum images pull count of {} - will not pull any more images", pullImagesCounter);
                break;
            }
        }
        return pulledImagesList;
    }

    private boolean isAllSoftwareRequiredInstalled() {
        return isDockerInstalled() && isRequiredRegistryManagerInstalled();
    }

    private boolean isAllNamesRequired() {
        List<String> namesList = config.getImageNames();
        boolean allNames = false;
        // If images list is not configured - we assume that we want to scan ALL images
        if (namesList == null || namesList.isEmpty() || namesList.contains(Constants.GLOB_PATTERN)) {
            allNames = true;
        }
        return allNames;
    }

    private boolean isAllTagsRequired() {
        List<String> tagsList = config.getImageTags();
        boolean allTags = false;
        // If tag list is not configured - we assume that we want to scan ALL tags
        if (tagsList == null || tagsList.isEmpty() || tagsList.contains(Constants.GLOB_PATTERN)) {
            allTags = true;
        }
        return allTags;
    }

    private boolean isAllDigestsRequired() {
        List<String> digestList = config.getImageDigests();
        boolean allDigests = false;
        // If digest list is not configured - we assume that we want to scan ALL tags
        if (digestList == null || digestList.isEmpty() || digestList.contains(Constants.GLOB_PATTERN)) {
            allDigests = true;
        }
        return allDigests;
    }

    private boolean isAllImagesRequired() {
        // forcePulling = Pull images that are already scanned
        boolean forcePulling = config.isForcePull();
        // We want to pull everything - so every image is required
        if (isAllNamesRequired() && isAllTagsRequired() && isAllDigestsRequired() && forcePulling) {
            return true;
        }
        return false;
    }

    private boolean isImageDataValid(AbstractRemoteDockerImage image) {
        List<String> imageTags = image.getImageTags();
        String imageDigest = image.getImageDigest();
        String imageName = image.getRepositoryName();

        if (imageTags == null && StringUtils.isBlank(imageDigest) && StringUtils.isBlank(imageName)) {
            // This tag/sha256/name does not met any of the requirements
            logger.debug("Image values are empty or null");
            return false;
        }
        return true;
    }

    // TODO: this function should receive manifest image object and check for all values (like date, tags list, etc.)
    private boolean isImagePullRequired(AbstractRemoteDockerImage image) {

        if (image == null) {
            logger.debug("Docker Image is null");
            return false;
        }

        /*
        // All images are required
        if (isAllImagesRequired()) {
            return true;
        }
        */

        boolean isAllNamesRequired = isAllNamesRequired();
        boolean isAllTagsRequired  = isAllTagsRequired();
        boolean isAllDigestsRequired = isAllDigestsRequired();

        // All images are required ?
        boolean forcePulling = config.isForcePull();
        // We want to pull everything - so every image is required
        if (isAllNamesRequired && isAllTagsRequired && isAllDigestsRequired && forcePulling) {
            return true;
        }

        // Otherwise we check if the image data is full for specific tag(s)/sha256
        if (!isImageDataValid(image)) {
            return false;
        }

        // Data from the remote image
        List<String> imageTags = image.getImageTags();
        String imageDigest = image.getImageDigest();
        String imageName = image.getRepositoryName();

        if (imageTags == null || imageTags.isEmpty()) {
            logger.info("Image {} with Digest {} - does not have any tags and will be ignored", imageName, imageDigest);
            return false;
        }

        // Data from the config file
        List<String> configImageNames = config.getImageNames();
        List<String> configImageTags = config.getImageTags();
        List<String> configImageDigests = config.getImageDigests();

        boolean isNameMet;
        // Empty configuration = similar to using all names
        if (configImageNames == null) {
            isNameMet = true;
            logger.debug("Configuration - docker.pull.images was not found - using .*.* as default");
        }
        // 'Name' may have a regular expression match
        else {
            isNameMet = isAllNamesRequired || configImageNames.contains(imageName) || isMatchStringInList(imageName, configImageNames);
        }

        // 'Tag' may have a regular expression match
        // 'Tag' should consider pulling/ignoring images with the WS.Scanned tag - according to 'docker.pull.force' flag
        boolean isTagMet = false;
        if (configImageTags == null) {
            isTagMet = true;
            logger.debug("Configuration - docker.pull.tags was not found - using .*.* as default");
        }else {
            for(String currentTag : imageTags) {
                isTagMet = isTagMet || configImageTags.contains(currentTag) || isMatchStringInList(currentTag, configImageTags);
            }
        }

        // 'Digest' cannot have a regular expression match
        boolean isDigestMet = isAllDigestsRequired || configImageDigests.contains(imageDigest);

        // Do not pull images that are already tagged with WS.Scanned tag
        if (!forcePulling) {
            if (imageTags.contains(WS_SCANNED_TAG)) {
                logger.debug("Image {} - was scanned already", imageName);
                isTagMet = false;
            }
        }

        // Is this tag/sha256/name in the required tags/sha256/name list?
        boolean result = isNameMet && isTagMet && isDigestMet ;
        if (!result) {
            logger.debug("Image does not met the requirements: {}", image);
            logger.debug("Name met - {} , Tag met - {} , Digest met - {}", isNameMet, isTagMet, isDigestMet);
        }
        return result;
    }

    private boolean pullImageWithFullUrl(String imageURL) {
        boolean result = false;
        if (!StringUtils.isBlank(imageURL)) {
            logger.info("Trying to pull image : {}", imageURL);
            String command = DOCKER_CLI_PULL;
            command += imageURL;
            try {
                // TODO: check if can use CommandLineProcess
                Process process = Runtime.getRuntime().exec(command);
                StringBuilder resultText = new StringBuilder();
                try (final BufferedReader reader
                             = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        System.out.println(line);
                        resultText.append(line);
                    }
                }
                int resultValue = process.waitFor();
                result = resultValue == 0;
                if (result) {
                    int index = resultText.indexOf("Status:");
                    if (index > 0) {
                        String status = resultText.substring(index);
                        logger.info("{}", status);
                        if (status.contains("Image is up to date for")) {
                            existingImagesCount++;
                            result = false; // The image was not pulled
                        } else if (status.contains("Downloaded newer image for")) {
                            pulledImagesCount++;
                        }
                    }
                } else {
                    logger.info("Image was not pulled!");
                    logger.info("{}", resultText);
                }
            } catch (InterruptedException e) {
                logger.info("Execution of {} failed: - {}", command, e.getMessage());
                Thread.currentThread().interrupt();
            } catch (IOException  e) {
                logger.info("Execution of {} failed: - {}", command, e.getMessage());
            }
        }
        return result;
    }

    // This function cannot be run with multiple threads because each time it is run it overrides the values
    // of resultVal and inputStream, so if other thread (for example) is reading the stream, it might get mixed data
    // from old command's stream and the new command's stream
    Pair<Integer, InputStream> executeCommand(String command) {
        int resultVal = 1;
        InputStream inputStream = null;
        try {
            logger.debug("Executing command: {}", command);
            Process process =  Runtime.getRuntime().exec(command);

            resultVal = process.waitFor();
            inputStream = process.getInputStream();

        } catch (InterruptedException e) {
            logger.info("Execution of {} failed: code - {} ; message - {}", command, resultVal, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (IOException  e) {
            logger.info("Execution of {} failed: code - {} ; message - {}", command, resultVal, e.getMessage());
        }
        if (inputStream == null) {
           // Create an empty InputStream instead of returning a null
           inputStream = new InputStream() {
               @Override
               public int read() throws IOException {
                   return -1;
               }
           } ;
        }
        return new Pair<>(resultVal, inputStream);
    }

    boolean isCommandSuccessful(String command) {
        Pair<Integer, InputStream> result = executeCommand(command);
        Integer val = result.getKey();
        return val == 0;
    }

    private boolean isDockerInstalled() {
        String command = DOCKER_CLI_VERSION;
        boolean installed = isCommandSuccessful(command);
        if (!installed) {
            logger.error("Docker is not installed or its path is not configured correctly");
        }
        return installed;
    }

    private boolean isMatchStringInList(String toMatch, List<String> stringsList) {
        if (toMatch == null || stringsList == null || stringsList.isEmpty()) {
            return false;
        }
        for (String currentString : stringsList) {
            if (toMatch.matches(currentString)) {
                return true;
            }
        }
        return false;
    }
/*
    private boolean isMatchStringInListWithIgnore(String toMatch, List<String> stringsList, List<String> ignoreList) {
        if (toMatch == null || stringsList == null || stringsList.isEmpty()) {
            return false;
        }
        if (ignoreList == null || ignoreList.isEmpty()) {
            return isMatchStringInList(toMatch, stringsList);
        }
        else {
            for (String currentString : stringsList) {
                if (!ignoreList.contains(currentString)) {
                    if (toMatch.matches(currentString)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
*/
    public void incrementScannedImagesCount(int amount) {
        scannedImagesCount += amount;
    }

    /*
    private String getImageSizeInMB(String imageUrl) {
        String command = "docker image inspect " + imageUrl + " --format='{{.Size}}'";
        Pair<Integer, String> pullResult = executeCommandWithOutput(command);
        boolean cmdResult = pullResult.getKey() == 0;
        String result = "Unknown";
        if (cmdResult) {
            String cmdVal = pullResult.getValue().replaceAll("'","");
            try {
                long sizeInBytes = Long.parseLong(cmdVal);
                long sizeInMegaBytes = sizeInBytes / 1024 / 1024;
                result = String.valueOf(sizeInMegaBytes);
            } catch (Exception ex) {
                logger.debug("Was not able to parse image size {} ", imageUrl);
                logger.debug("Error - {}", ex.getMessage());
            }
        }
        return result;
    }

    protected boolean isEnoughSpaceForImage() {
        return true;
    }
    */

}
