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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class NpmLsJsonDependencyCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String LS_COMMAND = "ls";
    public static final String LS_PARAMETER_JSON = "--json";

    private static final String NPM_COMMAND = isWindows() ? "npm.cmd" : "npm";
    private static final String RESOLVED = "resolved";
    private static final String LS_ONLY_PROD_ARGUMENT = "--only=prod";
    public static final String PEER_MISSING = "peerMissing";
    private static final String DEDUPED = "deduped";
    private static final String REQUIRED = "required";
    private static final String IGNORE_SCRIPTS = "--ignore-scripts";
    // Could be used for "-- UNMET DEPENDENCY" or "-- UNMET OPTIONAL DEPENDENCY"
    private static final String UNMET_DEPENDENCY = "-- UNMET ";
    private final Pattern GENERAL_PACKAGE_NAME_PATTERN = Pattern.compile(".* (.*)@(\\^)?[0-9]+\\.[0-9]+");
    private final Pattern SECOND_GENERAL_PACKAGE_PATTERN = Pattern.compile(".* (.*)@");

    /* --- Members --- */

    protected final boolean includeDevDependencies;
    protected final boolean ignoreNpmLsErrors;
    private boolean showNpmLsError;
    protected boolean npmLsFailureStatus;
    protected final long npmTimeoutDependenciesCollector;
    private final boolean ignoreScripts;

    /* --- Constructors --- */

    public NpmLsJsonDependencyCollector(boolean includeDevDependencies, long npmTimeoutDependenciesCollector, boolean ignoreNpmLsErrors, boolean ignoreScripts) {
        this.npmTimeoutDependenciesCollector = npmTimeoutDependenciesCollector;
        this.includeDevDependencies = includeDevDependencies;
        this.ignoreNpmLsErrors = ignoreNpmLsErrors;
        this.ignoreScripts = ignoreScripts;
        this.npmLsFailureStatus = false;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        try {
            CommandLineProcess npmLsJson = new CommandLineProcess(rootDirectory, getLsCommandParamsJson());
            npmLsJson.setTimeoutReadLineSeconds(this.npmTimeoutDependenciesCollector);
            List<String> linesOfNpmLsJson = npmLsJson.executeProcess();
            // flag that indicates if the 'npm ls' command failed
            this.npmLsFailureStatus = npmLsJson.isErrorInProcess() && !this.ignoreNpmLsErrors;
            StringBuilder json = new StringBuilder();
            for (String line : linesOfNpmLsJson) {
                json.append(line);
            }
            if (json != null && json.length() > 0 && (!npmLsJson.isErrorInProcess() || this.ignoreNpmLsErrors)) {
                logger.debug("'npm ls' output is not empty");
                if(npmLsJson.isErrorInProcess() && this.ignoreNpmLsErrors) {
                    logger.info("Ignore errors of 'npm ls'");
                }
                getDependencies(new JSONObject(json.toString()), rootDirectory, dependencies);
            }
        } catch (IOException e) {
            this.npmLsFailureStatus = true;
            logger.warn("Error getting dependencies after running 'npm ls --json' on {}, error : {}", rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }

        if (dependencies.isEmpty()) {
            if (!showNpmLsError && this.npmLsFailureStatus) {
                logger.warn("Failed to getting dependencies after running '{}', run {} on {} folder", getLsCommandParams(), getInstallParams(), rootDirectory);
                showNpmLsError = true;
            }
        }
        return getSingleProjectList(dependencies);
    }

    public boolean executePreparationStep(String folder) {
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

    private int getDependencies(JSONObject npmLsJson, List<String> linesOfNpmLs, int currentLineNumber, Collection<DependencyInfo> dependencies) {
        if (npmLsJson.has(Constants.DEPENDENCIES)) {
            JSONObject dependenciesJsonObject = npmLsJson.getJSONObject(Constants.DEPENDENCIES);
            if (dependenciesJsonObject != null) {
                for (int i = 0; i < dependenciesJsonObject.keySet().size(); i++) {
                    String currentLine = linesOfNpmLs.get(currentLineNumber);
                    // Examples:
                    // +-- UNMET DEPENDENCY @material-ui/core@1.5.0
                    // | +-- UNMET DEPENDENCY @babel/runtime@7.0.0-beta.42
                    // | | `-- UNMET DEPENDENCY regenerator-runtime@0.11.1
                    // |   | +-- UNMET OPTIONAL DEPENDENCY are-we-there-yet@1.1.4
                    // |   | | +-- UNMET OPTIONAL DEPENDENCY delegates@1.0.0
                    // |   | | `-- UNMET OPTIONAL DEPENDENCY readable-stream@2.3.6
                    if (currentLine.endsWith(DEDUPED) || currentLine.contains(UNMET_DEPENDENCY)) {
                        currentLineNumber++;
                        continue;
                    }
                    String dependencyAlias = getTheNextPackageNameFromNpmLs(currentLine);
                    try {
                        JSONObject dependencyJsonObject = dependenciesJsonObject.getJSONObject(dependencyAlias);
                        DependencyInfo dependency = getDependency(dependencyAlias, dependencyJsonObject);
                        if (dependency != null) {
                            dependencies.add(dependency);
                            logger.debug("Collect child dependencies of {}", dependencyAlias);
                            // collect child dependencies
                            Collection<DependencyInfo> childDependencies = new ArrayList<>();
                            currentLineNumber = getDependencies(dependencyJsonObject, linesOfNpmLs, currentLineNumber + 1, childDependencies);
                            dependency.getChildren().addAll(childDependencies);
                        } else {
                            // it can be only if was an error in 'npm ls'
                            if (dependencyJsonObject.has(REQUIRED)) {
                                Object requiredObject = dependencyJsonObject.get(REQUIRED);
                                if (requiredObject instanceof JSONObject) {
                                    currentLineNumber = getDependencies((JSONObject) requiredObject, linesOfNpmLs, currentLineNumber + 1, new ArrayList<>());
                                } else {
                                    currentLineNumber++;
                                }
                            } else {
                                currentLineNumber++;
                            }
                        }
                    } catch (JSONException e){
                        if (ignoreNpmLsErrors) {
                            logger.error(e.getMessage());
                            logger.debug("{}", e.getStackTrace());
                            currentLineNumber++;
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
        return currentLineNumber;
    }

    protected String getTheNextPackageNameFromNpmLs(String line) {
        String result = null;
        Matcher matcher = this.GENERAL_PACKAGE_NAME_PATTERN.matcher(line);
        if (matcher.find()) {
            // take only the name of the package from the match
            result = matcher.group(1);
        }
        if (result == null) {
            matcher = this.SECOND_GENERAL_PACKAGE_PATTERN.matcher(line);
            if (matcher.find()) {
                result = matcher.group(1);
            }
        }
        return result;
    }

    private String getVersionFromLink(String linkResolved) {
        URI uri = null;
        try {
            uri = new URI(linkResolved);
        } catch (URISyntaxException e) {
            logger.error(e.getMessage());
            logger.debug("{}", e.getStackTrace());
        }
        String path = uri.getPath();
        String idStr = path.substring(path.lastIndexOf('/') + 1);
        int lastIndexOfDash = idStr.lastIndexOf(Constants.DASH);
        int lastIndexOfDot = idStr.lastIndexOf(Constants.DOT);
        String resultVersion = idStr.substring(lastIndexOfDash + 1, lastIndexOfDot);
        return resultVersion;
    }

    /* --- Protected methods --- */

    protected void getDependencies(JSONObject jsonObject, String rootDirectory, Collection<DependencyInfo> dependencies) {
        try {
            CommandLineProcess npmLs = new CommandLineProcess(rootDirectory, getLsCommandParams());
            npmLs.setTimeoutReadLineSeconds(this.npmTimeoutDependenciesCollector);
            List<String> linesOfNpmLs = npmLs.executeProcess();
            getDependencies(jsonObject, linesOfNpmLs, 1, dependencies);
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running 'npm ls --json' on {}, error : {}", rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }
    }

    protected String[] getInstallParams() {
        if (this.ignoreScripts) {
            return new String[]{NPM_COMMAND, Constants.INSTALL, IGNORE_SCRIPTS};
        } else {
            return new String[]{NPM_COMMAND, Constants.INSTALL};
        }
    }

    protected String[] getLsCommandParams() {
        if (includeDevDependencies) {
            return new String[]{NPM_COMMAND, LS_COMMAND};
        } else {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_ONLY_PROD_ARGUMENT};
        }
    }

    protected String[] getLsCommandParamsJson() {
        if (includeDevDependencies) {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_PARAMETER_JSON};
        } else {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_ONLY_PROD_ARGUMENT, LS_PARAMETER_JSON};
        }
    }

    protected DependencyInfo getDependency(String dependencyAlias, JSONObject jsonObject) {
        String name = dependencyAlias;
        String version;
        if (jsonObject.has(Constants.VERSION)) {
            version = jsonObject.getString(Constants.VERSION);
        } else {
            if (jsonObject.has(RESOLVED)) {
                version = getVersionFromLink(jsonObject.getString(RESOLVED));
            } else if (jsonObject.has(Constants.MISSING) && jsonObject.getBoolean(Constants.MISSING)) {
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

    public boolean getNpmLsFailureStatus() {
        return this.npmLsFailureStatus;
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