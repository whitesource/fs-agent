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
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.OfflineUpdateRequest;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.*;
import org.whitesource.fs.configuration.OfflineConfiguration;
import org.whitesource.fs.configuration.SenderConfiguration;

import java.io.*;
import java.util.*;

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

    private static final String NEW_LINE = System.lineSeparator();
    private static final String DOT = ".";
    private static final String JAVA_NETWORKING = "java.net";
    private static final int MAX_NUMBER_OF_DEPENDENCIES = 1000000;
    private static final String AGENT_TYPE = "fs-agent";
    private static final String VERSION = "version";
    private static final String AGENTS_VERSION = "agentsVersion";

    /* --- Members --- */

    protected final SenderConfiguration senderConfiguration;
    private final OfflineConfiguration offlineConfiguration;
    protected StatusCode prepStepStatusCode = StatusCode.SUCCESS;
    private Properties artifactProperties;

    /* --- Constructors --- */

    public ProjectsSender(SenderConfiguration senderConfiguration, OfflineConfiguration offlineConfiguration) {
        this.senderConfiguration = senderConfiguration;
        this.offlineConfiguration = offlineConfiguration;
        this.artifactProperties = getArtifactProperties();
    }

    /* --- Public methods --- */

    public Pair<String, StatusCode> sendRequest(Collection<AgentProjectInfo> projects, String orgToken, String requesterEmail,
                                                String productNameOrToken, String productVersion, String whiteSourceFolderPath) {
        // send request
        logger.info("Initializing WhiteSource Client");
        WhitesourceService service = createService();
        String resultInfo = "";
        if (offlineConfiguration.isEnabled()) {
            resultInfo = offlineUpdate(service, orgToken, requesterEmail, productNameOrToken, productVersion, projects, whiteSourceFolderPath);
            return new Pair<>(resultInfo, this.prepStepStatusCode);
        } else {
            // update type
            UpdateType updateType = UpdateType.OVERRIDE;
            String updateTypeValue = senderConfiguration.getUpdateTypeValue();
            try {
                updateType = UpdateType.valueOf(updateTypeValue);
            } catch (Exception e) {
                logger.info("Invalid value {} for updateType, defaulting to {}", updateTypeValue, UpdateType.OVERRIDE);
            }
            logger.info("UpdateType set to {} ", updateTypeValue);

            checkDependenciesUpbound(projects);
            StatusCode statusCode = StatusCode.SUCCESS;
            try {
                if (senderConfiguration.isCheckPolicies()) {
                    boolean policyCompliance = checkPolicies(service, orgToken, productNameOrToken, productVersion, projects, whiteSourceFolderPath);
                    statusCode = policyCompliance ? StatusCode.SUCCESS : StatusCode.POLICY_VIOLATION;
                }
                if (statusCode == StatusCode.SUCCESS) {
                    resultInfo = update(service, orgToken, updateType, requesterEmail, productNameOrToken, productVersion, projects);
                    logger.info(resultInfo);
                    //strip line separators
                    resultInfo = resultInfo.replace(System.lineSeparator(), "");
                }
            } catch (WssServiceException e) {
                if (e.getCause() != null &&
                        e.getCause().getClass().getCanonicalName().substring(0, e.getCause().getClass().getCanonicalName().lastIndexOf(DOT)).equals(JAVA_NETWORKING)) {
                    statusCode = StatusCode.CONNECTION_FAILURE;
                } else {
                    statusCode = StatusCode.SERVER_FAILURE;
                }

                resultInfo = "Failed to send request to WhiteSource server: " + e.getMessage();
                logger.error(resultInfo, e);
            } finally {
                if (service != null) {
                    service.shutdown();
                }
            }
            if (statusCode == StatusCode.SUCCESS) {
                return new Pair<>(resultInfo, this.prepStepStatusCode);
            }
            return new Pair<>(resultInfo, statusCode);
        }
    }


    private void checkDependenciesUpbound(Collection<AgentProjectInfo> projects) {
        int numberOfDependencies = projects.stream().map(x -> x.getDependencies()).mapToInt(x -> x.size()).sum();
        if (numberOfDependencies > MAX_NUMBER_OF_DEPENDENCIES) {
            logger.warn("Number of dependencies: {} exceeded the maximum supported: {}", numberOfDependencies, MAX_NUMBER_OF_DEPENDENCIES);
        }
    }

    private WhitesourceService createService() {
        String serviceUrl = senderConfiguration.getServiceUrl();
        logger.info("Service URL is " + serviceUrl);
        boolean setProxy = false;
        final String proxyHost = senderConfiguration.getProxyHost();
        if (StringUtils.isNotBlank(proxyHost) || !offlineConfiguration.isEnabled()) {
            setProxy = true;
        }
        int connectionTimeoutMinutes = senderConfiguration.getConnectionTimeOut();
        final WhitesourceService service = new WhitesourceService(getAgentType(), getAgentVersion(), getPluginVersion(),
                serviceUrl, setProxy, connectionTimeoutMinutes);
        if (StringUtils.isNotBlank(proxyHost)) {
            final int proxyPort = senderConfiguration.getProxyPort();
            final String proxyUser = senderConfiguration.getProxyUser();
            final String proxyPass = senderConfiguration.getProxyPassword();
            service.getClient().setProxy(proxyHost, proxyPort, proxyUser, proxyPass);
        }
        return service;
    }

    private boolean checkPolicies(WhitesourceService service, String orgToken, String product, String productVersion,
                                  Collection<AgentProjectInfo> projects, String whiteSourceFolderPath) throws WssServiceException {
        boolean policyCompliance = true;
        boolean forceCheckAllDependencies = senderConfiguration.isForceCheckAllDependencies();
        logger.info("Checking policies");
        CheckPolicyComplianceResult checkPoliciesResult = service.checkPolicyCompliance(orgToken, product, productVersion, projects, forceCheckAllDependencies);
        boolean hasRejections = checkPoliciesResult.hasRejections();
        if (hasRejections) {
            if (senderConfiguration.isForceUpdate()) {
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

            File outputDir = new File(whiteSourceFolderPath);
            report.generate(outputDir, false);
            report.generateJson(outputDir);
            logger.info("Policies report generated successfully");
        } catch (IOException e) {
            logger.error("Error generating check policies report: " + e.getMessage(), e);
        }

        return policyCompliance;
    }

    private String update(WhitesourceService service, String orgToken, UpdateType updateType, String requesterEmail, String product, String productVersion,
                          Collection<AgentProjectInfo> projects) throws WssServiceException {
        logger.info("Sending Update");
        UpdateInventoryResult updateResult = service.update(orgToken, requesterEmail, updateType, product, productVersion, projects);
        return logResult(updateResult);
    }

    private String offlineUpdate(WhitesourceService service, String orgToken, String requesterEmail, String product, String productVersion,
                                 Collection<AgentProjectInfo> projects, String whiteSourceFolderPath) {
        String resultInfo = "";
        logger.info("Generating offline update request");

        boolean zip = offlineConfiguration.isZip();
        boolean prettyJson = offlineConfiguration.isPrettyJson();

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(orgToken, product, productVersion, projects);

        updateRequest.setRequesterEmail(requesterEmail);
        try {
            OfflineUpdateRequest offlineUpdateRequest = new OfflineUpdateRequest(updateRequest);

            UpdateType updateTypeFinal;

            // if the update type was forced by command or config -> set it
            if (StringUtils.isNotBlank(senderConfiguration.getUpdateTypeValue())) {
                String updateTypeValue = senderConfiguration.getUpdateTypeValue();
                try {
                    updateTypeFinal = UpdateType.valueOf(updateTypeValue);
                } catch (Exception e) {
                    logger.info("Invalid value {} for updateType, defaulting to {}", updateTypeValue, UpdateType.OVERRIDE);
                    updateTypeFinal = UpdateType.OVERRIDE;
                }
            } else {
                // Otherwise use the parameter in the file
                updateTypeFinal = updateRequest.getUpdateType();
            }

            logger.info("UpdateType offline set to {} ", updateTypeFinal);
            updateRequest.setUpdateType(updateTypeFinal);

            File outputDir = new File(whiteSourceFolderPath);
            File file = offlineUpdateRequest.generate(outputDir, zip, prettyJson);

            resultInfo = "Offline request generated successfully at " + file.getPath();
            logger.info(resultInfo);
        } catch (IOException e) {
            resultInfo = "Error generating offline update request: " + e.getMessage();
            logger.error(resultInfo);
        } finally {
            if (service != null) {
                service.shutdown();
            }
        }
        return resultInfo;
    }

    private String logResult(UpdateInventoryResult updateResult) {
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
        return resultLogMsg.toString();
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
        if (StringUtils.isNotBlank(val)) {
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