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
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.io.*;
import java.util.*;

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
    private static final String jsonArgument="--json";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    public static final String DEPENDENCIES = "dependencies";
    public static final String VERSION = "version";


    /* --- Public methods --- */

    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();

        try {
            // execute 'npm ls --json'
            ProcessBuilder pb = new ProcessBuilder(npm, lsArgument,jsonArgument);
            pb.directory(new File(rootDirectory));
            Process process = pb.start();

            // parse 'npm ls --json' output

            String json = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                json = reader.lines().reduce("", String::concat);
                reader.close();
            } catch (IOException e) {
                logger.error("error parsing output : {}", e.getMessage());
            }

            HashMap<String, DependencyInfo> dependenciesMap = new HashMap<>();
            dependencies = getDependencies(new JSONObject(json) , dependenciesMap);

        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls'", e);
        }

        if (dependencies.size() == 0){
            logger.warn("Failed getting dependencies after running 'npm ls -json' Please run 'npm install' on the folder {0}", rootDirectory);
            dependencies.clear();
        }
        return dependencies;
    }

    /* --- Private methods --- */

    private DependencyInfo getDependencyInfo(String name, JSONObject pack, HashMap<String, DependencyInfo> alreadyAdded) {
        DependencyInfo dependency = new DependencyInfo();
        String version = pack.getString(VERSION);
        dependency.setGroupId(name);
        String filename = NpmPackageJsonFile.getNpmArtifactId(name, version);
        dependency.setArtifactId(filename);
        dependency.setVersion(version);
        dependency.setFilename(filename);
        dependency.setDependencyType(DependencyType.NPM);

        Collection<DependencyInfo> dependencies = getDependencies(pack, alreadyAdded);
        dependency.getChildren().addAll(dependencies);
        return dependency;
    }

    private Collection<DependencyInfo> getDependencies(JSONObject pack,HashMap<String, DependencyInfo> alreadyAdded) {
        Collection<DependencyInfo> dependencyInfos = new ArrayList<>();
        try {
            JSONObject jsonObj = pack.getJSONObject(DEPENDENCIES);

            if (jsonObj != null) {
                for (String key : jsonObj.keySet()) {
                    DependencyInfo dependencyInfo = getDependencyInfo(key, jsonObj.getJSONObject(key),alreadyAdded);
                    if (!alreadyAdded.containsKey(key)) {
                        alreadyAdded.put(dependencyInfo.getArtifactId(), dependencyInfo);
                        dependencyInfos.add(dependencyInfo);
                    }
                    else{
                        String s ="";
                    }
                }
            }
        }catch (JSONException je) {
            //logger.debug("no child dependencies for {}", pack.toString());
        }
        return dependencyInfos;
    }

    /* --- Static methods --- */

    private static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }
}
