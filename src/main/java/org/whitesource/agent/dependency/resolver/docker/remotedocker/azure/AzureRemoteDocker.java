package org.whitesource.agent.dependency.resolver.docker.remotedocker.azure;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.AbstractRemoteDocker;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.AbstractRemoteDockerImage;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.RemoteDockerConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AzureRemoteDocker extends AbstractRemoteDocker {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(AzureRemoteDocker.class);
    private static final String DIGEST = "digest";
    private static final String TAGS = "tags";
    private static final String AZURE_SERVER_NAME = ".azurecr.io";

    /* --- Private members --- */

    private AzureCli azureCli;
    private boolean loggedInToAzure = false;
    private List<String> loggedInRegistries = new ArrayList<>();

    /* --- Constructors --- */

    public AzureRemoteDocker(RemoteDockerConfiguration config) {
        super(config);
        azureCli = new AzureCli();
    }

    /* --- Overridden protected methods --- */

    /**
     * Log in to container registry in Azure cloud.
     * If login succeeded, docker obtains authentication to pull images from this registry.
     * @return
     */
    @Override
    protected boolean loginToRemoteRegistry() {
        Pair<Integer, InputStream> result;

        try {
            // Check if user already logged in to azure
            isUserLoggedIn();

            // If user isn't logged in, then login to azure via az cli.
            if(!loggedInToAzure) {
                Process process = Runtime.getRuntime().exec(azureCli.getLoginCommand(config.getAzureUserName(), config.getAzureUserPassword()));
                int resultValue = process.waitFor();
                if (resultValue == 0) {
                    logger.info("Log in to Azure account {} - Succeeded", config.getAzureUserName());
                } else {
                    logger.info("Log in to Azure account {} - Failed", config.getAzureUserName());
                    String errorMessage = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8.name());
                    logger.error("Failed to log in to Azure account {} - {}", config.getAzureUserName(), errorMessage);
                    return false;
                }
            }

            // Log in to registry via azure cli, Run azure command: "az acr login -n <RegistryName>"
            // Once azure login command to registry succeeded - docker obtains authentication to pull images from registry.
            for (String registryName : config.getAzureRegistryNames()) {
                if (registryName != null && !registryName.trim().equals(Constants.EMPTY_STRING)) {
                    logger.info("Log in to Azure container registry {}", registryName);
                    result = executeCommand(azureCli.getLoginContainerRegistryCommand(registryName));
                    if (result.getKey() == 0) {
                        logger.info("Login to registry : {} - Succeeded", registryName);
                        loggedInRegistries.add(registryName);
                    } else {
                        logger.info("Login to registry {} - Failed", registryName);
                        logger.error("Failed to login registry {} - {}", registryName, IOUtils.toString(result.getValue(), StandardCharsets.UTF_8.name()));
                    }
                }
            }
            return true;

        } catch (InterruptedException interruptedException) {
            logger.debug("Failed to login to Azure account, Exception: {}", interruptedException.getMessage());
        } catch (IOException ioException) { // this exception occurred when parsing inputStream to String
            logger.debug("Failed to parse command error result, Exception: {}", ioException.getMessage());
        }

        return false;
    }

    @Override
    protected Set<AbstractRemoteDockerImage> getRemoteRegistryImagesList() {
        Set<AbstractRemoteDockerImage> images = new HashSet<>();
        if (!loggedInRegistries.isEmpty()) {
            logger.info("Get list of images for registries : [{}]", String.join(", ", loggedInRegistries));
            Gson gson = new Gson();
            String repositoryCommand;
            String[] repositoryNames;
            Pair<Integer, InputStream> result;

            // registryNames list taken from Config file
            for (String registryName : loggedInRegistries) {
                repositoryNames = null;
                /* Get list of images names in registry
                   Run azure command: "az acr repository list --name <RegistryName>
                 */
                repositoryCommand = azureCli.getRepositoryListCommand(registryName);
                result = executeCommand(repositoryCommand);
                try {
                    if (result.getKey() == 0) {
                        // Parse json array to string array that contain repositories/images names.
                        String resultValue = IOUtils.toString(result.getValue(), StandardCharsets.UTF_8.name());
                        logger.debug("Azure images names for registry \"{}\" : {}", registryName, resultValue);

                        Type type = new TypeToken<String[]>() {}.getType();
                        repositoryNames = gson.fromJson(resultValue, type);
                    } else {
                        logger.warn("Failed to get repositories list for registry \"{}\"", registryName);
                    }

                    if (repositoryNames != null) {
                        for (String repositoryName : repositoryNames) {
                            AzureDockerImage azureImage = new AzureDockerImage();
                            azureImage.setRepositoryName(repositoryName);
                            azureImage.setRegistry(registryName + AZURE_SERVER_NAME);

                            // Get repository manifest, digest and tags via azure cli command "az acr repository show-manifest"
                            /* [
                                  {
                                    "digest": "sha256:915f390a8912e16d4beb8689720a17348f3f6d1a7b659697df850ab625ea29d5",
                                    "tags": [
                                      "v1"
                                    ],
                                    "timestamp": "2018-11-20T14:55:48.3185739Z"
                                  }
                                ]
                            */
                            repositoryCommand = azureCli.getRepositoryShowManifest(registryName, repositoryName);
                            result = executeCommand(repositoryCommand);

                            if (result.getKey() == 0) {
                                String resultValue = IOUtils.toString(result.getValue(), StandardCharsets.UTF_8.name());
                                logger.debug("Manifest for repository \"{}\": {}", repositoryName, resultValue);
                                JSONArray jsonArray = new JSONArray(resultValue);
                                JSONObject jsonObject = jsonArray.getJSONObject(0);
                                // TODO in case there's  more than one digest?!
                                String digest = jsonObject.getString(DIGEST);
                                azureImage.setImageDigest(digest);
                                azureImage.setImageSha256(getSHA256FromManifest(digest));
                                //TODO tags not ok
                                setImageTagsList(azureImage, jsonObject);

                                images.add(azureImage);
                            } else {
                                logger.warn("Failed to get details of repository {}", repositoryName);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Failed to parse command {} result. Exception: {}", repositoryCommand, e.getMessage());
                    logger.debug("Failed to parse command {} result. Exception: {}", repositoryCommand, e.getStackTrace());
                }
            }
        }

        return images;
    }

    @Override
    protected boolean isRegistryCliInstalled() {
        boolean installed = isCommandSuccessful(azureCli.getBasicCommand());
        if (!installed) {
            logger.error("Azure CLI is not installed");
        }
        return installed;
    }

    @Override
    protected String getImageFullURL(AbstractRemoteDockerImage image) {
        String result = Constants.EMPTY_STRING;
        if (image != null) {
            // get Unique identifier for azure image "AzureDockerImage"
            result = image.getUniqueIdentifier();
        }
        return result;
    }

    @Override
    protected void logoutRemoteDocker() {
        // If user isn't logged in, then logout from azure via az cli.
        if(!loggedInToAzure) {
            logger.debug("Logging out from azure account..");
            boolean loggedOut = isCommandSuccessful(azureCli.getLogoutCommand(config.getAzureUserName()));
            if (!loggedOut) {
                logger.error("Failed to logout from azure account");
            }
        }
    }

    private void setImageTagsList(AzureDockerImage azureImage, JSONObject jsonObject) {
        List<String> tags = new ArrayList<>();
        JSONArray tagsJsonArray = jsonObject.getJSONArray(TAGS);
        for (int i = 0; i < tagsJsonArray.length(); i++) {
            tags.add(tagsJsonArray.getString(i));
        }
        azureImage.setImageTags(tags);
    }

    /**
     * Get Azure logged in users account list.
     * Check if user logged in. If true then set 'loggedInToAzure' to true and UA will not Login/Logout account.
     *
     */
    private void isUserLoggedIn() {
        String accountListCmd = azureCli.getLoggedInAccountList();
        Pair<Integer, InputStream> result = executeCommand(accountListCmd);
        if (result.getKey() == 0) {
            try {
                String accountList = IOUtils.toString(result.getValue(), StandardCharsets.UTF_8.name());

                JSONArray jsonArray = new JSONArray(accountList);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String loggedInUserName = jsonObject.getJSONObject("user").getString("name");
                    if (loggedInUserName.equalsIgnoreCase(config.getAzureUserName())) {
                        loggedInToAzure = true;
                        break;
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to parse command {} result. Exception: {}", accountListCmd, e.getMessage());
                logger.debug("Failed to parse command {} result. Exception: {}", accountListCmd, e.getStackTrace());
            }
        } else {
            logger.warn("Failed to get Azure logged in account list, command: {}.", accountListCmd);
        }
    }
}