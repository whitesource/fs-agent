/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.OfflineUpdateRequest;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.fs.FileSystemAgentConfiguration;
import org.whitesource.fs.Main;
import org.whitesource.fs.StatusCode;
import org.whitesource.fs.configuration.ConfigurationValidation;

import java.io.*;
import java.util.*;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Abstract class for all WhiteSource command line agents.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class ProjectsSender {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ProjectsSender.class);

    private static final String NEW_LINE = "\n";
    private static final String DOT = ".";
    private static final String JAVA_NETWORKING = "java.net";
    private static final int MAX_NUMBER_OF_DEPENDENCIES = 1000000;
    private static final String AGENT_TYPE = "fs-agent";
    private static final String VERSION = "version";
    private static final String AGENTS_VERSION = "agentsVersion";

    /* --- Members --- */

    protected final Properties config;
    private final ConfigurationValidation configurationValidation;
    protected StatusCode prepStepStatusCode = StatusCode.SUCCESS;
    private Properties artifactProperties;

    /* --- Constructors --- */

    public ProjectsSender(FileSystemAgentConfiguration fileSystemAgentConfiguration) {
        this.config = fileSystemAgentConfiguration.getProperties();
        this.configurationValidation = new ConfigurationValidation();
        this.artifactProperties = getArtifactProperties();
    }

    /* --- Public methods --- */

    public StatusCode sendProjects(Collection<AgentProjectInfo> projects) {
        Iterator<AgentProjectInfo> iterator = projects.iterator();
        while (iterator.hasNext()) {
            AgentProjectInfo project = iterator.next();
            if (project.getDependencies().isEmpty()) {
                iterator.remove();

                // if coordinates are null, then use token
                String projectIdentifier;
                Coordinates coordinates = project.getCoordinates();
                if (coordinates == null) {
                    projectIdentifier = project.getProjectToken();
                } else {
                    projectIdentifier = coordinates.getArtifactId();
                }
                logger.info("Removing empty project {} from update (found 0 matching files)", projectIdentifier);
            }
        }

        if (projects.isEmpty()) {
            logger.info("Exiting, nothing to update");
            return StatusCode.SUCCESS;
        } else {
            return sendRequest(projects);
        }
    }

    /* --- Private methods --- */

    private StatusCode sendRequest(Collection<AgentProjectInfo> projects) {
        // org token
        String orgToken = config.getProperty(ORG_TOKEN_PROPERTY_KEY);

        // product token or name (and version)
        String product = config.getProperty(PRODUCT_TOKEN_PROPERTY_KEY);
        String productVersion = null;
        if (StringUtils.isBlank(product)) {
            product = config.getProperty(PRODUCT_NAME_PROPERTY_KEY);
            productVersion = config.getProperty(PRODUCT_VERSION_PROPERTY_KEY);
        }

        // requester email
        String requesterEmail = config.getProperty(REQUESTER_EMAIL);

        // send request
        logger.info("Initializing WhiteSource Client");
        WhitesourceService service = createService();
        if (getBooleanProperty(OFFLINE_PROPERTY_KEY, false)) {
            offlineUpdate(service, orgToken, requesterEmail, product, productVersion, projects);
            return this.prepStepStatusCode;
        } else {
            // update type
            UpdateType updateType = UpdateType.OVERRIDE;
            String updateTypeValue = config.getProperty(UPDATE_TYPE, UpdateType.OVERRIDE.toString());
            try {
                updateType = UpdateType.valueOf(updateTypeValue);
            } catch (Exception e) {
                logger.info("Invalid value {} for updateType, defaulting to {}", updateTypeValue, UpdateType.OVERRIDE);
            }
            logger.info("UpdateType set to {} ", updateTypeValue);

            checkDependenciesUpbound(projects);
            StatusCode statusCode = StatusCode.SUCCESS;
            try {
                if (getBooleanProperty(CHECK_POLICIES_PROPERTY_KEY, false)) {
                    boolean policyCompliance = checkPolicies(service, orgToken, product, productVersion, projects);
                    statusCode = policyCompliance ? StatusCode.SUCCESS : StatusCode.POLICY_VIOLATION;
                }
                if (statusCode == StatusCode.SUCCESS) {
                    update(service, orgToken, updateType, requesterEmail, product, productVersion, projects);
                }
            } catch (WssServiceException e) {
                if (e.getCause() != null &&
                        e.getCause().getClass().getCanonicalName().substring(0, e.getCause().getClass().getCanonicalName().lastIndexOf(DOT)).equals(JAVA_NETWORKING)) {
                    statusCode = StatusCode.CONNECTION_FAILURE;
                } else {
                    statusCode = StatusCode.SERVER_FAILURE;
                }
                logger.error("Failed to send request to WhiteSource server: " + e.getMessage(), e);
            } finally {
                if (service != null) {
                    service.shutdown();
                }
            }
            if (statusCode == StatusCode.SUCCESS) {
                return this.prepStepStatusCode;
            }
            return statusCode;
        }
    }

    private boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        return configurationValidation.getBooleanProperty(config, propertyName, defaultValue);
    }

    private void checkDependenciesUpbound(Collection<AgentProjectInfo> projects) {
        int numberOfDependencies = projects.stream().map(x -> x.getDependencies()).mapToInt(x -> x.size()).sum();
        if (numberOfDependencies > MAX_NUMBER_OF_DEPENDENCIES) {
            logger.warn("Number of dependencies: {} exceeded the maximum supported: {}", numberOfDependencies, MAX_NUMBER_OF_DEPENDENCIES);
        }
    }

    private WhitesourceService createService() {
        String serviceUrl = config.getProperty(ClientConstants.SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        logger.info("Service URL is " + serviceUrl);
        boolean setProxy = false;
        final String proxyHost = config.getProperty(PROXY_HOST_PROPERTY_KEY);
        if (StringUtils.isNotBlank(proxyHost) || !getBooleanProperty(OFFLINE_PROPERTY_KEY, false)) {
            setProxy = true;
        }
        int connectionTimeoutMinutes = Integer.parseInt(config.getProperty(ClientConstants.CONNECTION_TIMEOUT_KEYWORD,
                String.valueOf(ClientConstants.DEFAULT_CONNECTION_TIMEOUT_MINUTES)));
        final WhitesourceService service = new WhitesourceService(getAgentType(), getAgentVersion(), getPluginVersion(),
                serviceUrl, setProxy, connectionTimeoutMinutes);
        if (StringUtils.isNotBlank(proxyHost)) {
            final int proxyPort = Integer.parseInt(config.getProperty(PROXY_PORT_PROPERTY_KEY));
            final String proxyUser = config.getProperty(PROXY_USER_PROPERTY_KEY);
            final String proxyPass = config.getProperty(PROXY_PASS_PROPERTY_KEY);
            service.getClient().setProxy(proxyHost, proxyPort, proxyUser, proxyPass);
        }
        return service;
    }

    private boolean checkPolicies(WhitesourceService service, String orgToken, String product, String productVersion,
                                  Collection<AgentProjectInfo> projects) throws WssServiceException {
        boolean policyCompliance = true;
        boolean forceCheckAllDependencies = getBooleanProperty(FORCE_CHECK_ALL_DEPENDENCIES, false);
        logger.info("Checking policies");
        CheckPolicyComplianceResult checkPoliciesResult = service.checkPolicyCompliance(orgToken, product, productVersion, projects, forceCheckAllDependencies);
        boolean hasRejections = checkPoliciesResult.hasRejections();
        if (hasRejections) {
            if (getBooleanProperty(FORCE_UPDATE, false)) {
                logger.info("Some dependencies violate open source policies, however all were force " +
                        "updated to organization inventory.");
            } else {
                logger.info("Some dependencies did not conform with open source policies, review report for details");
                logger.info("=== UPDATE ABORTED ===");
                policyCompliance = false;
            }
        } else {
            logger.info("All dependencies conform with open source policies.");
        }

        try {
            // generate report
            PolicyCheckReport report = new PolicyCheckReport(checkPoliciesResult);
            File outputDir = new File(".");
            report.generate(outputDir, false);
            report.generateJson(outputDir);
            logger.info("Policies report generated successfully");
        } catch (IOException e) {
            logger.error("Error generating check policies report: " + e.getMessage(), e);
        }

        return policyCompliance;
    }

    private void update(WhitesourceService service, String orgToken, UpdateType updateType, String requesterEmail, String product, String productVersion,
                        Collection<AgentProjectInfo> projects) throws WssServiceException {
        logger.info("Sending Update");
        UpdateInventoryResult updateResult = service.update(orgToken, requesterEmail, updateType, product, productVersion, projects);
        logResult(updateResult);
    }

    private void offlineUpdate(WhitesourceService service, String orgToken, String requesterEmail, String product, String productVersion,
                               Collection<AgentProjectInfo> projects) {
        logger.info("Generating offline update request");

        boolean zip = getBooleanProperty(OFFLINE_ZIP_PROPERTY_KEY, false);
        boolean prettyJson = getBooleanProperty(OFFLINE_PRETTY_JSON_KEY, false);

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(orgToken, product, productVersion, projects);

        updateRequest.setRequesterEmail(requesterEmail);
        try {
            OfflineUpdateRequest offlineUpdateRequest = new OfflineUpdateRequest(updateRequest);

            UpdateType updateTypeFinal;

            // if the update type was forced by command or config -> set it
            if (StringUtils.isNotBlank(config.getProperty(UPDATE_TYPE))){
                String updateTypeValue = config.getProperty(UPDATE_TYPE) ;
                try {
                    updateTypeFinal = UpdateType.valueOf(updateTypeValue);
                } catch (Exception e) {
                    logger.info("Invalid value {} for updateType, defaulting to {}", updateTypeValue, UpdateType.OVERRIDE);
                    updateTypeFinal = UpdateType.OVERRIDE;
                }
            }else {
                // Otherwise use the parameter in the file
                updateTypeFinal = updateRequest.getUpdateType();
            }

            logger.info("UpdateType offline set to {} ", updateTypeFinal);
            updateRequest.setUpdateType(updateTypeFinal);
            File outputDir = new File(".");
            File file = offlineUpdateRequest.generate(outputDir, zip, prettyJson);
            logger.info("Offline request generated successfully at {}", file.getPath());
        } catch (IOException e) {
            logger.error("Error generating offline update request: " + e.getMessage());
        } finally {
            if (service != null) {
                service.shutdown();
            }
        }
    }

    private void logResult(UpdateInventoryResult updateResult) {
        StringBuilder resultLogMsg = new StringBuilder("Inventory update results for ").append(updateResult.getOrganization()).append(NEW_LINE);

        // newly created projects
        Collection<String> createdProjects = updateResult.getCreatedProjects();
        if (createdProjects.isEmpty()) {
            resultLogMsg.append("No new projects found.").append(NEW_LINE);
        } else {
            resultLogMsg.append("Newly created projects:").append(NEW_LINE);
            for (String projectName : createdProjects) {
                resultLogMsg.append(projectName).append(NEW_LINE);
            }
        }

        // updated projects
        Collection<String> updatedProjects = updateResult.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
            resultLogMsg.append("No projects were updated.").append(NEW_LINE);
        } else {
            resultLogMsg.append("Updated projects:").append(NEW_LINE);
            for (String projectName : updatedProjects) {
                resultLogMsg.append(projectName).append(NEW_LINE);
            }
        }

        // support token
        String requestToken = updateResult.getRequestToken();
        if (StringUtils.isNotBlank(requestToken)) {
            resultLogMsg.append(NEW_LINE).append("Support Token: ").append(requestToken).append(NEW_LINE);
        }
        logger.info(resultLogMsg.toString());
    }

    private String getAgentType() {
        return AGENT_TYPE;
    }

    private String getAgentVersion() {
        return getResource(AGENTS_VERSION);
    }

    private String getPluginVersion() {
        return getResource(VERSION);
    }

    private String getResource(String propertyName) {
        String val = (artifactProperties.getProperty(propertyName));
        if(StringUtils.isNotBlank(val)){
            return val;
        }
        return "";
    }

    private Properties getArtifactProperties() {
        Properties properties = new Properties();
        try (InputStream stream = Main.class.getResourceAsStream("/project.properties")) {
            properties.load(stream);
        } catch (IOException e) {
            logger.error("Failed to get version ", e);
        }
        return properties;
    }
}