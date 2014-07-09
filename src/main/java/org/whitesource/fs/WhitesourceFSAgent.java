package org.whitesource.fs;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.ChecksumUtils;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Author: Itai Marko
 */
public class WhitesourceFSAgent {

    private static final Logger logger = LoggerFactory.getLogger(WhitesourceFSAgent.class);
    private static final String INCLUDES_EXCLUDES_SEPARATOR_REGEX = "[,;\\s]+";

    private final String dependencyDir;
    private final Properties config;

    public WhitesourceFSAgent(String dependencyDir, Properties config) {
        this.dependencyDir = dependencyDir;
        this.config = config;
    }

    public void updateWhitesource() {
        WhitesourceService service = null;
        try {
            AgentProjectInfo projectInfo = createProjectInfo();
            service = createWhitesourceService();
            String orgToken = config.getProperty(Constants.ORG_TOKEN_PROPERTY_KEY);

            // product properties
            String productVersion = null;
            String product = config.getProperty(Constants.PRODUCT_TOKEN_PROPERTY_KEY);
            if (StringUtils.isBlank(product)) {
                product = config.getProperty(Constants.PRODUCT_NAME_PROPERTY_KEY);
                productVersion = config.getProperty(Constants.PRODUCT_VERSION_PROPERTY_KEY);
            }

            UpdateInventoryResult updateResult = service.update(orgToken, product, productVersion, Arrays.asList(projectInfo));
            logResult(updateResult);
        } catch (WssServiceException e) {
            logger.error("Failed to update White Source server: " + e.getMessage(), e);
        } finally {
            if (service != null) {
                service.shutdown();
            }
        }
    }

    private WhitesourceService createWhitesourceService() {
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
        String[] files = scanner.getIncludedFiles();
        final File basedir = scanner.getBasedir();
        List<DependencyInfo> dependencyInfos = new ArrayList<DependencyInfo>();
        for (String file : files) {
            addNewDependencyInfo(file, basedir, dependencyInfos);
        }
        return dependencyInfos;
    }

    private void addNewDependencyInfo(String dependencyFileName, File basedir, List<DependencyInfo> dependencyInfos) {
        String sha1;
        File dependencyFile = new File(basedir, dependencyFileName);
        try {
            sha1 = ChecksumUtils.calculateSHA1(dependencyFile);
        } catch (IOException e) {
            logger.warn("Failed to add dependency " + dependencyFileName + " to dependency list: ", e);
            return;
        }
        final DependencyInfo dependencyInfo = new DependencyInfo(sha1);
        dependencyInfo.setArtifactId(dependencyFile.getName());
        dependencyInfos.add(dependencyInfo);
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
