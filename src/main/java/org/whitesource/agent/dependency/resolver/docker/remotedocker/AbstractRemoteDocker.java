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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractRemoteDocker {

    private Collection<DockerImage> imagesPulled;
    private Collection<DockerImage> imagesFound;
    private static String DOCKER_CLI_VERSION = "docker --version";
    private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteDocker.class);

    protected RemoteDockerConfiguration config;
    /* --- Constructors --- */

    public AbstractRemoteDocker(RemoteDockerConfiguration config) {
        this.config = config;
    }

    /* --- Public methods --- */

    public int pullRemoteDockerImages() {
        int pulledImagesCount = 0;
        if (isAllSoftwareRequiredInstalled()) {
             if (loginToRemoteRegistry()) {
                 imagesFound = listImagesOnRemoteRegistry();
                 if (imagesFound != null && !imagesFound.isEmpty()) {
                     imagesPulled = pullImagesFromRemoteRegistry();
                     if (imagesPulled != null) {
                         return imagesPulled.size();
                     }
                 }
             }
        }
        return pulledImagesCount;
    }

    /*
     private void displayProgress(int index, int totalFiles) {
        StringBuilder sb = new StringBuilder("[INFO] ");

        // draw each animation for 4 frames
        int actualAnimationIndex = animationIndex % (ANIMATION_FRAMES * 4);
        sb.append(progressAnimation.get((actualAnimationIndex / 4) % ANIMATION_FRAMES));
        animationIndex++;

        // draw progress bar
        sb.append(" [");
        double percentage = ((double) index / totalFiles) * 100;
        int progressionBlocks = (int) (percentage / 3);
        for (int i = 0; i < progressionBlocks; i++) {
            sb.append(Constants.POUND);
        }
        for (int i = progressionBlocks; i < 33; i++) {
            sb.append(Constants.WHITESPACE);
        }
        sb.append("] {0}% - {1} of {2} files\r");
        System.out.print(MessageFormat.format(sb.toString(), (int) percentage, index, totalFiles));

        if (index == totalFiles) {
            // clear progress animation
            System.out.print("                                                                                  \r");
        }
    }
     */

    protected abstract boolean loginToRemoteRegistry();

    protected abstract Collection<DockerImage> listImagesOnRemoteRegistry();

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

    protected abstract boolean isRequiredRegistryManagerInstalled();

    private boolean isAllSoftwareRequiredInstalled() {
        return isDockerInstalled() && isRequiredRegistryManagerInstalled();
    }

    private boolean isImageRequired(DockerImage image) {
        if (config != null && image != null) {
            // TODO: Maybe enable regular expression for tags/ids ?! like .*18.3.* or .*.*

            List<String> tagsList = config.getImageTags();
            List<String> digestList = config.getImageDigests();

            // If tag list is not configured - we assume that we want to scan ALL tags
            if (tagsList == null || tagsList.isEmpty() || tagsList.contains(".*.*")) {
                return true;
            }

            // If digest list is not configured - we assume that we want to scan ALL tags
            if (digestList == null || digestList.isEmpty() || digestList.contains(".*.*")) {
                return true;
            }

            // Otherwise we look for specific tag(s)/sha256
            String imageTag = image.getTag();
            String imageDigest = image.getId();

            if ((imageTag == null || imageTag.isEmpty()) && (imageDigest == null || imageDigest.isEmpty())) {
                // This tag/sha256 does not met the requirements
                return false;
            }

            // Is this tag/sha256 in the required tags/sha256 list?
            return tagsList.contains(imageTag) || digestList.contains(imageDigest);
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
            Pair<Integer, String> pullResult = executeCommandWithOutput(command);
            result = pullResult.getKey() == 0;
            if (result) {
                logger.info("Image was pulled successfully (or already up to date)");
                int index = pullResult.getValue().indexOf("Status:");
                if (index > 0) {
                    String status = pullResult.getValue().substring(index);
                    logger.info("{}", status);
                }
            } else {
                logger.info("Image was not pulled!");
                logger.info("{}", pullResult.getValue());
            }
        }
        return result;
    }

    //public abstract Collection<DockerImage> getAllImages();
    /*
    protected boolean isEnoughSpaceForImage() {
        return true;
    }
*/
    // This function cannot be run with multiple threads because each time it is run it overrides the values
    // of resultVal and inputStream, so if other thread (for example) is reading the stream, it might get mixed data
    // from old command's stream and the new command's stream
    Pair<Integer, InputStream> executeCommand(String command) {
        int resultVal = 1;
        InputStream inputStream = null;
        try {
            Process process =  Runtime.getRuntime().exec(command);
            resultVal = process.waitFor();
            inputStream = process.getInputStream();
        } catch (IOException | InterruptedException e) {
            logger.info("Execution of {} failed: code - {} ; message - {}", command, resultVal, e.getMessage());
            Thread.currentThread().interrupt();
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

    private Pair<Integer, String> executeCommandWithOutput(String command) {
        Pair<Integer, InputStream> executionResult = executeCommand(command);
        StringBuilder stBuilder = new StringBuilder();
        Integer intVal = executionResult.getKey();
        try {
            String line;
            InputStream inputStream = executionResult.getValue();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                stBuilder.append(line);
            }
        } catch (IOException e) {
            logger.info("Execution of {} failed - {}", command, e.getMessage());
        }
        return new Pair<>(intVal, stBuilder.toString());
    }

    private boolean isDockerInstalled() {
        return isCommandSuccessful(DOCKER_CLI_VERSION);
    }

    public void removePulledImages() {
        if (imagesPulled != null) {
            for(DockerImage image: imagesPulled) {
                executeCommand("docker rmi " + image.getId());
            }
        }
    }

    protected abstract String getImageFullURL(DockerImage image);

}
