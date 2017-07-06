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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collect dependencies using 'npm ls' command.
 *
 * @author eugen.horovitz
 */
public class NpmLsDependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsDependencyCollector.class);

    private static final int PARENT_TO_CHILD_DISTANCE = 2;
    private static final String npm = isWindows() ? "npm.cmd" : "npm";
    private static final String LINES = "-- ";
    private static final String PLUS = "+";
    private static final String APOSTROPHE = "`";
    private static final String SPACE = " ";
    private static final String EMPTY = "`-- (empty)";
    private static final String lsArgument = "ls";
    private static final String TWO_DASHES_ONE_SPACE = "-- ";
    private static final String AT_SIGN = "@";
    private static final String ONE_BAR_SPACE = "| ";
    private static final String UNMET_OPTIONAL_DEPENDENCY = "UNMET OPTIONAL DEPENDENCY ";
    private static final String UNMET_PEER_DEPENDENCY = "UNMET PEER DEPENDENCY ";
    private static final String UNMET_DEPENDENCY = " UNMET DEPENDENCY ";
    private static final String OS_NAME = "os.name";
    private static final String SLASH = "\\";
    private static final String WINDOWS = "win";

    /* --- Public methods --- */

    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Map<Integer, List<DependencyInfo>> levelToDependenciesMap = new HashMap<>();
        List<DependencyInfo> dependencies = new ArrayList<>();

        try {
            // execute 'npm ls'
            ProcessBuilder pb = new ProcessBuilder(npm, lsArgument);
            pb.directory(new File(rootDirectory));
            Process process = pb.start();

            // parse 'npm ls' output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<String> lines = reader.lines().collect(Collectors.toList());
            for (String line : lines) {
                if (line.length() == 0 || line.contains(SLASH) || line.contains(EMPTY)) {
                    continue;
                }

                int localDepth = line.indexOf(TWO_DASHES_ONE_SPACE);
                String[] pack = getSplitNameVersion(line);

                // sometimes the dependencies are not met or the line contains errors
                if (pack.length == PARENT_TO_CHILD_DISTANCE && !isInvalidLine(line)) {
                    DependencyInfo dependencyInfo = getDependencyInfo(pack);
                    addDependency(levelToDependenciesMap, localDepth, dependencyInfo);

                    if (levelToDependenciesMap.get(localDepth - PARENT_TO_CHILD_DISTANCE) != null) {
                        DependencyInfo parent = levelToDependenciesMap.get(localDepth - PARENT_TO_CHILD_DISTANCE)
                                .get(levelToDependenciesMap.get(localDepth - PARENT_TO_CHILD_DISTANCE).size() - 1);
                        parent.getChildren().add(dependencyInfo);
                    }
                    dependencies.add(dependencyInfo);
                }
            }
        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls'", e);
        }

        if (dependencies.stream().allMatch(dependencyInfo ->
                dependencyInfo.getArtifactId().contains(UNMET_DEPENDENCY.replace(SPACE, "")))) {
            logger.warn("Failed getting dependencies after running 'npm ls' Please run 'npm install' on the folder {0}", rootDirectory);
            dependencies.clear();
        }
        return dependencies;
    }

    /* --- Private methods --- */

    private String[] getSplitNameVersion(String line) {
        String[] lineSplit = line.replace(ONE_BAR_SPACE, "").split(AT_SIGN);

        //handle scoped packages with double at sign @
        if (lineSplit.length > 2) {
            if (lineSplit.length > 2) {
                String[] pack = new String[2];
                pack[1] = lineSplit[lineSplit.length - 1];

                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < lineSplit.length; i++)
                    if (i < lineSplit.length - 1) {
                        builder.append(lineSplit[i]);
                    }
                pack[0] = builder.toString();
                return pack;
            }
        }
        return lineSplit;
    }

    private boolean isInvalidLine(String line) {
        boolean result = line.length() > UNMET_PEER_DEPENDENCY.length() &&
                (line.contains(UNMET_OPTIONAL_DEPENDENCY)
                        || line.contains(UNMET_PEER_DEPENDENCY));
        return result;
    }

    private void addDependency(Map<Integer, List<DependencyInfo>> levelToDependenciesMap, int localDepth, DependencyInfo dependencyInfo) {
        if (!levelToDependenciesMap.keySet().contains(localDepth)) {
            levelToDependenciesMap.put(localDepth, new ArrayList<>());
        }
        levelToDependenciesMap.get(localDepth).add(dependencyInfo);
    }

    private DependencyInfo getDependencyInfo(String[] pack) {
        DependencyInfo dependency = new DependencyInfo();
        String version = pack[1];
        String name = pack[0]
                .replace(LINES, "")
                .replace(PLUS, "")
                .replace(APOSTROPHE, "")
                .replace(SPACE, "");
        dependency.setGroupId(name);
        String filename = NpmPackageJsonFile.getNpmArtifactId(name, version);
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
