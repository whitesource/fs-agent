package org.whitesource.agent.dependency.resolver.docker.remotedocker.amazon;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.docker.DockerImage;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.AbstractRemoteDocker;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.AbstractRemoteDockerImage;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.RemoteDockerConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class RemoteDockerAmazonECR extends AbstractRemoteDocker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteDocker.class);
    private static final String AWS_VERSION = "aws --version";
    private static final String AWS_ECR_GET_LOGIN = "aws ecr get-login --no-include-email";
    private static final AmazonECR amazonClient = AmazonECRClientBuilder.standard().build();

    private Map<String, String> imageToRepositoryUriMap;
    private Map<String, String> imageDigestToTagMap;
    private String defaultRegistryId;

    public RemoteDockerAmazonECR(RemoteDockerConfiguration config) {
        super(config);
        imageToRepositoryUriMap = new HashMap<>();
        imageDigestToTagMap = new HashMap<>();
    }

    public boolean isRegistryCliInstalled() {
        boolean installed = isCommandSuccessful(AWS_VERSION);
        if (!installed) {
            logger.error("AWS ECR is not installed or its path is not configured correctly");
        }
        return installed;
    }

    public boolean loginToRemoteRegistry() {
        boolean saveDefaultRegistryId = true;
        StringBuilder stCommand = new StringBuilder();
        stCommand.append(AWS_ECR_GET_LOGIN);
        if (config != null) {
            List<String> registriesList = config.getAmazonRegistryIds();
            // We have several registry Ids
            if (registriesList.size() > 1) {
                stCommand.append(Constants.WHITESPACE);
                stCommand.append("--registry-ids");
                for (String registryId : registriesList) {
                    stCommand.append(Constants.WHITESPACE);
                    stCommand.append(registryId);
                }
                // We have several registry ids - so we don't know which is the default
                saveDefaultRegistryId = false;
            } else if (registriesList.size() == 1) {
                String registryId = registriesList.get(0);
                if (Constants.EMPTY_STRING.equals(registryId)) {
                    logger.info("No registryIds value is found! Logging to default Amazon ECR registry");
                } else {
                    stCommand.append(Constants.WHITESPACE);
                    stCommand.append("--registry-ids");
                    stCommand.append(Constants.WHITESPACE);
                    stCommand.append(registryId);
                }
            }
        }

        boolean loginResult = false;
        // Run this command to ask for permissions from Amazon to run Docker
        Pair<Integer, InputStream> result = executeCommand(stCommand.toString());
        Integer intVal = result.getKey();
        // The request was successful
        if (intVal == 0) {
            try {
                String line;
                // The result will be a long string with the full information (like password, region, etc)
                InputStream inputStream = result.getValue();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

                // Each line will include a Docker login command
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(DOCKER_CLI_LOGIN)) {
                        String command = line;
                        if (SystemUtils.IS_OS_LINUX && config.isLoginSudo()) {
                            command = LINUX_PREFIX_SUDO + command;
                        }
                        // Execute the Docker command and get permission
                        result = executeCommand(command);
                        boolean loginToCurrentRegistry = (result.getKey() == 0);
                        // Were able to login to at least 1 registry
                        loginResult = loginResult || loginToCurrentRegistry;
                        String[] dockerCommandVal = line.split(Constants.WHITESPACE);
                        if (dockerCommandVal.length > 1) {
                            String registryPath = dockerCommandVal[dockerCommandVal.length - 1];
                            String loginMessage = "OK";
                            if (!loginToCurrentRegistry) {
                                loginMessage = "Failed";
                            }
                            logger.info("Login to registry : {} - {}", registryPath, loginMessage);
                            if (saveDefaultRegistryId) {
                                defaultRegistryId = registryPath;
                            }
                        }
                        else {
                            logger.info("Invalid Docker login command: {}", line);
                        }
                    }
                }
            } catch (IOException e) {
                logger.info("Execution of {} failed - {}", AWS_ECR_GET_LOGIN, e.getMessage());
            }
        } else {
            logger.info("Login to registries list - {} - failed", config.getAmazonRegistryIds());
            logger.debug("loginToRemoteRegistry - failed with error code {}", intVal);
        }
        return loginResult;
    }

    @Override
    public String getImageFullURL(AbstractRemoteDockerImage image) {
        String result = Constants.EMPTY_STRING;
        if (image != null) {
            String repositoryUri = imageToRepositoryUriMap.get(image.getRepositoryName());
            if (repositoryUri != null && !repositoryUri.isEmpty()) {
                /* Command should look like:
                   'docker pull {registryId}.dkr.ecr.us-east-1.amazonaws.com/{imageName}:{Tag}'
                */
                result = repositoryUri + Constants.COLON + imageDigestToTagMap.get(image.getImageDigest());
                //result = repositoryUri + "@" + image.getImageDigest();
            }
        }
        return result;
    }

    private Collection<Repository> getRepositoriesList(String registryId, List<String> repositoryNames) {
        // aws ecr describe-repositories  [--registry-id <value>] [--repository-names <value>]

        logger.debug("getRepositoriesList start");
        // If registry id is null/empty - the default registry is assumed
        DescribeRepositoriesRequest request = new DescribeRepositoriesRequest();
        if (!StringUtils.isBlank(registryId)) {
            request = request.withRegistryId(registryId);
            logger.debug("getRepositoriesList - registryId= {}", registryId);
        }
        // If repository names is null/empty - then all repositories in a registry are described
        if (repositoryNames != null && !repositoryNames.isEmpty()) {
            request = request.withRepositoryNames(repositoryNames);
            logger.debug("getRepositoriesList - repositoryNames= {}", repositoryNames);
        }

        Collection<Repository> repositoriesList = Collections.emptyList();
        try {
            DescribeRepositoriesResult response = amazonClient.describeRepositories(request);
            repositoriesList = response.getRepositories();
            if (repositoriesList != null) {
                for (Repository repository : repositoriesList) {
                    imageToRepositoryUriMap.put(repository.getRepositoryName(),repository.getRepositoryUri());
                }
            } else {
                repositoriesList = Collections.emptyList();
            }
        } catch (Exception ex) {
            String currentRegistryName = !StringUtils.isBlank(registryId) ? registryId : defaultRegistryId;
            logger.error("Could not get repositories info of registry - {}", currentRegistryName);
            logger.error("{}", ex.getMessage());
        }
        logger.debug("getRepositoriesList finish");
        return repositoriesList;
    }

    private Collection<ImageDetail> getRepositoryImages(String repositoryName, String registryId) {
        // aws ecr describe-images [--registry-id <value>] --repository-name <value>

        logger.debug("getRepositoryImages start");
        // RepositoryName cannot be null/empty
        if (StringUtils.isBlank(repositoryName)) {
            logger.debug("getRepositoryImages repositoryName is blank/null");
            return Collections.emptyList();
        }

        DescribeImagesRequest request = new DescribeImagesRequest().withRepositoryName(repositoryName);
        if (!StringUtils.isBlank(registryId)) {
            request = request.withRegistryId(registryId);
            logger.debug("getRepositoryImages repositoryName is {}", repositoryName);
        }
        List<ImageDetail> imageDetailsList = Collections.emptyList();
        try {
            DescribeImagesResult describeImagesResult = amazonClient.describeImages(request);
            imageDetailsList = describeImagesResult.getImageDetails();
        } catch (Exception ex) {
            String currentRegistryName = !StringUtils.isBlank(registryId) ? registryId : defaultRegistryId;
            logger.error("Could not get repository images info of repository {} - on registry - {}", repositoryName, currentRegistryName);
            logger.error("{}", ex.getMessage());
        }
        logger.debug("getRepositoryImages finish");
        return imageDetailsList;
    }

    private List<Image> getImagesInformation(String repositoryName, String registryId, String tag, String digest) {
        // aws ecr batch-get-image [--registry-id <value>] --repository-name <value> --image-ids <value>
        // --image-ids imageTag=<value>,imageDigest=<value> - can be 1 of them or both

        logger.debug("getImagesInformation start");

        // RepositoryName cannot be null/empty
        if (StringUtils.isBlank(repositoryName)) {
            logger.debug("getImagesInformation repositoryName is blank/null");
            return Collections.emptyList();
        }

        // Should be at least Tag or Digest
        boolean tagIsEmpty = StringUtils.isBlank(tag);
        boolean digestIsEmpty = StringUtils.isBlank(digest);
        if (tagIsEmpty && digestIsEmpty) {
            logger.debug("getImagesInformation tag && digest are blank/null");
            return Collections.emptyList();
        }

        ImageIdentifier imageIdentifier = new ImageIdentifier();
        if (!tagIsEmpty) {
            imageIdentifier = imageIdentifier.withImageTag(tag);
            logger.debug("getImagesInformation tag is {}", tag);
        }
        if (!digestIsEmpty) {
            imageIdentifier = imageIdentifier.withImageDigest(digest);
            logger.debug("getImagesInformation digest is {}", digest);
        }

        List<Image> resultImage = null;
        BatchGetImageRequest request = new BatchGetImageRequest().withImageIds(imageIdentifier).withRepositoryName(repositoryName);

        if (!StringUtils.isBlank(registryId)) {
            logger.debug("getImagesInformation registryId is {}", registryId);
            request = request.withRegistryId(registryId);
        }

        try {
            // Here we got a response that includes 2 lists:
            // images list & failures list
            BatchGetImageResult response = amazonClient.batchGetImage(request);
            if (response != null) {
                resultImage = response.getImages();
                List<ImageFailure> imageFailures = response.getFailures();
                if (imageFailures != null && !imageFailures.isEmpty()) {
                    logger.info("Errors received when trying to get images:");
                    for (ImageFailure imageFailure : imageFailures) {
                        logger.info("{}", imageFailure);
                    }
                }
            }
        }catch (Exception ex) {
            logger.error("Could not get detailed information for repositoryName - {}", repositoryName);
            logger.error("{}", ex.getMessage());
        }

        logger.debug("getImagesInformation finish");
        return resultImage;
    }

    private DockerImage getRepositoryImageAsDockerImage(Image image) {
        DockerImage resultImage = null;
        if (image != null) {
            String repositoryName = image.getRepositoryName();
            String manifestInfo = image.getImageManifest();
            // Docker's sha256 different form Amazon's sha256 (but it can be found in Amazon's manifest)
            String imageHash = getSHA256FromManifest(manifestInfo);
            try {
                String tag = image.getImageId().getImageTag();
                resultImage = new DockerImage(repositoryName, tag, imageHash);
            } catch (Exception ex) {
                logger.error("Could not get image tag of {} ", repositoryName);
                logger.error("{}", ex.getMessage());
            }
        }
        return resultImage;
    }

    @Override
    protected Set<AbstractRemoteDockerImage> getRemoteRegistryImagesList() {

        logger.debug("getRemoteRegistryImagesList start");

        List<String> registryIdsList = config.getAmazonRegistryIds();
        // The registry id values should be explicitly defined, so if the user does not define it or use a .*.* value
        // then we convert it to empty value - which will be considered by Amazon as the default registry (of the user
        // that performed the login)
        //Constants.
        if (registryIdsList == null || registryIdsList.isEmpty() || registryIdsList.contains(Constants.GLOB_PATTERN)) {
            registryIdsList = new LinkedList<>();
            registryIdsList.add(Constants.EMPTY_STRING);
            logger.debug("getRemoteRegistryImagesList registryIdsList is default (includes only empty string)");
        }

        //List<DockerImage> result = new LinkedList<>();
        Set<AbstractRemoteDockerImage> result = new HashSet<>();
        // Get all repositories of required registry ids
        for(String registryId : registryIdsList) {
            // Use 'null' instead of 'config.getImageNames()' - because getImageNames() can include repository names
            // that do not appear in the current registry id - this will cause an exception from Amazon response and
            // we will not get the other available repository names in the response.
            // But when using repository names = null -> Amazon will treat it as a request to bring all repositories
            logger.debug("getRemoteRegistryImagesList registryId is {}", registryId);
            Collection<Repository> repositoriesList = getRepositoriesList(registryId,null);
            if (repositoriesList != null) {
                // for each repository (repository = collection of same image with different tags/digests)
                for (Repository repository : repositoriesList) {
                    // repositoryName cannot be null
                    String repositoryName = repository.getRepositoryName();
                    logger.debug("getRemoteRegistryImagesList registryId - {} , repository - {}", registryId, repositoryName);
                    // Get information about all images in the repository
                    Collection<ImageDetail> imageDetailsList = getRepositoryImages(repositoryName, registryId);
                    // The information is 'ImageDetail' contains the sha256 as Amazon stores it
                    // But we need the sha256 as Docker stores it
                    if (imageDetailsList != null) {
                        // So for each image (repository,registry,digest -> unique key)
                        for (ImageDetail imageDetail : imageDetailsList) {
                            String digest = imageDetail.getImageDigest();
                            String registry = imageDetail.getRegistryId();
                            List<String> tags = imageDetail.getImageTags();
                            Date imagePushedAt = imageDetail.getImagePushedAt();
                            logger.debug("getRemoteRegistryImagesList registryId - {} , repository - {}, tags - {}",
                                    registryId, repositoryName, tags);
                            // Get the 'Image' information - it includes the sha256 as Docker stores it
                            List<Image> imagesList = getImagesInformation(repositoryName, registry, Constants.EMPTY_STRING, digest);
                            if (imagesList != null && !imagesList.isEmpty()) {
                                for (Image image : imagesList) {
                                    // Convert 'Image' to 'DockerImage' by extracting the Docker sha256 from 'Image'
                                    //DockerImage newDockerImage = getRepositoryImageAsDockerImage(image);
                                    //logger.debug("getRemoteRegistryImagesList - new Docker image - {}", newDockerImage);
                                    //result.add(image.getImageManifest());
                                    // TODO: change this
                                    String manifest = image.getImageManifest();
                                    String mainTag = image.getImageId().getImageTag();
                                    String dockerDigest = getSHA256FromManifest(manifest);

                                    DockerImageAmazon newDockerImage = new DockerImageAmazon(registry, repositoryName,
                                            digest, tags, imagePushedAt, manifest, mainTag, dockerDigest);
                                    //newDockerImage.setImageSha256(dockerDigest);

                                    result.add(newDockerImage);
                                    imageDigestToTagMap.put(digest, mainTag);
                                }
                            }
                        }
                    }
                }
            }
        }
        logger.debug("Found {} images", result.size());
        logger.debug("getRemoteRegistryImagesList finish");
        return result;
    }

    @Override
    protected void logoutRemoteDocker() {
        // TODO Check if need to logout from amazon account - We dont login to amazon account ? check with george
    }
}
