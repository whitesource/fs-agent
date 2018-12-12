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
package org.whitesource.fs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.util.ContextInitializer;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ProjectsSender;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.utils.Pair;
import org.whitesource.fs.configuration.ConfigurationSerializer;
import org.whitesource.fs.configuration.RequestConfiguration;
import org.whitesource.web.FsaVerticle;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Author: Itai Marko
 */
public class Main {
    protected static final String LOGBACK_FSA_XML = "logback-FSA.xml";

    /* --- Static members --- */

    public static Logger logger; // don't initialize the logger here, only after setting the
    // ContextInitializer.CONFIG_FILE_PROPERTY property (set inside setLoggerConfiguration method)
    public static final long MAX_TIMEOUT = 1000 * 60 * 60;
    private static ProjectsSender projectsSender = null;
    private static Vertx vertx;
    public static int exitCode = 0;

    ProjectsCalculator projectsCalculator = new ProjectsCalculator();
    public static final String HELP_CONTENT_FILE_NAME = "helpContent.txt";

    /* --- Main --- */

    public static void main(String[] args) {
        int exitCode = mainScan(args);
        exit(exitCode);
    }

    private static int mainScan(String[] args) {

        if (isHelpArg(args)) {
            printHelpContent();
            System.exit(StatusCode.SUCCESS.getValue());
        }

        CommandLineArgs commandLineArgs = new CommandLineArgs();
        commandLineArgs.parseCommandLine(args);

        StatusCode processExitCode;

        // read configuration senderConfig
        FSAConfiguration fsaConfiguration = new FSAConfiguration(args);
        // don't make any reference to the logger before calling this method

        setLoggerConfiguration(fsaConfiguration.getLogLevel(), fsaConfiguration.getLogContext());

        boolean isStandalone = commandLineArgs.web.equals(Constants.FALSE);
        logger.info(fsaConfiguration.toString());
        if (fsaConfiguration.getSender() != null && fsaConfiguration.getSender().isSendLogsToWss()) {
            logger.info("-----------------------------------------------------------------------------");
            logger.info("'sendLogsToWss' parameter is enabled");
            logger.info("Data of your scan will be sent to WhiteSource for diagnostic purposes");
            logger.info("-----------------------------------------------------------------------------");
        }
        if (isStandalone) {
            try {
                if (fsaConfiguration.getErrors() == null || fsaConfiguration.getErrors().size() > 0) {
                    processExitCode = StatusCode.ERROR;
                    fsaConfiguration.getErrors().forEach(error -> logger.error(error));
                    logger.warn("Exiting");
                } else {
                    processExitCode = new Main().scanAndSend(fsaConfiguration, true).getStatusCode();
                }
            } catch (Exception e) {
                // catch any exception that may be thrown, return error code
                logger.warn("Process encountered an error: {}" + e.getMessage(), e);
                processExitCode = StatusCode.ERROR;
            } finally {
                new TempFolders().deleteTempFolders();
            }

            logger.info("Process finished with exit code {} ({})", processExitCode.name(), processExitCode.getValue());
            exitCode = getValue(processExitCode);
        } else {
            //this is a work around
            vertx = Vertx.vertx(new VertxOptions()
                    .setBlockedThreadCheckInterval(MAX_TIMEOUT));

            JsonObject config = new JsonObject();
            config.put(FsaVerticle.CONFIGURATION, new ConfigurationSerializer().getAsString(fsaConfiguration, false));
            DeploymentOptions options = new DeploymentOptions()
                    .setConfig(config)
                    .setWorker(true);
            vertx.deployVerticle(FsaVerticle.class.getName(), options);
        }
        return exitCode;
    }

    private static int getValue(StatusCode processExitCode) {
        return processExitCode.getValue();
    }

