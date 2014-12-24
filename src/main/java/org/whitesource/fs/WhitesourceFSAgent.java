/**
 * Copyright (C) 2014 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.dispatch.CheckPoliciesResult;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.OfflineUpdateRequest;
import org.whitesource.agent.report.PolicyCheckReport;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import static org.whitesource.fs.Constants.*;

/**
 * @author Itai Marko
 * @author tom.shapira
 */
public class WhitesourceFSAgent {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(WhitesourceFSAgent.class);
    private static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";

    /* --- Members --- */

    private final String dependencyDir;
    private final Properties config;

    /* --- Constructors --- */

    public WhitesourceFSAgent(String dependencyDir, Properties config) {
        this.dependencyDir = dependencyDir;
        this.config = config;
    }

    /* --- Public methods --- */

    public void sendRequest() {
        AgentProjectInfo projectInfo = createProjectInfo();
        if (projectInfo.getDependencies().isEmpty()) {
            logger.info("Exiting, nothing to update");
        } else {
            sendRequest(projectInfo);
        }
    }

    private void sendRequest(AgentProjectInfo projectInfo) {
        boolean checkPolicies = false;
        String checkPoliciesValue = config.getProperty(CHECK_POLICIES_PROPERTY_KEY);
        if (StringUtils.isNotBlank(checkPoliciesValue)) {
            checkPolicies = Boolean.valueOf(checkPoliciesValue);
        }

        String orgToken = config.getProperty(Constants.ORG_TOKEN_PROPERTY_KEY);
        String productVersion = null;
        String product = config.getProperty(Constants.PRODUCT_TOKEN_PROPERTY_KEY);
        if (StringUtils.isBlank(product)) {
            product = config.getProperty(Constants.PRODUCT_NAME_PROPERTY_KEY);
            productVersion = config.getProperty(Constants.PRODUCT_VERSION_PROPERTY_KEY);
        }

        boolean offline = false;
        String offlineValue = config.getProperty(OFFLINE_PROPERTY_KEY);
        if (StringUtils.isNotBlank(offlineValue)) {
            offline = Boolean.valueOf(offlineValue);
        }

        // send request
        WhitesourceService service = createService();
        List<AgentProjectInfo> projects = Arrays.asList(projectInfo);
        if (offline) {
            offlineUpdate(service, orgToken, product, productVersion, projects);
        } else {
            try {
                boolean sendUpdate = true;
                if (checkPolicies) {
                    boolean policyCompliance = checkPolicies(service, orgToken, product, productVersion, projects);
                    sendUpdate = policyCompliance;
                }

                if (sendUpdate) {
                    update(service, orgToken, product, productVersion, projects);
                }
            } catch (WssServiceException e) {
                logger.error("Failed to send request to WhiteSource server: " + e.getMessage(), e);
            } finally {
                if (service != null) {
                    service.shutdown();
                }
            }
        }
    }

    /* --- Private methods --- */

