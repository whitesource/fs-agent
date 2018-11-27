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

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.BomParser;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class represents an NPM package.json file.
 *
 * @author eugen.horovitz
 */
public class NpmBomParser extends BomParser {

    /* --- Static members --- */

    private static final String NPM_REGISTRY = "registry.npmjs.org";
    private static final String VISUALSTUDIO_REGISTRY = "pkgs.visualstudio";
    private static final String ARTIFACTORY_FORWARD_SLASH = "/artifactory/";
    private static String OPTIONAL_DEPENDENCIES = "optionalDependencies";
    private static String SHA1 = "_shasum";
    private static String NPM_PACKAGE_FORMAT = "{0}-{1}.tgz";
    private static String RESOLVED = "_resolved";

    private final Logger logger = LoggerFactory.getLogger(NpmBomParser.class);

    /* --- Protected methods --- */

    protected String getVersion(JSONObject json, String fileName) {
        if (json.has(Constants.VERSION)) {
            return json.getString(Constants.VERSION);
        }
        logger.debug("version not found in file {}", fileName);
        return Constants.EMPTY_STRING;
    }

    /* --- Static methods --- */

    public static String getNpmArtifactId(String name, String version) {
        return MessageFormat.format(NPM_PACKAGE_FORMAT, name, version);
    }

    /* --- Overridden methods --- */

    @Override
    protected BomFile parseBomFile(String jsonText, String localFileName) {
        JSONObject json = new JSONObject(jsonText);
        String name = json.getString(Constants.NAME);
        String version = getVersion(json, localFileName);
        Map dependencies = getDependenciesFromJson(json, Constants.DEPENDENCIES);
        Map optionalDependencies = getDependenciesFromJson(json, OPTIONAL_DEPENDENCIES);
        String fileName = getFilename(name, version);
        String sha1 = Constants.EMPTY_STRING;
        String resolved = null;
        if(json.has(RESOLVED)) {
            resolved = json.getString(RESOLVED);
        }

        // optional fields for packageJson parser
        if (json.has(SHA1)) {
            sha1 = json.getString(SHA1);
        } else {
            logger.debug("shasum not found in file {}", localFileName);
        }

        RegistryType registryType = getRegistryType(resolved);

        BomFile bom = new BomFile(name, version, sha1, fileName, localFileName, dependencies, optionalDependencies, resolved, registryType);
        return bom;
    }

    private RegistryType getRegistryType(String resolved) {
        RegistryType registryType = null;
        if (StringUtils.isNotBlank(resolved)) {
            if (resolved.contains(ARTIFACTORY_FORWARD_SLASH)) {
                registryType = RegistryType.ARTIFACTORY;
            } else if (resolved.contains(VISUALSTUDIO_REGISTRY)) {
                registryType = RegistryType.VISUAL_STUDIO;
            } else if (resolved.contains(NPM_REGISTRY)) {
                registryType = RegistryType.NPM_REGISTRY;
            } else {
                registryType = RegistryType.OTHER;
            }
        }
        return registryType;
    }

    @Override
    protected String getFilename(String name, String version) {
        return getNpmArtifactId(name, version);
    }

    /* --- Private methods --- */

    private Map<String, String> getDependenciesFromJson(JSONObject json, String keyJson) {
        Map<String, String> nameVersionMap = new HashMap<>();
        if (json.has(keyJson)) {
            JSONObject optionals = json.getJSONObject(keyJson);
            Iterator<String> keys = optionals.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                nameVersionMap.put(key, optionals.getString(key));
            }
        }
        return nameVersionMap;
    }
}