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
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;
import org.whitesource.agent.report.OfflineUpdateRequest;
import org.whitesource.agent.report.PolicyCheckReport;
import org.whitesource.archiveReaders.ArchiveExtractor;
import org.whitesource.scm.ScmConnector;

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
    private static final String EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX = ",";
    private static final List<String> progressAnimation = Arrays.asList("|", "/", "-", "\\");
    private static final int ANIMATION_FRAMES = progressAnimation.size();
    public static final String GLOB_PATTERN_PREFIX = "**/*";
    public static final String DEFAULT_ARCHIVE_DEPTH = "0";
    private static int animationIndex = 0;

    /* --- Members --- */

    private final Properties config;
    private final List<String> dependencyDirs;

    /* --- Constructors --- */

    public WhitesourceFSAgent(Properties config, List<String> dependencyDirs) {
        this.config = config;
        this.dependencyDirs = dependencyDirs;
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
        String orgToken = config.getProperty(Constants.ORG_TOKEN_PROPERTY_KEY);
        String productVersion = null;
        String product = config.getProperty(Constants.PRODUCT_TOKEN_PROPERTY_KEY);
        if (StringUtils.isBlank(product)) {
            product = config.getProperty(Constants.PRODUCT_NAME_PROPERTY_KEY);
            productVersion = config.getProperty(Constants.PRODUCT_VERSION_PROPERTY_KEY);
        }

        // send request
        logger.info("Initializing WhiteSource Client");
        WhitesourceService service = createService();
        List<AgentProjectInfo> projects = Arrays.asList(projectInfo);
        if (getBooleanProperty(OFFLINE_PROPERTY_KEY, false)) {
            offlineUpdate(service, orgToken, product, productVersion, projects);
        } else {
            try {
                boolean sendUpdate = true;
                if (getBooleanProperty(CHECK_POLICIES_PROPERTY_KEY, false)) {
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
        String serviceUrl = config.getProperty(ClientConstants.SERVICE_URL_KEYWORD, ClientConstants.DEFAULT_SERVICE_URL);
        logger.info("Service URL is " + serviceUrl);
        final WhitesourceService service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, serviceUrl);
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

        boolean zip = getBooleanProperty(OFFLINE_ZIP_PROPERTY_KEY, false);
        boolean prettyJson = getBooleanProperty(OFFLINE_PRETTY_JSON_KEY, false);

        // generate offline request
        UpdateInventoryRequest updateRequest = service.offlineUpdate(orgToken, product, productVersion, projects);
        try {
            OfflineUpdateRequest offlineUpdateRequest = new OfflineUpdateRequest(updateRequest);
            File outputDir = new File(".");
            File file = offlineUpdateRequest.generate(outputDir, zip, prettyJson);
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
        List<String> scannerBaseDirs = dependencyDirs;

        // create scm connector
        String scmType = config.getProperty(SCM_TYPE_PROPERTY_KEY);
        String url = config.getProperty(SCM_URL_PROPERTY_KEY);
        String username = config.getProperty(SCM_USER_PROPERTY_KEY);
        String password = config.getProperty(SCM_PASS_PROPERTY_KEY);
        String branch = config.getProperty(SCM_BRANCH_PROPERTY_KEY);
        String tag = config.getProperty(SCM_TAG_PROPERTY_KEY);
        String privateKey = config.getProperty(SCM_PPK_PROPERTY_KEY);
        ScmConnector scmConnector = ScmConnector.create(scmType, url, privateKey, username, password, branch, tag);
        if (scmConnector != null) {
            logger.info("Connecting to SCM");
            scannerBaseDirs.clear();
            scannerBaseDirs.add(scmConnector.cloneRepository().getPath());
        }

        // read properties
        final String[] includes = config.getProperty(Constants.INCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String[] excludes = config.getProperty(Constants.EXCLUDES_PATTERN_PROPERTY_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        final String globCaseSensitive = config.getProperty(Constants.CASE_SENSITIVE_GLOB_PROPERTY_KEY);
        final int archiveExtractionDepth = Integer.parseInt(config.getProperty(Constants.ARCHIVE_EXTRACTION_DEPTH_KEY, DEFAULT_ARCHIVE_DEPTH));

        // validate parameters
        validateParams(archiveExtractionDepth, includes);

        // scan directories
        int totalFiles = 0;
        Map<File, Collection<String>> fileMap = new HashMap<File, Collection<String>>();

        // go over all base directories, look for archives
        Map<String, String> archiveToBaseDirMap = new HashMap<String, String>();
        ArchiveExtractor archiveExtractor = null;
        if (archiveExtractionDepth > 0) {
            final String[] archiveIncludes = config.getProperty(Constants.ARCHIVE_INCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
            final String[] archiveExcludes = config.getProperty(Constants.ARCHIVE_EXCLUDES_PATTERN_KEY, "").split(INCLUDES_EXCLUDES_SEPARATOR_REGEX);
            archiveExtractor = new ArchiveExtractor(archiveIncludes, archiveExcludes);
            for (String scannerBaseDir : scannerBaseDirs) {
                archiveToBaseDirMap.put(archiveExtractor.extractArchives(scannerBaseDir , archiveExtractionDepth), scannerBaseDir);
            }
            scannerBaseDirs.addAll(archiveToBaseDirMap.keySet());
        }

        for (String scannerBaseDir : scannerBaseDirs) {
            File file = new File(scannerBaseDir);
            if (file.exists()) {
                if (file.isDirectory()) {
                    logger.info("Scanning Directory {} for Matching Files (may take a few minutes)", scannerBaseDir);
                    DirectoryScanner scanner = new DirectoryScanner();
                    scanner.setBasedir(scannerBaseDir);
                    scanner.setIncludes(includes);
                    scanner.setExcludes(excludes);
                    scanner.setFollowSymlinks(getBooleanProperty(FOLLOW_SYMBOLIC_LINKS, true));
                    if (StringUtils.isNotBlank(globCaseSensitive)) {
                        if (globCaseSensitive.equalsIgnoreCase("true") || globCaseSensitive.equalsIgnoreCase("y")) {
                            scanner.setCaseSensitive(true);
                        } else if (globCaseSensitive.equalsIgnoreCase("false") || globCaseSensitive.equalsIgnoreCase("n")) {
                            scanner.setCaseSensitive(false);
                        } else {
                            logger.error("Bad {}. Received {}, required true/false or y/n", Constants.CASE_SENSITIVE_GLOB_PROPERTY_KEY, globCaseSensitive);
                            scmConnector.deleteCloneDirectory();
                            System.exit(-1); // TODO this is within a try frame. Throw an exception instead
                        }
                    }
                    scanner.scan();
                    File basedir = scanner.getBasedir();
                    String[] fileNames = scanner.getIncludedFiles();
                    fileMap.put(basedir, Arrays.asList(fileNames));
                    totalFiles += fileNames.length;
                } else {
                    // handle file
                    fileMap.put(file.getParentFile(), Arrays.asList(file.getName()));
                    totalFiles++;
                }
            } else {
                logger.info(MessageFormat.format("File {0} doesn't exist", scannerBaseDir));
            }
        }
        logger.info(MessageFormat.format("Total Files Found: {0}", totalFiles));

        // get excluded copyrights
        final String excludedCopyrightsValue = config.getProperty(Constants.EXCLUDED_COPYRIGHT_KEY, "");
        Collection<String> excludedCopyrights = new ArrayList<String>(Arrays.asList(excludedCopyrightsValue.split(EXCLUDED_COPYRIGHTS_SEPARATOR_REGEX)));
        excludedCopyrights.remove("");

        // check scan partial sha1s (false by default)
        boolean partialSha1Match = getBooleanProperty(PARTIAL_SHA1_MATCH_KEY, false);

        DependencyInfoFactory factory = new DependencyInfoFactory(excludedCopyrights, partialSha1Match);

        // create dependency infos from files
        logger.info("Starting Analysis");
        List<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        displayProgress(0, totalFiles);
        int index = 1;
        for (Map.Entry<File, Collection<String>> entry : fileMap.entrySet()) {
            for (String fileName : entry.getValue()) {
                DependencyInfo originalDependencyInfo = factory.createDependencyInfo(entry.getKey(), fileName);
                if (originalDependencyInfo != null) {
                    if (scmConnector != null) {
                        // no need to send system path for file from scm repository
                        originalDependencyInfo.setSystemPath(null);
                    }
                    dependencyInfos.add(originalDependencyInfo);
                }

                // print progress
                displayProgress(index, totalFiles);
                index++;
            }
        }

        // replace temp folder name with base dir
        for (DependencyInfo dependencyInfo : dependencyInfos) {
            String systemPath = dependencyInfo.getSystemPath();
            for (String key : archiveToBaseDirMap.keySet()){
                if (dependencyInfo.getSystemPath().contains(key)){
                    dependencyInfo.setSystemPath(systemPath.replace(key, archiveToBaseDirMap.get(key)));
                    break;
                }
            }
        }

        // delete all archive temp folders
        if (archiveExtractor != null) {
            archiveExtractor.deleteArchiveDirectory();
        }

        // delete scm clone directory
        if (scmConnector != null) {
            scmConnector.deleteCloneDirectory();
        }
        logger.info("Finished Analyzing Files");
        return dependencyInfos;
    }


    private boolean getBooleanProperty(String propertyKey, boolean defaultValue) {
        boolean property = defaultValue;
        String propertyValue = config.getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Boolean.valueOf(propertyValue);
        }
        return property;
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
            sb.append("#");
        }
        for (int i = progressionBlocks; i < 33; i++) {
            sb.append(" ");
        }
        sb.append("] {0}% - {1} of {2} files\r");
        System.out.print(MessageFormat.format(sb.toString(), (int) percentage, index, totalFiles));

        if (index == totalFiles) {
            // clear progress animation
            System.out.print("                                                                                  \r");
        }
    }

    private void validateParams(int archiveExtractionDepth, String[] includes) {
        boolean isShutDown = false;
        if (archiveExtractionDepth < 0 || archiveExtractionDepth > 5) {
            logger.warn("Error: archiveExtractionDepth value should be greater than 0 and less than 4");
            isShutDown = true;
        }
        if (includes.length < 1 ||  StringUtils.isBlank(includes[0]) ){
            logger.warn("Error: includes parameter must have at list one scanning pattern");
            isShutDown = true;
        }
        if (isShutDown == true){
            logger.warn("Exiting");
            System.exit(1);
        }
    }
}
