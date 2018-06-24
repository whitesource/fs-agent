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
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.dispatch.UpdateType;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.OfflineUpdateRequest;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.agent.utils.Pair;
import org.whitesource.contracts.PluginInfo;
import org.whitesource.fs.ProjectsDetails;
import org.whitesource.fs.StatusCode;
import org.whitesource.fs.configuration.OfflineConfiguration;
import org.whitesource.fs.configuration.RequestConfiguration;
import org.whitesource.fs.configuration.SenderConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Class for sending projects for all WhiteSource command line agents.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class ProjectsSender {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ProjectsSender.class);
    public static final String PROJECT_URL_PREFIX = "Wss/WSS.html#!project;id=";

    /* --- Members --- */

    private final SenderConfiguration senderConfig;
    private final OfflineConfiguration offlineConfig;
    private final RequestConfiguration requestConfig;
    private final PluginInfo pluginInfo;
    protected StatusCode prepStepStatusCode = StatusCode.SUCCESS;

    /* --- Constructors --- */

    public ProjectsSender(SenderConfiguration senderConfig, OfflineConfiguration offlineConfig, RequestConfiguration requestConfig, PluginInfo pluginInfo) {
        this.senderConfig = senderConfig;
        this.offlineConfig = offlineConfig;
        this.requestConfig = requestConfig;
        this.pluginInfo = pluginInfo;
    }

    /* --- Public methods --- */

    public Pair<String, StatusCode> sendRequest(ProjectsDetails projectsDetails) {
        // send request
        logger.info("Initializing WhiteSource Client");
        Collection<AgentProjectInfo> projects = projectsDetails.getProjects();
        WhitesourceService service = createService();
        String resultInfo = Constants.EMPTY_STRING;
        if (offlineConfig.isEnabled()) {
            resultInfo = offlineUpdate(service, projects);
            return new Pair<>(resultInfo, this.prepStepStatusCode);
        } else {
            // update type
            UpdateType updateType = UpdateType.OVERRIDE;
            String updateTypeValue = senderConfig.getUpdateTypeValue();
            try {
                updateType = UpdateType.valueOf(updateTypeValue);
            } catch (Exception e) {
                logger.info("Invalid value {} for updateType, defaulting to {}", updateTypeValue, UpdateType.OVERRIDE);
            }
            logger.info("UpdateType set to {} ", updateTypeValue);

            checkDependenciesUpbound(projects);
            StatusCode statusCode = StatusCode.SUCCESS;

            if (senderConfig.isEnableImpactAnalysis()) {
                runViaAnalysis(projectsDetails, service);
            } else if (!senderConfig.isEnableImpactAnalysis()) {
                //todo return logs when needed would be enabled for all WSE-342
            }

            int retries = senderConfig.getConnectionRetries();
            while (retries-- > -1) {
                try {
                    statusCode = checkPolicies(service, projects);
                    if (statusCode == StatusCode.SUCCESS) {
                        resultInfo = update(service, projects);
                    }
                    break;
                } catch (WssServiceException e) {
                    if (e.getCause() != null &&
                            e.getCause().getClass().getCanonicalName().substring(0,
                                    e.getCause().getClass().getCanonicalName().lastIndexOf(Constants.DOT)).equals(Constants.JAVA_NETWORKING)) {
                        statusCode = StatusCode.CONNECTION_FAILURE;
                        logger.error("Trying " + (retries + 1) + " more time" + (retries != 0 ? "s" : Constants.EMPTY_STRING));
                    } else {
                        statusCode = StatusCode.SERVER_FAILURE;
                        retries = -1;
                    }
                    resultInfo = "Failed to send request to WhiteSource server: " + e.getMessage();
                    logger.error(resultInfo, e.getMessage());
                    logger.debug(resultInfo, e);
                    if (retries > -1) {
                        try {
                            Thread.sleep(senderConfig.getConnectionRetriesIntervals());
                        } catch (InterruptedException e1) {
                            logger.error("Failed to sleep while retrying to connect to server " + e1.getMessage(), e1);
                        }
                    }

                    String requestToken = e.getRequestToken();
                    if (StringUtils.isNotBlank(requestToken)) {
                        resultInfo += Constants.NEW_LINE + "Support token: " + requestToken;
                        logger.info("Support token: {}", requestToken);
                    }
                }
            }
            if (service != null) {
                service.shutdown();
            }
            if (statusCode == StatusCode.SUCCESS) {
                return new Pair<>(resultInfo, this.prepStepStatusCode);
            }
            return new Pair<>(resultInfo, statusCode);
        }
    }

    private void runViaAnalysis(ProjectsDetails projectsDetails, WhitesourceService service) {
        try {
            Class<?> vulnerabilitiesAnalysisClass = Class.forName("whitesource.analysis.vulnerabilities.VulnerabilitiesAnalysis");
            Method getAnalysisMethod
                    = vulnerabilitiesAnalysisClass.getMethod("getAnalysis", String.class, int.class);
            Object vulnerabilitiesAnalysis = null;
            for (AgentProjectInfo project : projectsDetails.getProjectToViaComponents().keySet()) {
                // check language for scan according to user file
                LinkedList<ViaComponents> viaComponentsList = projectsDetails.getProjectToViaComponents().get(project);
                for (ViaComponents viaComponents : viaComponentsList) {
                    logger.info("Starting VIA impact analysis");
                    String appPath = viaComponents.getAppPath();
                    ViaLanguage language = viaComponents.getLanguage();
                    try {
                        vulnerabilitiesAnalysis = getAnalysisMethod.invoke(null, language.toString(), requestConfig.getViaAnalysisLevel());
                        // set app path for java script
                        if (language.equals(ViaLanguage.JAVA_SCRIPT)) {
                            int lastIndex = appPath.lastIndexOf(Constants.BACK_SLASH) != -1 ? appPath.lastIndexOf(Constants.BACK_SLASH) : appPath.lastIndexOf(Constants.FORWARD_SLASH);
                            appPath = appPath.substring(0, lastIndex);
                        }
                        if (vulnerabilitiesAnalysis != null) {
                            AgentProjectInfo projectToServer = new AgentProjectInfo();
                            projectToServer.setDependencies(viaComponents.getDependencies());
                            projectToServer.setProjectSetupDescription(project.getProjectSetupDescription());
                            projectToServer.setCoordinates(project.getCoordinates());
                            projectToServer.setProjectToken(project.getProjectToken());
                            projectToServer.setProjectSetupStatus(project.getProjectSetupStatus());
                            projectToServer.setParentCoordinates(project.getParentCoordinates());
                            Class<?> fsaAgentServerClass = Class.forName("whitesource.analysis.server.FSAgentServer");
                            Object server = fsaAgentServerClass.getConstructor(AgentProjectInfo.class, WhitesourceService.class, String.class, String.class).newInstance(
                                    projectToServer, service, requestConfig.getApiToken(), requestConfig.getUserKey());
                            logger.info("Starting analysis for: {}", appPath);
                            Class<?> serverClass = Class.forName("whitesource.analysis.server.Server");
                            Method runAnalysis = vulnerabilitiesAnalysisClass.getDeclaredMethod("runAnalysis", serverClass, String.class, Collection.class, Boolean.class);
                            runAnalysis.invoke(vulnerabilitiesAnalysis, server, appPath, project.getDependencies(), Boolean.valueOf(requestConfig.getViaDebug()));
                            logger.info("Got impact analysis result from server");
                        }
                    } catch (InvocationTargetException e) {
                        logger.error("Failed to run VIA impact analysis {}", e.getTargetException().getMessage());
                    } catch (Exception e) {
                        logger.error("Failed to run VIA impact analysis {}", e.getMessage());
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            logger.error("Failed to run VIA impact analysis, couldn't find method {}", e.getMessage());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to run VIA impact analysis, couldn't find class {}", e.getMessage());
        }
    }

    private void checkDependenciesUpbound(Collection<AgentProjectInfo> projects) {
        int numberOfDependencies = projects.stream().map(x -> x.getDependencies()).mapToInt(x -> x.size()).sum();
        if (numberOfDependencies > Constants.MAX_NUMBER_OF_DEPENDENCIES) {
            logger.warn("Number of dependencies: {} exceeded the maximum supported: {}", numberOfDependencies, Constants.MAX_NUMBER_OF_DEPENDENCIES);
        }
    }

    private WhitesourceService createService() {
        logger.info("Service URL is " + senderConfig.getServiceUrl());
        boolean setProxy = false;
        if (StringUtils.isNotBlank(senderConfig.getProxyHost()) || !offlineConfig.isEnabled()) {
            setProxy = true;
        }
        int connectionTimeoutMinutes = senderConfig.getConnectionTimeOut();
        final WhitesourceService service = new WhitesourceService(pluginInfo.getAgentType(), pluginInfo.getAgentVersion(), pluginInfo.getPluginVersion(),
                senderConfig.getServiceUrl(), setProxy, connectionTimeoutMinutes, senderConfig.isIgnoreCertificateCheck());
        if (StringUtils.isNotBlank(senderConfig.getProxyHost())) {
            service.getClient().setProxy(senderConfig.getProxyHost(), senderConfig.getProxyPort(), senderConfig.getProxyUser(), senderConfig.getProxyPassword());
        }
        return service;
    }

    private StatusCode checkPolicies(WhitesourceService service, Collection<AgentProjectInfo> projects) throws WssServiceException {
        boolean policyCompliance = true;
        if (senderConfig.isCheckPolicies()) {
            logger.info("Checking policies");
            CheckPolicyComplianceResult checkPoliciesResult = service.checkPolicyCompliance(requestConfig.getApiToken(), requestConfig.getProductNameOrToken(),
                    requestConfig.getProductVersion(), projects, senderConfig.isForceCheckAllDependencies(), requestConfig.getUserKey(), requestConfig.getRequesterEmail());
            if (checkPoliciesResult.hasRejections()) {
                if (senderConfig.isForceUpdate()) {
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

            String requestToken = checkPoliciesResult.getRequestToken();
            if (StringUtils.isNotBlank(requestToken)) {
                logger.info("Check Policies Support Token: {}", requestToken);
            }

            try {
                // generate report
                PolicyCheckReport report = new PolicyCheckReport(checkPoliciesResult);

                File outputDir = new File(offlineConfig.getWhiteSourceFolderPath());
                report.generate(outputDir, false);
                report.generateJson(outputDir);
                logger.info("Policies report generated successfully");
            } catch (IOException e) {
                logger.error("Error generating check policies report: " + e.getMessage(), e);
            }
        }
        return policyCompliance ? StatusCode.SUCCESS : StatusCode.POLICY_VIOLATION;
    }

    private String update(WhitesourceService service, Collection<AgentProjectInfo> projects) throws WssServiceException {
        logger.info("Sending Update");
        UpdateInventoryResult updateResult = service.update(requestConfig.getApiToken(), requestConfig.getRequesterEmail(), UpdateType.valueOf(senderConfig.getUpdateTypeValue()),
                requestConfig.getProductNameOrToken(), requestConfig.getProductVersion(), projects, requestConfig.getUserKey());
        String resultInfo = logResult(updateResult);

        // remove line separators
        resultInfo = resultInfo.replace(System.lineSeparator(), Constants.EMPTY_STRING);
        return resultInfo;
    }

    private String offlineUpdate(WhitesourceService service, Collection<AgentProjectInfo> projects) {
        String resultInfo = Constants.EMPTY_STRING;
        logger.info("Generating offline update request");

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(requestConfig.getApiToken(), requestConfig.getProductNameOrToken(),
                requestConfig.getProductVersion(), projects, requestConfig.getUserKey());

        updateRequest.setRequesterEmail(requestConfig.getRequesterEmail());
        try {
            OfflineUpdateRequest offlineUpdateRequest = new OfflineUpdateRequest(updateRequest);

            UpdateType updateTypeFinal;

            // if the update type was forced by command or config -> set it
            if (StringUtils.isNotBlank(senderConfig.getUpdateTypeValue())) {
                try {
                    updateTypeFinal = UpdateType.valueOf(senderConfig.getUpdateTypeValue());
                } catch (Exception e) {
                    logger.info("Invalid value {} for updateType, defaulting to {}", senderConfig.getUpdateTypeValue(), UpdateType.OVERRIDE);
                    updateTypeFinal = UpdateType.OVERRIDE;
                }
            } else {
                // Otherwise use the parameter in the file
                updateTypeFinal = updateRequest.getUpdateType();
            }

            logger.info("UpdateType offline set to {} ", updateTypeFinal);
            updateRequest.setUpdateType(updateTypeFinal);

            File outputDir = new File(offlineConfig.getWhiteSourceFolderPath()).getAbsoluteFile();
            if (!outputDir.exists() && !outputDir.mkdir()) {
                throw new IOException("Unable to make output directory: " + outputDir);
            }

            File file = offlineUpdateRequest.generate(outputDir, offlineConfig.isZip(), offlineConfig.isPrettyJson());

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
        StringBuilder resultLogMsg = new StringBuilder("Inventory update results for ").append(updateResult.getOrganization()).append(Constants.NEW_LINE);
        logger.info("Inventory update results for {}", updateResult.getOrganization());
        // newly created projects
        Collection<String> createdProjects = updateResult.getCreatedProjects();
        if (createdProjects.isEmpty()) {
            logger.info("No new projects found.");
            resultLogMsg.append("No new projects found.").append(Constants.NEW_LINE);
        } else {
            logger.info("Newly created projects:");
            resultLogMsg.append("Newly created projects:").append(Constants.NEW_LINE);
            for (String projectName : createdProjects) {
                logger.info("# {}", projectName);
                resultLogMsg.append(projectName).append(Constants.NEW_LINE);
            }
        }

        // updated projects
        Collection<String> updatedProjects = updateResult.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
            logger.info("No projects were updated.");
            resultLogMsg.append("No projects were updated.").append(Constants.NEW_LINE);
        } else {
            logger.info("Updated projects:");
            resultLogMsg.append("Updated projects:").append(Constants.NEW_LINE);
            for (String projectName : updatedProjects) {
                logger.info("# {}", projectName);
                resultLogMsg.append(projectName).append(Constants.NEW_LINE);
            }
        }
        // reading projects' URLs
        HashMap<String, Integer> projectsUrls = updateResult.getProjectNamesToIds();
        if (projectsUrls != null && !projectsUrls.isEmpty()) {
            for (String projectName : projectsUrls.keySet()) {
                String appUrl = senderConfig.getServiceUrl().replace("agent", Constants.EMPTY_STRING);
                String projectsUrl = appUrl + PROJECT_URL_PREFIX + projectsUrls.get(projectName);
                logger.info("Project name: {}, URL: {}", projectName, projectsUrl);
                resultLogMsg.append(Constants.NEW_LINE).append("Project name: ").append(projectName).append(", project URL:").append(projectsUrl);
            }
        }

        // support token
        String requestToken = updateResult.getRequestToken();
        if (StringUtils.isNotBlank(requestToken)) {
            logger.info("Support Token: {}", requestToken);
            resultLogMsg.append(Constants.NEW_LINE).append("Support Token: ").append(requestToken);
        }
        return resultLogMsg.toString();
    }
}