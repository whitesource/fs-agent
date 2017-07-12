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
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
/**
 * Collect dependencies using 'npm ls' command.
 *
 * @author eugen.horovitz
 */
public class NpmLsJsonDependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    private static final String npm = isWindows() ? "npm.cmd" : "npm";
    private static final String lsArgument = "ls";
    private static final String jsonArgument = "--json";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    private static final String DEPENDENCIES = "dependencies";
    private static final String VERSION = "version";
    private static final String lsOnlyProdArgument = "--only=prod";
    private final String[] npmArguments;

    /* --- Constructors --- */

    public NpmLsJsonDependencyCollector(boolean includeDevDependencies) {
        if (includeDevDependencies) {
            npmArguments = new String[]{npm, lsArgument, jsonArgument};
        } else {
            npmArguments = new String[]{npm, lsArgument, lsOnlyProdArgument, jsonArgument};
        }
    }

    public NpmLsJsonDependencyCollector() {
        this(false);
    }

    /* --- Public methods --- */

    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // execute 'npm ls'
            ProcessBuilder pb = new ProcessBuilder(npmArguments);

            pb.directory(new File(rootDirectory));
            Process process = pb.start();

            // parse 'npm ls' output
            String json = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                json = reader.lines().reduce("", String::concat);
                reader.close();
            } catch (IOException e) {
                logger.error("error parsing output : {}", e.getMessage());
            }

            dependencies.addAll(getDependencies(new JSONObject(json)));
        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls --json' on {}", rootDirectory);
        }

        if (dependencies.isEmpty()) {
            logger.warn("Failed getting dependencies after running 'npm ls --json' Please run 'npm install' on the folder {}", rootDirectory);
        }
        return dependencies;
    }

    /* --- Private methods --- */

    private Collection<DependencyInfo> getDependencies(JSONObject jsonObject) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        if (jsonObject.has(DEPENDENCIES)) {
            JSONObject dependenciesJsonObject = jsonObject.getJSONObject(DEPENDENCIES);
            if (dependenciesJsonObject != null) {
                for (String dependencyName : dependenciesJsonObject.keySet()) {
                    JSONObject dependencyJsonObject = dependenciesJsonObject.getJSONObject(dependencyName);
                    if (dependencyJsonObject.keySet().isEmpty()) {
                        logger.debug("Dependency {} has no JSON content", dependencyName);
                    } else {
                        DependencyInfo dependency = getDependency(dependencyName, dependencyJsonObject);
                        dependencies.add(dependency);

                        // collect child dependencies
                        Collection<DependencyInfo> childDependencies = getDependencies(dependencyJsonObject);
                        dependency.getChildren().addAll(childDependencies);
                    }
                }
            }
        }
        return dependencies;
    }

    private DependencyInfo getDependency(String name, JSONObject jsonObject) {
        String version = jsonObject.getString(VERSION);
        String filename = NpmPackageJsonFile.getNpmArtifactId(name, version);

        DependencyInfo dependency = new DependencyInfo();
        dependency.setGroupId(name);
        dependency.setArtifactId(filename);
        dependency.setVersion(version);
        dependency.setFilename(filename);
        dependency.setDependencyType(DependencyType.NPM);
        return dependency;
    }

    /* --- Static methods --- */

    private static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }
}