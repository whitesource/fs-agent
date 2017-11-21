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

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.*;

/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class NpmLsJsonDependencyCollector implements DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String LS_COMMAND = "ls";
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
    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // execute 'npm ls'
            ProcessBuilder pb = new ProcessBuilder(getLsCommandParams());
            pb.directory(new File(rootDirectory));
            // redirect the error output to avoid output of npm ls by operating system
            String redirectErrorOutput = isWindows() ? "nul" : "/dev/null";
            pb.redirectError(new File(redirectErrorOutput));
            logger.debug("start 'npm ls'");
            Process process = pb.start();

            // parse 'npm ls' output
            // String json = null;
            StringBuilder json = new StringBuilder();
            InputStreamReader inputStreamReader = null;
            BufferedReader reader = null;
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            boolean continueReadingLines = true;
            try {
                inputStreamReader = new InputStreamReader(process.getInputStream());
                reader = new BufferedReader(inputStreamReader);
                logger.debug("trying to read dependencies using 'npm ls'");

                int lineIndex = 1;
                String line = "";
                while (continueReadingLines && line != null) {
                    Future<String> future = executorService.submit(new ReadLineTask(reader));
                    try {
                        line = future.get(this.npmTimeoutDependenciesCollector, TimeUnit.SECONDS);
                        if (StringUtils.isNotBlank(line)) {
                            logger.debug("Read line #{}: {}", lineIndex, line);
                            json.append(line);
                        } else {
                            logger.debug("Finished reading {} lines", lineIndex - 1);
                        }
                    } catch (TimeoutException e) {
                        logger.debug("Received timeout when reading line #" + lineIndex, e);
                        continueReadingLines = false;
                    } catch (Exception e) {
                        logger.debug("Error reading line #" + lineIndex, e);
                        continueReadingLines = false;
                    }
                    lineIndex++;
                }
            } catch (Exception e) {
                logger.error("error parsing output : {}", e.getMessage());
            } finally {
                executorService.shutdown();
                IOUtils.closeQuietly(inputStreamReader);
                IOUtils.closeQuietly(reader);
            }

            if (json != null && json.length() > 0 && continueReadingLines) {
                logger.debug("'npm ls' output is not empty");
                dependencies.addAll(getDependencies(new JSONObject(json.toString())));
            }
        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls --json' on {}", rootDirectory);
        }

        if (dependencies.isEmpty()) {
            if (!showNpmLsError) {
                logger.info("Failed getting dependencies after running '{}' Please run 'npm install' on the folder {}", getLsCommandParams(), rootDirectory);
                showNpmLsError = true;
            }
        }
        return dependencies;
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