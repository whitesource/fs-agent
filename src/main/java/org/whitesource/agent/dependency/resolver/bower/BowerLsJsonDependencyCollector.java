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
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;

/**
 * Collect dependencies using 'bower ls' command.
 *
 * @author eugen.horovitz
 */
public class BowerLsJsonDependencyCollector extends NpmLsJsonDependencyCollector {

    /* --- Statics Members --- */

    private static final String BOWER_COMMAND = NpmLsJsonDependencyCollector.isWindows() ? "bower.cmd" : "bower";
    private static final String VERSION = "version";
    private static final String PKG_META = "pkgMeta";
    public static final String NAME = "name";

    /* --- Constructors --- */

    public BowerLsJsonDependencyCollector() {
        super(false);
    }

    /* --- Overridden methods --- */

    @Override
    protected String[] getLsCommandParams() {
        return new String[]{BOWER_COMMAND, NpmLsJsonDependencyCollector.LS_COMMAND, NpmLsJsonDependencyCollector.LS_PARAMETER_JSON};
    }

    @Override
    protected DependencyInfo getDependency(String dependencyAlias, JSONObject jsonObject) {
        String version = "";
        String name = "";

        if (jsonObject.has(PKG_META)) {
            JSONObject metaData = jsonObject.getJSONObject(PKG_META);
            if (metaData.has(VERSION)) {
                version = metaData.getString(VERSION);
            }
            if (metaData.has(NAME)) {
                name = metaData.getString(NAME);
            }
        }

        DependencyInfo dependency = new DependencyInfo();
        dependency.setGroupId(name);
        dependency.setArtifactId(name);
        dependency.setVersion(version);
        dependency.setDependencyType(DependencyType.BOWER);
        return dependency;
    }
}