    private WhitesourceService createService() {
        final WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, null);
        final String proxyHost = config.getProperty(Constants.PROXY_HOST_PROPERTY_KEY);
        if (StringUtils.isNotBlank(proxyHost)) {
            final int proxyPort = Integer.parseInt(config.getProperty(Constants.PROXY_PORT_PROPERTY_KEY));
            final String proxyUser = config.getProperty(Constants.PROXY_USER_PROPERTY_KEY);
            final String proxyPass = config.getProperty(Constants.PROXY_PASS_PROPERTY_KEY);
            service.getClient().setProxy(proxyHost, proxyPort, proxyUser, proxyPass);
        }
        return service;
    }

    private boolean checkPolicies(WhitesourceService service, String orgToken, String product, String productVersion, List<AgentProjectInfo> projects)
            throws WssServiceException {
        boolean policyCompliance = true;

        logger.info("Checking policies");
        CheckPoliciesResult checkPoliciesResult = service.checkPolicies(orgToken, product, productVersion, projects);
        if (checkPoliciesResult.hasRejections()) {
            logger.info("Some dependencies did not conform with open source policies, review report for details");
            logger.info("=== UPDATE ABORTED ===");
            policyCompliance = false;
        } else {
            logger.info("All dependencies conform with open source policies");
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

    private void update(WhitesourceService service, String orgToken, String product, String productVersion, List<AgentProjectInfo> projects)
            throws WssServiceException {
        logger.info("Sending Update");
        UpdateInventoryResult updateResult = service.update(orgToken, product, productVersion, projects);
        logResult(updateResult);
    }

    private void offlineUpdate(WhitesourceService service, String orgToken, String product, String productVersion,
                               List<AgentProjectInfo> projects) {
        logger.info("Generating offline update request");

        // zip?
        String zipValue = config.getProperty(OFFLINE_ZIP_PROPERTY_KEY);
        boolean zip = false;
        if (StringUtils.isNotBlank(zipValue)) {
            zip = Boolean.valueOf(zipValue);
        }

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(orgToken, product, productVersion, projects);
        try {
            OfflineUpdateRequest offlineUpdateRequest = new OfflineUpdateRequest(updateRequest);
            File outputDir = new File(".");
            File file = offlineUpdateRequest.generate(outputDir, zip);
            logger.info("Offline request generated successfully at {}", file.getPath());
        } catch (IOException e) {
            logger.error("Error generating offline update request: " + e.getMessage(), e);
        } finally {
            if (service != null) {
                service.shutdown();
            }
        }
    }

    private AgentProjectInfo createProjectInfo() {
        AgentProjectInfo projectInfo = new AgentProjectInfo();

        // use token or name + version
        String projectToken = config.getProperty(Constants.PROJECT_TOKEN_PROPERTY_KEY);
        if (StringUtils.isNotBlank(projectToken)) {
            projectInfo.setProjectToken(projectToken);
        } else {
            String projectName = config.getProperty(Constants.PROJECT_NAME_PROPERTY_KEY);
            String projectVersion = config.getProperty(Constants.PROJECT_VERSION_PROPERTY_KEY);
            projectInfo.setCoordinates(new Coordinates(null, projectName, projectVersion));
        }

        projectInfo.setDependencies(getDependencyInfos());
        return projectInfo;
    }

    private List<DependencyInfo> getDependencyInfos() {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(dependencyDir);
        String includes = config.getProperty(Constants.INCLUDES_PATTERN_PROPERTY_KEY, "**/*");
        scanner.setIncludes(includes.split(INCLUDES_EXCLUDES_SEPARATOR_REGEX));
        String excludes = config.getProperty(Constants.EXCLUDES_PATTERN_PROPERTY_KEY, "");
        scanner.setExcludes(excludes.split(INCLUDES_EXCLUDES_SEPARATOR_REGEX));
        final String globCaseSensitive = config.getProperty(Constants.CASE_SENSITIVE_GLOB_PROPERTY_KEY);
        if (StringUtils.isNotBlank(globCaseSensitive)) {
            if (globCaseSensitive.equalsIgnoreCase("true") || globCaseSensitive.equalsIgnoreCase("y")) {
                scanner.setCaseSensitive(true);
            } else if (globCaseSensitive.equalsIgnoreCase("false") || globCaseSensitive.equalsIgnoreCase("n")) {
                scanner.setCaseSensitive(false);
            } else {
                logger.error("Bad {}. Received {}, required true/false or y/n", Constants.CASE_SENSITIVE_GLOB_PROPERTY_KEY, globCaseSensitive);
                System.exit(-1); // TODO this is within a try frame. Throw an exception instead
            }
        }
        scanner.scan();
        String[] fileNames = scanner.getIncludedFiles();
        File basedir = scanner.getBasedir();

        DependencyInfoFactory factory = new DependencyInfoFactory();
        List<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        for (String fileName : fileNames) {
            DependencyInfo dependencyInfo = factory.createDependencyInfo(basedir, fileName);
            if (dependencyInfo != null) {
                dependencyInfos.add(dependencyInfo);
            }
        }
        logger.info(MessageFormat.format("Total Files Found: {0}", dependencyInfos.size()));
        return dependencyInfos;
    }

    private void logResult(UpdateInventoryResult updateResult) {
        StringBuilder resultLogMsg = new StringBuilder("Inventory update results for ").append(updateResult.getOrganization()).append("\n");

        // newly created projects
        Collection<String> createdProjects = updateResult.getCreatedProjects();
        if (createdProjects.isEmpty()) {
            resultLogMsg.append("No new projects found.").append("\n");
        } else {
            resultLogMsg.append("Newly created projects:").append("\n");
            for (String projectName : createdProjects) {
                resultLogMsg.append(projectName).append("\n");
            }
        }

        // updated projects
        Collection<String> updatedProjects = updateResult.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
            resultLogMsg.append("No projects were updated.").append("\n");
        } else {
            resultLogMsg.append("Updated projects:").append("\n");
            for (String projectName : updatedProjects) {
                resultLogMsg.append(projectName).append("\n");
            }
        }

        logger.info(resultLogMsg.toString());
    }
}
