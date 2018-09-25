package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.*;
import org.apache.commons.lang.StringUtils;
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
import java.util.*;

public class RemoteDockerAmazonECR extends AbstractRemoteDocker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteDocker.class);
    private static final String REGEX_SHA256 = "sha256:";
    private static final int    LENGTH_OF_SHA256 = 64;
    private static final String AWS_VERSION = "aws --version";
    private static final String AWS_ECR_GET_LOGIN = "aws ecr get-login --no-include-email";
    private static final AmazonECR amazonClient = AmazonECRClientBuilder.standard().build();

    private Map<String, String> imageToRepositoryUriMap;
    private String defaultRegistryId;

    public RemoteDockerAmazonECR(RemoteDockerConfiguration config) {
        super(config);
        imageToRepositoryUriMap = new HashMap<>();
    }

    public boolean isRequiredRegistryManagerInstalled() {
        return isCommandSuccessful(AWS_VERSION);
    }

    public boolean loginToRemoteRegistry() {
        boolean saveDefaultRegistryId = true;
        StringBuilder stCommand = new StringBuilder();
        stCommand.append(AWS_ECR_GET_LOGIN);
        if (config != null) {
            List<String> registriesList = config.getAmazonRegistryIds();
            if (registriesList != null && !registriesList.isEmpty()) {
                stCommand.append(Constants.WHITESPACE);
                stCommand.append("--registry-ids");
                for (String registryId : registriesList) {
                    stCommand.append(Constants.WHITESPACE);
                    stCommand.append(registryId);
                }
                // We have several registry ids - so we don't know which is the default
                saveDefaultRegistryId = false;
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
                        // Execute the Docker command and get permission
                        result = executeCommand(line);
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
        }
        return loginResult;
    }

    private String getSHA256FromManifest(String manifest) {
        if (manifest == null || manifest.isEmpty()) {
            return Constants.EMPTY_STRING;
        }
        /*
         * Manifest content will look like:
         {
           "schemaVersion": 2,
           "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
           "config": {
              "mediaType": "application/vnd.docker.container.image.v1+json",
              "size": 16528,
              "digest": "sha256:2c73dd0370e688b915c0814e0a533252f69c0a30d06e62918f61b5df932d4d3a"
           },
           "layers": [
              {
                 "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                 "size": 42326024,
                 "digest": "sha256:95871a41108917a5c23932b5bc425cbd6bd3db6c232b5f413d6ef4d6e658d95e"
              },
              {
                "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                "size": 849,
                "digest": "sha256:f7253e37cce8225bbf4fbcd40af1810064ae4ea4a1692547910faf0c0cb68231"
              },
              ...
           ]
        }
        And we need to extract the value of "digest" field under "config"
        */

        // TODO: Better solution - use regular expression
        // Simple solution - find the first 'sha256:' substring and get the 64 characters after it (SHA256 size i 64 char)
        int indexOfSHA256 = manifest.indexOf(REGEX_SHA256);
        if (indexOfSHA256 < 0) {
            return Constants.EMPTY_STRING;
        }

        try {
            int startIndex = indexOfSHA256 + REGEX_SHA256.length();
            int endIndex = startIndex + LENGTH_OF_SHA256 ;
            return manifest.substring(startIndex, endIndex);
        } catch (Exception ex) {
            logger.error("Could not get config -> digest -> sha256 value from manifest - {}", ex.getMessage());
            logger.error("Manifest content - {}", manifest);
        }
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getImageFullURL(DockerImage image) {
        String result = "";
        if (image != null) {
            String repositoryUri = imageToRepositoryUriMap.get(image.getRepository());
            if (repositoryUri != null && !repositoryUri.isEmpty()) {
                // TODO: Can region be different from us-east-1 ?!
                /* Command should look like:
                   'docker pull {registryId}.dkr.ecr.us-east-1.amazonaws.com/{imageName}:{Tag}'
                */
                result = repositoryUri + ":" + image.getTag();
            }
        }
        return result;
    }

    private Collection<Repository> getRepositoriesList(String registryId, List<String> repositoryNames) {
        // aws ecr describe-repositories  [--registry-id <value>] [--repository-names <value>]

        // If registry id is null/empty - the default registry is assumed
        DescribeRepositoriesRequest request = new DescribeRepositoriesRequest();
        if (registryId != null && !registryId.isEmpty()) {
            request = request.withRegistryId(registryId);
        }
        // If repository names is null/empty - then all repositories in a registry are described
        if (repositoryNames != null && !repositoryNames.isEmpty()) {
            request = request.withRepositoryNames(repositoryNames);
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

        return repositoriesList;
    }

    private Collection<ImageDetail> getImagesOfRepository(String repositoryName, String registryId) {
        // aws ecr describe-images [--registry-id <value>] --repository-name <value>

        // RepositoryName cannot be null/empty
        if (repositoryName == null || repositoryName.isEmpty()) {
            return Collections.emptyList();
        }

        DescribeImagesRequest request = new DescribeImagesRequest().withRepositoryName(repositoryName);
        if (registryId != null && !registryId.isEmpty()) {
            request = request.withRegistryId(registryId);
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
        return imageDetailsList;
    }

    private List<Image> getImagesInformation(String repositoryName, String registryId, String tag, String digest) {
        // aws ecr batch-get-image [--registry-id <value>] --repository-name <value> --image-ids <value>
        // --image-ids imageTag=<value>,imageDigest=<value> - can be 1 of them or both

        // RepositoryName cannot be null/empty
        if (repositoryName == null || repositoryName.isEmpty()) {
            return Collections.emptyList();
        }

        // Should be at least Tag or Digest
        boolean tagIsEmpty = tag == null || tag.isEmpty();
        boolean digestIsEmpty = digest == null || digest.isEmpty();
        if (tagIsEmpty && digestIsEmpty) {
            return Collections.emptyList();
        }

        ImageIdentifier imageIdentifier = new ImageIdentifier();
        if (!tagIsEmpty) {
            imageIdentifier = imageIdentifier.withImageTag(tag);
        }
        if (!digestIsEmpty) {
            imageIdentifier = imageIdentifier.withImageDigest(digest);
        }

        List<Image> resultImage = null;
        BatchGetImageRequest request = new BatchGetImageRequest().withImageIds(imageIdentifier).withRepositoryName(repositoryName);

        if (registryId != null && !registryId.isEmpty()) {
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

    public Collection<DockerImage> listImagesOnRemoteRegistry() {
        List<String> registryIdsList = config.getAmazonRegistryIds();
        // The registry id values should be explicitly defined, so if the user does not define it or use a .*.* value
        // then we convert it to empty value - which will be considered by Amazon as the default registry (of the user
        // that performed the login)
        if (registryIdsList == null || registryIdsList.isEmpty() || registryIdsList.contains(".*.*")) {
            registryIdsList = new ArrayList<>(1);
            registryIdsList.add(Constants.EMPTY_STRING);
        }

        List<DockerImage> result = new ArrayList<>();
        // Get all repositories of required registry ids
        for(String registryId : registryIdsList) {
            // Use 'null' instead of 'config.getImageNames()' - because getImageNames() can include repository names
            // that do not appear in the current registry id - this will cause an exception from Amazon response and
            // we will not get the other available repository names in the response.
            // But when using repository names = null -> Amazon will treat it as a request to bring all repositories
            Collection<Repository> repositoriesList = getRepositoriesList(registryId,null);
            if (repositoriesList != null) {
                // for each repository (repository = collection of same image with different tags/digests)
                for (Repository repository : repositoriesList) {
                    // repositoryName cannot be null
                    String repositoryName = repository.getRepositoryName();
                    // Get information about all images in the repository
                    Collection<ImageDetail> imageDetailsList = getImagesOfRepository(repositoryName, registryId);
                    // The information is 'ImageDetail' contains the sha256 as Amazon stores it
                    // But we need the sha256 as Docker stores it
                    if (imageDetailsList != null) {
                        // So for each image (repository,registry,digest -> unique key)
                        for (ImageDetail imageDetail : imageDetailsList) {
                            String digest = imageDetail.getImageDigest();
                            String registry = imageDetail.getRegistryId();
                            // Get the 'Image' information - it includes the sha256 as Docker stores it
                            List<Image> imagesList = getImagesInformation(repositoryName, registry, "", digest);
                            if (imagesList != null && !imagesList.isEmpty()) {
                                for (Image image : imagesList) {
                                    // Convert 'Image' to 'DockerImage' by extracting the Docker sha256 from 'Image'
                                    DockerImage newDockerImage = getRepositoryImageAsDockerImage(image);
                                    result.add(newDockerImage);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}
