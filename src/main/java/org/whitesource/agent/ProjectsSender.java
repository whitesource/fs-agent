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
import org.whitesource.agent.api.dispatch.*;
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
import whitesource.analysis.server.FSAgentServer;
import whitesource.analysis.server.Server;
import whitesource.analysis.vulnerabilities.VulnerabilitiesAnalysis;
import whitesource.via.api.vulnerability.update.GlobalVulnerabilityAnalysisResult;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

/**
 * Class for sending projects for all WhiteSource command line agents.
 *
 * @author Itai Marko
 * @author tom.shapira
 * @author anna.rozin
 */
public class ProjectsSender {

    private final Logger logger = LoggerFactory.getLogger(ProjectsSender.class);

    /* --- Static members --- */

    public static final String PROJECT_URL_PREFIX = "Wss/WSS.html#!project;id=";
    private static final String NEW_LINE = System.lineSeparator();
    private static final String DOT = ".";
    private static final String JAVA_NETWORKING = "java.net";
    private static final int MAX_NUMBER_OF_DEPENDENCIES = 1000000;
    public static final String JAVA = "java";
    public static final String JAVA_SCRIPT = "javascript";
    public static final String BACK_SLASH = "\\";
    public static final String FORWARD_SLASH = "/";

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
        String resultInfo = "";
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

//            // TODO remove projects.size() == 1 when via will scan more than one project
            if (senderConfig.isEnableImpactAnalysis() && projects.size() == 1) {
                runViaAnalysis(projectsDetails, service);
            }  else if (!senderConfig.isEnableImpactAnalysis()) {
                logger.info("Impact analysis won't run, via is not enabled");
            }

