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
package org.whitesource.agent.dependency.resolver.npm;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class NpmLsJsonDependencyCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String LS_COMMAND = "ls";
    public static final String INSTALL_COMMAND = "install";
    public static final String LS_PARAMETER_JSON = "--json";

    private static final String NPM_COMMAND = isWindows() ? "npm.cmd" : "npm";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    private static final String DEPENDENCIES = "dependencies";
    private static final String VERSION = "version";
    private static final String RESOLVED = "resolved";
    private static final String LS_ONLY_PROD_ARGUMENT = "--only=prod";
    private static final String MISSING = "missing";
    public static final String PEER_MISSING = "peerMissing";
    private static final String NAME = "name";

    /* --- Members --- */

    protected final boolean includeDevDependencies;
    private boolean showNpmLsError;
    private final long npmTimeoutDependenciesCollector;

    /* --- Constructors --- */

    public NpmLsJsonDependencyCollector(boolean includeDevDependencies, long npmTimeoutDependenciesCollector) {
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.includeDevDependencies = includeDevDependencies;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            CommandLineProcess npmLs = new CommandLineProcess(rootDirectory, getLsCommandParams());
            npmLs.setTimeoutReadLineSeconds(this.npmTimeoutDependenciesCollector);
            List<String> lines = npmLs.executeProcess();
            StringBuilder json = new StringBuilder();
            for (String line : lines) {
                json.append(line);
            }
            if (json != null && json.length() > 0 && !npmLs.isErrorInProcess()) {
                logger.debug("'npm ls' output is not empty");
                dependencies.addAll(getDependencies(new JSONObject(json.toString())));
            }
        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls --json' on {}", rootDirectory);
        }

        if (dependencies.isEmpty()) {
            if (!showNpmLsError) {
                logger.info("Failed getting dependencies after running '{}' Please run {} on the folder {}", getLsCommandParams(),getInstallParams(), rootDirectory);
                showNpmLsError = true;
            }
        }
        return getSingleProjectList(dependencies);
    }

    public boolean executePreparationStep(String folder ) {
        String[] command = getInstallParams();
        logger.debug("Running install command : " + command);
        CommandLineProcess npmInstall = new CommandLineProcess(folder, command);
        try {
            npmInstall.executeProcessWithoutOutput();
        } catch (IOException e) {
            logger.debug("Could not run " + command + " in folder " + folder);
            return false;
        }
        return npmInstall.isErrorInProcess();
    }

    /* --- Private methods --- */

    private Collection<DependencyInfo> getDependencies(JSONObject jsonObject) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        if (jsonObject.has(DEPENDENCIES)) {
            JSONObject dependenciesJsonObject = jsonObject.getJSONObject(DEPENDENCIES);
            if (dependenciesJsonObject != null) {
                for (String dependencyAlias : dependenciesJsonObject.keySet()) {
                    JSONObject dependencyJsonObject = dependenciesJsonObject.getJSONObject(dependencyAlias);
                    if (dependencyJsonObject.keySet().isEmpty()) {
                        logger.debug("Dependency {} has no JSON content", dependencyAlias);
                    } else {
                        DependencyInfo dependency = getDependency(dependencyAlias, dependencyJsonObject);
                        if (dependency != null) {
                            dependencies.add(dependency);

                            logger.debug("Collect child dependencies of {}", dependencyAlias);
                            // collect child dependencies
                            Collection<DependencyInfo> childDependencies = getDependencies(dependencyJsonObject);
                            dependency.getChildren().addAll(childDependencies);
                        }
                    }
                }
            }
        }
        return dependencies;
    }

    private String getVersionFromLink(String linkResolved) {
        URI uri = null;
        try {
            uri = new URI(linkResolved);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        String path = uri.getPath();
        String idStr = path.substring(path.lastIndexOf('/') + 1);
        int lastIndexOfDash = idStr.lastIndexOf("-");
        int lastIndexOfDot = idStr.lastIndexOf(".");
        String resultVersion = idStr.substring(lastIndexOfDash + 1, lastIndexOfDot);
        return resultVersion;
    }

    /* --- Protected methods --- */

    protected String[] getInstallParams() {
        return new String[]{NPM_COMMAND, INSTALL_COMMAND};
    }

    protected String[] getLsCommandParams() {
        if (includeDevDependencies) {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_PARAMETER_JSON};
        } else {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_ONLY_PROD_ARGUMENT, LS_PARAMETER_JSON};
        }
    }

    protected DependencyInfo getDependency(String dependencyAlias, JSONObject jsonObject) {
        String name = dependencyAlias;
        String version;
        if (jsonObject.has(VERSION)) {
            version = jsonObject.getString(VERSION);
        } else {
            if (jsonObject.has(RESOLVED)) {
                version = getVersionFromLink(jsonObject.getString(RESOLVED));
            } else if (jsonObject.has(MISSING) && jsonObject.getBoolean(MISSING)) {
                logger.warn("Unmet dependency --> {}", name);
                return null;
            } else if (jsonObject.has(PEER_MISSING) && jsonObject.getBoolean(PEER_MISSING)) {
                logger.warn("Unmet dependency --> peer missing {}", name);
                return null;
            } else {
                // we still should return null since this is a non valid dependency
                logger.warn("Unknown error. 'version' tag could not be found for {}", name);
                return null;
            }
        }

        String filename = NpmBomParser.getNpmArtifactId(name, version);
        DependencyInfo dependency = new DependencyInfo();
        dependency.setGroupId(name);
        dependency.setArtifactId(filename);
        dependency.setVersion(version);
        dependency.setFilename(filename);
        dependency.setDependencyType(DependencyType.NPM);
        return dependency;
    }

    /* --- Static methods --- */

    public static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }

    /* --- Nested classes --- */

    class ReadLineTask implements Callable<String> {

        /* --- Members --- */

        private final BufferedReader reader;

        /* --- Constructors --- */

        ReadLineTask(BufferedReader reader) {
            this.reader = reader;
        }

        /* --- Overridden methods --- */

        @Override
        public String call() throws Exception {
//            while (true) { }
            return reader.readLine();
        }
    }
}