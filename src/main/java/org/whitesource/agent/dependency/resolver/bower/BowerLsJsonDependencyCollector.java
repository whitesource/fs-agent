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
package org.whitesource.agent.dependency.resolver.bower;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Collect dependencies using 'bower ls' command.
 *
 * @author eugen.horovitz
 */
public class BowerLsJsonDependencyCollector extends NpmLsJsonDependencyCollector {

    /* --- Statics Members --- */
    private final Logger logger = LoggerFactory.getLogger(BowerLsJsonDependencyCollector.class);
    private static final String BOWER_COMMAND = NpmLsJsonDependencyCollector.isWindows() ? "bower.cmd" : "bower";
    private static final String VERSION = "version";
    private static final String PKG_META = "pkgMeta";
    private static final String RESOLUTION = "_resolution";
    private static final String TYPE = "type";
    private static final String TAG = "tag";
    private static final String NAME = "name";
    private static final String MISSING = "missing";
    private static final String DEPENDENCIES = "dependencies";

    /* --- Constructors --- */

    public BowerLsJsonDependencyCollector(long npmTimeoutDependenciesCollector) {
        super(false, npmTimeoutDependenciesCollector, false);
    }

    /* --- Overridden methods --- */

    @Override
    protected String[] getInstallParams() {
        return new String[]{BOWER_COMMAND, INSTALL_COMMAND};
    }

    @Override
    protected String[] getLsCommandParamsJson() {
        return new String[]{BOWER_COMMAND, NpmLsJsonDependencyCollector.LS_COMMAND, NpmLsJsonDependencyCollector.LS_PARAMETER_JSON};
    }

    @Override
    protected DependencyInfo getDependency(String dependencyAlias, JSONObject jsonObject) {
        String version = "";
        String name = "";
        boolean unmetDependency = false;

        if (jsonObject.has(MISSING) && jsonObject.getBoolean(MISSING)) {
            unmetDependencyLog(dependencyAlias);
            return null;
        }
        if (jsonObject.has(PKG_META)) {
            JSONObject metaData = jsonObject.getJSONObject(PKG_META);
            if (metaData.has(RESOLUTION)) {
                JSONObject resolution = metaData.getJSONObject(RESOLUTION);
                String resolutionType = resolution.getString(TYPE);
                if (metaData.has(NAME)) {
                    name = metaData.getString(NAME);
                } else {
                    unmetDependency = true;
                }
                if (resolutionType.equals(TAG) || resolutionType.equals(VERSION)) {
                    version = metaData.getString(VERSION);
                } else {
                    logger.warn("We were not able to allocate the bower version for '{}' in you bower.json file." +
                            "At the moment we only support tag, so please modify your bower.json " +
                            "accordingly and run the plugin again.", name);
                    return null;
                }
            } else {
                unmetDependency = true;
            }
        } else {
            unmetDependency = true;
        }

        if (unmetDependency) {
            unmetDependencyLog(dependencyAlias);
            return null;
        }

        DependencyInfo dependency = new DependencyInfo();
        dependency.setGroupId(name);
        dependency.setArtifactId(name);
        dependency.setVersion(version);
        dependency.setDependencyType(DependencyType.BOWER);
        return dependency;
    }

    @Override
    protected void getDependencies(JSONObject jsonObject, String rootDirectory, Collection<DependencyInfo> dependencies) {
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
                            Collection<DependencyInfo> childDependencies = new ArrayList<>();
                            getDependencies(dependencyJsonObject, rootDirectory, childDependencies);
                            dependency.getChildren().addAll(childDependencies);
                        }
                    }
                }
            }
        }
    }

    private void unmetDependencyLog(String dependencyAlias) {
        logger.warn("Unmet dependency --> {}", dependencyAlias);
    }
}