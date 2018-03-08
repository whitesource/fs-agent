/**
 * Copyright (C) 2017 WhiteSource Ltd.
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
package org.whitesource.agent.dependency.resolver.python;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.dispatch.UpdateInventoryRequest;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.fs.OfflineReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.whitesource.scm.ScmConnector.SCM_CONNECTOR_TMP_DIRECTORY;

public class PythonDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.python.PythonDependencyResolver.class);
    private static final String PATTERN = "**/*";
    private static final String PYTHON_BOM = "requirements.txt";
    private static final String WHITESOURCE_TEMP_FOLDER = "Whitesource_python_resolver";

    private static final String WS_SETUP_PY = "ws_setup.py";
    private static final String WS_CONFIG = "ws_config.py";
    private static final String INSTALL = "install";
    private static final String WHITESOURCE_UPDATE_COMMAND = "whitesource_update";
    private static final String CONFIG_FLAG = "-p";
    private static final String WS_PYTHON_PACKAGE_NAME = "ws-python-package-name";
    private static final String UNINSTALL = "uninstall";
    private static final String WSS_PLUGIN = "wss_plugin";
    private static final String YES = "-y";
    private static final String UPDATE_REQUEST_JSON = "update_request.json";
    private static final String WHITESOURCE_OFFLINE_FOLDER = "whitesource";
    private static final String OFFLINE_FLAG = "-o";
    private static final String TRUE = "True";
    private static final String WS_PYTHON_PACKAGE_VERSION = "9.9.9.9";
    private static final String PY_EXT = ".py";
    private final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

    private final String[] pythonConfig = new String[]{
            "# config_file.py",
            "config_info = {",
            "'org_token': 'org-token',",
            "'check_policies': True,",
            "'force_check_all_dependencies': True,",
            "'force_update': True,",
            "'product_name': '" + WS_PYTHON_PACKAGE_NAME + "',",
            "'product_version': '1.0',",
            "'index_url': 'https://pypi.python.org/simple/', #optional",
            "'proxy': {",
            "'host': '',",
            "'port': '',",
            "'username': '',",
            "'password': ''",
            "}",
            "}"};

    private final String[] setupPy = new String[]{
            "from setuptools import setup",
            "from plugin import WssPythonPlugin",
            "requirements=WssPythonPlugin.open_required('requirements.txt')",
            "setup(",
            "    name=\"" + WS_PYTHON_PACKAGE_NAME + "\",",
            "    version=\"" + WS_PYTHON_PACKAGE_VERSION + "\",",
            "    entry_points={\"distutils.commands\": [\"whitesource_update = plugin.WssPythonPlugin:SetupToolsCommand\"]},",
            "    install_requires=requirements,",
            "    author=\"name\",",
            "    author_email=\"me@example.com\",",
            "    description=\"This is an example package\",",
            ")"};

    private final Collection<String> excludes = Arrays.asList("**/*" + PY_EXT);
    private final String pythonPath;
    private final String pipPath;
    private final boolean isPythonIsWssPluginInstalled;
    private final boolean uninstallPythonPlugin;

    public PythonDependencyResolver(String pythonPath, String pipPath , boolean isPythonIsWssPluginInstalled , boolean uninstallPythonPlugin) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.isPythonIsWssPluginInstalled = isPythonIsWssPluginInstalled;
        this.uninstallPythonPlugin = uninstallPythonPlugin;
    }

    /* --- Overridden methods --- */

    @Override
    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> configFiles) {
        String tempDir = getTempDir();
        Map<AgentProjectInfo, Path> resolvedProjects = new HashMap<>();
        String[] args = new String[0];
        List<String> output = new ArrayList<>();
        try {
            FileUtils.forceMkdir(new File(tempDir));

            // FSA will copy the whole directory to a temp folder
            boolean isTempDirectory = topLevelFolder.contains(SCM_CONNECTOR_TMP_DIRECTORY);
            if (!isTempDirectory) {
                FileUtils.copyDirectory(new File(topLevelFolder), new File(tempDir));
            } else {
                tempDir = topLevelFolder;
            }

            // FSA will crete custom ws_setup.py and custom ws_config.py
            Path pathConfig = Paths.get(tempDir.toString(), WS_CONFIG);
            Path pathSetupPy = Paths.get(tempDir.toString(), WS_SETUP_PY);

            saveConfigFile(pathConfig, pythonConfig);
            saveConfigFile(pathSetupPy, setupPy);

            // FSA will run "pip install wss_plugin"
            if (!isPythonIsWssPluginInstalled) {
                output = processCommand(tempDir, new String[]{pipPath, INSTALL, WSS_PLUGIN});
            }
            // FSA will run "python setup.py install"
            output = processCommand(tempDir, new String[]{pythonPath, WS_SETUP_PY, INSTALL});
            // FSA will run "python setup.py whitesource_update -p "custom_config.py"
            output = processCommand(tempDir, new String[]{pythonPath, WS_SETUP_PY, WHITESOURCE_UPDATE_COMMAND, CONFIG_FLAG, WS_CONFIG, OFFLINE_FLAG, TRUE});

            // Python-plugin will export an offline file
            // FSA will read the python offline request
            OfflineReader offlineReader = new OfflineReader();
            String offlineFile = Paths.get(tempDir, WHITESOURCE_OFFLINE_FOLDER, UPDATE_REQUEST_JSON).toString();
            if (Files.exists(Paths.get(offlineFile))) {
                Collection<UpdateInventoryRequest> updateInventoryRequests = offlineReader.getAgentProjectsFromRequests(Arrays.asList(offlineFile));
                Collection<AgentProjectInfo> projects = updateInventoryRequests.stream().flatMap(update -> update.getProjects().stream()).collect(Collectors.toList());
                // add projects to map
                if (projects != null && projects.size() > 0) {
                    AgentProjectInfo project = projects.stream().findFirst().get();
                    project.getDependencies().forEach(dependencyInfo ->
                            dependencyInfo.setDependencyType(DependencyType.PYTHON));
                    resolvedProjects.put(project, Paths.get(tempDir));
                }
            } else {
                logger.warn("Offline file '" + offlineFile + "' could not be found");
            }

            // FSA will run pip uninstall "project-name"
            output = processCommand(tempDir, new String[]{pipPath, UNINSTALL, YES, WS_PYTHON_PACKAGE_NAME});

            // FSA will run "pip uninstall wss_plugin" if we already installed and the user asked for uninstall
            if (uninstallPythonPlugin && !isPythonIsWssPluginInstalled) {
                output = processCommand(tempDir, new String[]{pipPath, UNINSTALL, YES, WSS_PLUGIN});
            }

            if (!isTempDirectory) {
                FileUtils.deleteDirectory(new File(tempDir));
            }
        } catch (IOException e) {
            logger.warn("Failed to get python dependencies : " + e.getMessage());
            logger.debug("Error while running ", String.join(" ", args));
            logger.debug("Error while running ", String.join(" ", output));
        }

        return new ResolutionResult(resolvedProjects, getExcludes(), DependencyType.PYTHON, topLevelFolder);
    }

    private List<String> processCommand(String tempDir, String[] args) throws IOException {
        try {
            CommandLineProcess commandLineProcess = new CommandLineProcess(tempDir, args);
            List<String> lines = commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                logger.debug("Fail to run '" + String.join(" ", args) + "' in '" + tempDir + "'.\n  Try running custom process manually");
            }
            return lines;
        } catch (IOException ioe) {
            logger.error("Consider adding '" + args[0] + "' to the PATH or set '" + args[0] + "' full path in the configuration file");
            throw ioe;
        }
    }

    private boolean saveConfigFile(Path config, String[] fileLines) {
        try {
            Files.write(config, Arrays.stream(fileLines).collect(Collectors.toList()));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private String getTempDir() {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String tempFolder = JAVA_TEMP_DIR.endsWith(File.separator) ? JAVA_TEMP_DIR + WHITESOURCE_TEMP_FOLDER + File.separator + creationDate :
                JAVA_TEMP_DIR + File.separator + WHITESOURCE_TEMP_FOLDER + File.separator + creationDate;

        return tempFolder;
    }

    @Override
    protected Collection<String> getExcludes() {
        return excludes ;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(PY_EXT));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.PYTHON;
    }

    @Override
    public String getBomPattern() {
        return PATTERN + PYTHON_BOM;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }
}
