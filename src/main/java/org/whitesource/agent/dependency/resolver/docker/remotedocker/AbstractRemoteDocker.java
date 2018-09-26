package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.docker.DockerImage;
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

    // This is a list of the pulled images only - Users may require to pull existing images - but they are not saved
    // in this list because we will remove the images that we pulled here (we don't want to remove the existing images
    // of the users)
    private Collection<DockerImage> imagesPulled;

    private Collection<DockerImage> imagesFound;
    private static String DOCKER_CLI_VERSION = "docker --version";
    protected static String DOCKER_CLI_LOGIN  = "docker login";
    private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteDocker.class);

    protected RemoteDockerConfiguration config;
    private int pulledImagesCount;
    private int existingImagesCount;

    /* --- Constructors --- */

    public AbstractRemoteDocker(RemoteDockerConfiguration config) {
        this.config = config;
        pulledImagesCount = 0;
        existingImagesCount = 0;
    }

    /* --- Public methods --- */

    public void pullRemoteDockerImages() {
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
    }

    public void removePulledRemoteDockerImages() {
        if (imagesPulled != null) {
            for(DockerImage image: imagesPulled) {
                String command = "docker rmi ";
                // Use force delete
                if (config.isForceDelete()) {
                    command += "-f ";
                }
                executeCommand(command + image.getId());
            }
        }
    }

    protected abstract boolean loginToRemoteRegistry();

    protected abstract Collection<DockerImage> listImagesOnRemoteRegistry();

    protected abstract boolean isRequiredRegistryManagerInstalled();

    protected abstract String getImageFullURL(DockerImage image);

    private Collection<DockerImage> pullImagesFromRemoteRegistry() {
        if (imagesFound != null) {
            Collection<DockerImage> pulledImagesList = new ArrayList<>();
            for (DockerImage image : imagesFound) {
                if (isImageRequired(image)) {
                    String imageURL = getImageFullURL(image);
                    if (pullImageWithFullUrl(imageURL)) {
                        pulledImagesList.add(image);
                    }
                }
            }
            return pulledImagesList;
        }
        return Collections.emptyList();
    }

    private boolean isAllSoftwareRequiredInstalled() {
        return isDockerInstalled() && isRequiredRegistryManagerInstalled();
    }

    private boolean isImageRequired(DockerImage image) {
        if (config != null && image != null) {

            List<String> namesList = config.getImageNames();
            List<String> tagsList = config.getImageTags();
            List<String> digestList = config.getImageDigests();

            boolean allNames = false;
            // If images list is not configured - we assume that we want to scan ALL images
            if (namesList == null || namesList.isEmpty() || namesList.contains(".*.*")) {
                allNames = true;
            }

            boolean allTags = false;
            // If tag list is not configured - we assume that we want to scan ALL tags
            if (tagsList == null || tagsList.isEmpty() || tagsList.contains(".*.*")) {
                allTags = true;
            }

            boolean allDigests = false;
            // If digest list is not configured - we assume that we want to scan ALL tags
            if (digestList == null || digestList.isEmpty() || digestList.contains(".*.*")) {
                allDigests = true;
            }

            // We want to pull everything - so every image is required
            if (allNames && allTags && allDigests) {
                return true;
            }

            // Otherwise we look for specific tag(s)/sha256
            String imageTag = image.getTag();
            String imageDigest = image.getId();
            String imageName = image.getRepository();

            if ( (imageTag == null || imageTag.isEmpty()) &&
                 (imageDigest == null || imageDigest.isEmpty()) &&
                 (imageName == null || imageName.isEmpty())) {
                // This tag/sha256/name does not met any of the requirements
                return false;
            }

            // Name and Tag may have a regular expression match
            boolean isNameMet   = allNames || namesList.contains(imageName) || isMatchStringInList(imageName, namesList);
            boolean isTagMet    = allTags  || tagsList.contains(imageTag)   || isMatchStringInList(imageTag, tagsList);
            // Digest cannot have a regular expression match
            boolean isDigestMet = allDigests || digestList.contains(imageDigest);

            // Is this tag/sha256/name in the required tags/sha256/name list?
            return isNameMet && isTagMet && isDigestMet ;
        }
        return false;
    }

    private boolean pullImageWithFullUrl(String imageURL) {
        boolean result = false;
        if (imageURL != null && !imageURL.isEmpty()) {
            logger.info("Trying to pull image : {}", imageURL);
            String command = "docker pull";
            command += Constants.WHITESPACE;
            command += imageURL;
            try {
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
        return isCommandSuccessful(DOCKER_CLI_VERSION);
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