    private static void setLoggerConfiguration(String logLevel, String logContext) {
        // setting the logback name manually, to override the default logback.xml which is originated from the jar of wss-agent-api-client.
        // making sure this is done before initializing the logger object, for otherwise this overriding will fail
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, LOGBACK_FSA_XML);
        if (StringUtils.isNotEmpty(logContext)) {
            LoggerFactory.contextId = logContext;
        }
        logger = LoggerFactory.getLogger(Main.class);
        // read log level from configuration file
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Logger mapLog = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Constants.MAP_LOG_NAME);
        root.setLevel(Level.toLevel(logLevel, Level.INFO));
        ((LogMapAppender) mapLog.getAppender(Constants.MAP_APPENDER_NAME)).setRootLevel(root.getLevel());
    }

    public ProjectsDetails scanAndSend(FSAConfiguration fsaConfiguration, boolean shouldSend) {
        if (fsaConfiguration.getErrors() != null && fsaConfiguration.getErrors().size() > 0) {
            return new ProjectsDetails(new ArrayList<>(), StatusCode.ERROR, String.join(System.lineSeparator(), fsaConfiguration.getErrors()));
        }

        ProjectsDetails result = projectsCalculator.getAllProjects(fsaConfiguration);

        OfflineReader offlineReader = new OfflineReader();
        Collection<UpdateInventoryRequest> updateInventoryRequests = offlineReader.getAgentProjectsFromRequests(fsaConfiguration.getOfflineRequestFiles());
        Collection<AgentProjectInfo> offlineProjects = updateInventoryRequests.stream().flatMap(updateInventoryRequest ->
                updateInventoryRequest.getProjects().stream()).collect(Collectors.toList());

        if (fsaConfiguration.getOfflineRequestFiles() != null) {
            result.addOfflineProjects(offlineProjects);
        } else {
            // in case of offline requests remove other
        }

        if (fsaConfiguration.getUseCommandLineProjectName()) {
            // change project name from command line in case the user sent name via commandLine
            String projectName = fsaConfiguration.getRequest().getProjectName();

            Set<AgentProjectInfo> agentProjectInfos = new HashSet<>();
            for (AgentProjectInfo projectInfo : result.getProjectToViaComponents().keySet()) {
                agentProjectInfos.add(projectInfo);
            }
            if (agentProjectInfos.size() == 1 && projectName != null) {
                for (AgentProjectInfo project : agentProjectInfos) {
                    project.getCoordinates().setArtifactId(projectName);
                }
            }
        }

        RequestConfiguration req = fsaConfiguration.getRequest();
        // updating the product name and version from the offline file
        if (fsaConfiguration != null && !fsaConfiguration.getUseCommandLineProductName() && updateInventoryRequests.size() > 0) {
            UpdateInventoryRequest offLineReq = updateInventoryRequests.stream().findFirst().get();
            req = new RequestConfiguration(req.getApiToken(), req.getUserKey(), req.getRequesterEmail(), req.isProjectPerSubFolder(), req.getProjectName(),
                    req.getProjectToken(), req.getProjectVersion(), offLineReq.product(), null, offLineReq.productVersion(),
                    req.getAppPaths(), req.getViaDebug(), req.getViaAnalysisLevel(), req.getIaLanguage(), req.getScanComment(), req.isRequireKnownSha1());
        }

        if (!result.getStatusCode().equals(StatusCode.SUCCESS)) {
            return new ProjectsDetails(result.getProjects(), result.getStatusCode(), Constants.EMPTY_STRING);
        }

        if (shouldSend) {
            ProjectsSender projectsSender = getProjectsSender(fsaConfiguration, req);
            Pair<String, StatusCode> processExitCode = sendProjects(projectsSender, result);
            logger.debug("Process finished with exit code {} ({})", processExitCode.getKey(), processExitCode.getValue());
            return new ProjectsDetails(new ArrayList<>(), processExitCode.getValue(), processExitCode.getKey());
        } else {
            return new ProjectsDetails(result.getProjects(), result.getStatusCode(), Constants.EMPTY_STRING);
        }
    }

    private ProjectsSender getProjectsSender(FSAConfiguration fsaConfiguration, RequestConfiguration req) {
        ProjectsSender projectsSender;
        if (!projectSenderExist()) {
            projectsSender = new ProjectsSender(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), req, new FileSystemAgentInfo());
        } else {
            projectsSender = Main.projectsSender;
        }
        return projectsSender;
    }

    private Pair<String, StatusCode> sendProjects(ProjectsSender projectsSender, ProjectsDetails projectsDetails) {
        Collection<AgentProjectInfo> projects = projectsDetails.getProjects();
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
            return new Pair<>("Exiting, nothing to update", StatusCode.SUCCESS);
        } else {
            return projectsSender.sendRequest(projectsDetails);//todo
        }
    }

    private static boolean isHelpArg(String[] args) {
        for (String arg : args) {
            if (Constants.HELP_ARG1.equals(arg) || Constants.HELP_ARG2.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printHelpContent() {
        logger = LoggerFactory.getLogger(Main.class);
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        try {
            ClassLoader classLoader = Main.class.getClassLoader();
            inputStream = classLoader.getResourceAsStream(HELP_CONTENT_FILE_NAME);
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String result = "";
            String line = bufferedReader.readLine();
            while (line != null) {
                result = result + line + System.lineSeparator();
                line = bufferedReader.readLine();
            }
            logger.info(result);
        } catch (IOException e) {
            logger.warn("Could not show the help command");
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        } catch (IOException e) {
            logger.warn("Could not close the help file");
        }
    }

    public static void exit(int statusCode) {
        System.exit(statusCode);
    }

    private boolean projectSenderExist() {
        return Main.projectsSender != null;
    }

    // end to end integration projectSenderExist
    protected static void endToEndIntegration(String[] args, ProjectsSender testProjectsSender) {
        projectsSender = testProjectsSender;
        mainScan(args);
    }
}