            int retries = senderConfig.getConnectionRetries();
            while (retries-- > -1) {
                try {
                    statusCode = checkPolicies(service, projects);
                    if (statusCode == StatusCode.SUCCESS) {
                        resultInfo = update(service, projects);
                    }
                    retries = -1;
                } catch (WssServiceException e) {
                    if (e.getCause() != null &&
                            e.getCause().getClass().getCanonicalName().substring(0, e.getCause().getClass().getCanonicalName().lastIndexOf(DOT)).equals(JAVA_NETWORKING)) {
                        statusCode = StatusCode.CONNECTION_FAILURE;
                    } else {
                        statusCode = StatusCode.SERVER_FAILURE;
                    }
                    resultInfo = "Failed to send request to WhiteSource server: " + e.getMessage();
                    logger.error(resultInfo, e);
                    logger.error("Trying " + (retries + 1) + " more time" + (retries != 0 ? "s" : ""));
                    if (retries > -1) {
                        try {
                            Thread.sleep(senderConfig.getConnectionRetriesIntervals());
                        } catch (InterruptedException e1) {
                            logger.error("Failed to sleep while retrying to connect to server " + e1.getMessage(), e1);
                        }
                    }

                    String requestToken = e.getRequestToken();
                    if (StringUtils.isNotBlank(requestToken)) {
                        resultInfo += NEW_LINE + "Support token: " + requestToken;
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

    public ConfigurationResult getUserConfigurationFromServer() {
        logger.info("Initializing WhiteSource Client");
        WhitesourceService service = createService();
        // send request to get configuration from server
        return getConfigurationFromServer(service);
    }

    private ConfigurationResult getConfigurationFromServer(WhitesourceService service) {
        try {
            ConfigurationResult configurationResult = service.getConfiguration(requestConfig.getApiToken(),//, requestConfig.getRequesterEmail(),
                    requestConfig.getProductNameOrToken(), "");//requestConfig.getProjectVersion());
            return  configurationResult;
        } catch (WssServiceException e) {
            logger.warn("Error getting user configuration from server - ", e.getMessage());
        }
        return  null;
    }

    private void runViaAnalysis(ProjectsDetails projectsDetails, WhitesourceService service) {
        //todo comment in via code
        VulnerabilitiesAnalysis vulnerabilitiesAnalysis = null;
        GlobalVulnerabilityAnalysisResult result = null;

        for (AgentProjectInfo project : projectsDetails.getProjectToLanguage().keySet()) {
            Server server = new FSAgentServer(project, service, requestConfig.getApiToken());
            //TODO remove later
//            Server server = new DemoServerProjInfo();
//            server.setdb("c:/Users/AharonAbadi/work/vulnerabilityCleaner/via-visual-studio-integration/examples/via-server/via.db");
            try {
                String appPath = requestConfig.getAppPath();
                // check language for scan according to user file
                logger.info("Starting VIA impact analysis");
                String language = projectsDetails.getProjectToLanguage().get(project);
                vulnerabilitiesAnalysis = VulnerabilitiesAnalysis.getAnalysis(language);
                // set app path for java script
                if (language.equals(JAVA_SCRIPT)) {
                    int lastIndex = appPath.lastIndexOf(BACK_SLASH) != -1 ? appPath.lastIndexOf(BACK_SLASH) : appPath.lastIndexOf(FORWARD_SLASH);
                    appPath = appPath.substring(0, lastIndex);
                }

                if (vulnerabilitiesAnalysis != null) {
                    vulnerabilitiesAnalysis.runAnalysis(server, appPath, project.getDependencies(),
                            Boolean.valueOf(requestConfig.getViaDebug()));
                    logger.info("Got impact analysis result from server");
                }
            } catch (Exception e) {
                logger.error("Failed to run impact analysis {}", e.getMessage());
            }
        }
    }


    private void checkDependenciesUpbound(Collection<AgentProjectInfo> projects) {
        int numberOfDependencies = projects.stream().map(x -> x.getDependencies()).mapToInt(x -> x.size()).sum();
        if (numberOfDependencies > MAX_NUMBER_OF_DEPENDENCIES) {
            logger.warn("Number of dependencies: {} exceeded the maximum supported: {}", numberOfDependencies, MAX_NUMBER_OF_DEPENDENCIES);
        }
    }

    private WhitesourceService createService() {
        String serviceUrl = senderConfig.getServiceUrl();
        logger.info("Service URL is " + serviceUrl);
        boolean setProxy = false;
        if (StringUtils.isNotBlank(senderConfig.getProxyHost()) || !offlineConfig.isEnabled()) {
            setProxy = true;
        }
        int connectionTimeoutMinutes = senderConfig.getConnectionTimeOut();
        final WhitesourceService service = new WhitesourceService(pluginInfo.getAgentType(),pluginInfo.getAgentVersion(),pluginInfo.getPluginVersion(),
                serviceUrl, setProxy, connectionTimeoutMinutes, senderConfig.isIgnoreCertificateCheck());
        if (StringUtils.isNotBlank(senderConfig.getProxyHost())) {
            service.getClient().setProxy(senderConfig.getProxyHost(), senderConfig.getProxyPort(), senderConfig.getProxyUser(), senderConfig.getProxyPassword());
        }
        return service;
    }

//    private WhitesourceService createService(String serviceUrl, String proxyHost, boolean offline, int connectionTimeoutMinutes, String agentType,
//                                             String agentVersion, String pluginVersion, boolean ignoreCertificateCheck, int proxyPort, String proxyUser, String proxyPassword) {
//        logger.info("Service URL is " + serviceUrl);
//        boolean setProxy = false;
//        if (StringUtils.isNotBlank(proxyHost) || !offline) {
//            setProxy = true;
//        }
//        final WhitesourceService service = new WhitesourceService(agentType, agentVersion, pluginVersion, serviceUrl, setProxy,
//                connectionTimeoutMinutes, ignoreCertificateCheck);
//        if (StringUtils.isNotBlank(proxyHost)) {
//            service.getClient().setProxy(proxyHost, proxyPort, proxyUser, proxyPassword);
//        }
//        return service;
//    }

    private StatusCode checkPolicies(WhitesourceService service, Collection<AgentProjectInfo> projects) throws WssServiceException {
        boolean policyCompliance = true;
        if (senderConfig.isCheckPolicies()) {
            logger.info("Checking policies");
            CheckPolicyComplianceResult checkPoliciesResult = service.checkPolicyCompliance(requestConfig.getApiToken(), requestConfig.getProductNameOrToken(), requestConfig.getProductVersion(), projects, senderConfig.isForceCheckAllDependencies());
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

//    private getConfiguration() {
//
//    }

    private String update(WhitesourceService service, Collection<AgentProjectInfo> projects) throws WssServiceException {
        logger.info("Sending Update");
        UpdateInventoryResult updateResult = service.update(requestConfig.getApiToken(), requestConfig.getRequesterEmail(),
                UpdateType.valueOf(senderConfig.getUpdateTypeValue()), requestConfig.getProductNameOrToken(), requestConfig.getProjectVersion(), projects);
        String resultInfo = logResult(updateResult);

        // remove line separators
        resultInfo = resultInfo.replace(System.lineSeparator(), "");
        return resultInfo;
    }

    private String offlineUpdate(WhitesourceService service, Collection<AgentProjectInfo> projects) {
        String resultInfo = "";
        logger.info("Generating offline update request");

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(requestConfig.getApiToken(), requestConfig.getProductNameOrToken(), requestConfig.getProductVersion(), projects);

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
        StringBuilder resultLogMsg = new StringBuilder("Inventory update results for ").append(updateResult.getOrganization()).append(NEW_LINE);

        logger.info("Inventory update results for {}", updateResult.getOrganization());

        // newly created projects
        Collection<String> createdProjects = updateResult.getCreatedProjects();
        if (createdProjects.isEmpty()) {
            logger.info("No new projects found.");
            resultLogMsg.append("No new projects found.").append(NEW_LINE);
        } else {
            logger.info("Newly created projects:");
            resultLogMsg.append("Newly created projects:").append(NEW_LINE);
            for (String projectName : createdProjects) {
                logger.info("# {}", projectName);
                resultLogMsg.append(projectName).append(NEW_LINE);
            }
        }


        // updated projects
        Collection<String> updatedProjects = updateResult.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
            logger.info("No projects were updated.");
            resultLogMsg.append("No projects were updated.").append(NEW_LINE);
        } else {
            logger.info("Updated projects:");
            resultLogMsg.append("Updated projects:").append(NEW_LINE);
            for (String projectName : updatedProjects) {
                logger.info("# {}", projectName);
                resultLogMsg.append(projectName).append(NEW_LINE);
            }
        }

        // reading projects' URLs
        HashMap<String, Integer> projectsUrls = updateResult.getProjectNamesToIds();
        if (projectsUrls != null && !projectsUrls.isEmpty()) {
            for (String projectName : projectsUrls.keySet()) {
                String appUrl = senderConfig.getServiceUrl().replace("agent","");
                String projectsUrl = appUrl + PROJECT_URL_PREFIX + projectsUrls.get(projectName);
                logger.info("Project name: {}, URL: {}",projectName, projectsUrl);
                resultLogMsg.append(NEW_LINE).append("Project name: ").append(projectName).append(", project URL:").append(projectsUrl);
            }
        }

        // support token
        String requestToken = updateResult.getRequestToken();
        if (StringUtils.isNotBlank(requestToken)) {
            logger.info("Support Token: {}", requestToken);
            resultLogMsg.append(NEW_LINE).append("Support Token: ").append(requestToken);
        }
        return resultLogMsg.toString();
    }
}