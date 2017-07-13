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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class represents an NPM package.json file.
 *
 * @author eugen.horovitz
 */
public class NpmPackageJsonFile {

    /* --- Static members --- */

    private static String OPTIONAL_DEPENDENCIES = "optionalDependencies";
    private static String NAME = "name";
    private static String VERSION = "version";
    private static String SHA1 = "_shasum";
    private static String DEPENDENCIES = "dependencies";
    private static String NPM_PACKAGE_FORMAT = "{0}-{1}.tgz";

    private static final Logger logger = LoggerFactory.getLogger(NpmPackageJsonFile.class);

    /* --- Members --- */

    private final String name;
    private final String version;
    private String sha1;
    private String fileName;
    private final String localFileName;
    private final Map<String, String> dependencies;
    private Map<String, String> optionalDependencies;

    /* --- Constructors --- */

    private NpmPackageJsonFile(String jsonText, String localFileName) {
        JSONObject json = new JSONObject(jsonText);
        name = json.getString(NAME);
        version = json.getString(VERSION);
        dependencies = getDependenciesFromJson(json, DEPENDENCIES);
        optionalDependencies = getDependenciesFromJson(json, OPTIONAL_DEPENDENCIES);
        this.localFileName = localFileName;
        fileName = getNpmArtifactId(name, version);

        // optional fields for packageJson parser
        if (json.has(SHA1)) {
            sha1 = json.getString(SHA1);
        } else {
            logger.debug("shasum not found in file {}", localFileName);
        }
    }

    /* --- Static methods --- */

    public static String getNpmArtifactId(String name, String version) {
        return MessageFormat.format(NPM_PACKAGE_FORMAT, name, version);
    }

    public static NpmPackageJsonFile parseNpmPackageJsonFile(String fileName) {
        NpmPackageJsonFile packageJsonFile = null;
        String json = null;
        try (InputStream is = new FileInputStream(fileName)) {
            json = IOUtils.toString(is);
            is.close();
        } catch (FileNotFoundException e) {
            logger.error("file Not Found: {}", fileName);
        } catch (IOException e) {
            logger.error("error getting file : {}", e.getMessage());
        }

        if (json != null) {
            try {
                packageJsonFile = new NpmPackageJsonFile(json, fileName);
            } catch (Exception e) {
                logger.debug("Invalid NPM package.json file {}", fileName);
            }
        }
        return packageJsonFile;
    }

    /* --- Public method --- */

    public boolean isValid() {
        return StringUtils.isNoneBlank(name, version);
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

    /* --- Getters --- */

    public String getName() {
        return name;
    }

    public String getLocalFileName() {
        return localFileName;
    }

    public String getVersion() {
        return version;
    }

    public String getSha1() {
        return sha1;
    }

    public String getFileName() {
        return fileName;
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getOptionalDependencies() {
        return optionalDependencies;
    }
}