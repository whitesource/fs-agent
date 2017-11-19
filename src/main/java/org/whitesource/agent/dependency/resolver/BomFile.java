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
package org.whitesource.agent.dependency.resolver;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author eugen.horovitz
 */
public class BomFile {

    /* --- Members --- */

    private final String name;
    private final String version;
    private String sha1;
    private String fileName;
    private final String localFileName;
    private final Map<String, String> dependencies;
    private Map<String, String> optionalDependencies;

    /* --- Constructors --- */

    public BomFile(String name, String version, String sha1, String fileName, String localFileName,
                   Map<String, String> dependencies, Map<String, String> optionalDependencies) {
        this.name = name;
        this.version = version;
        this.sha1 = sha1;
        this.fileName = fileName;
        this.localFileName = localFileName;
        this.dependencies = dependencies;
        this.optionalDependencies = optionalDependencies;
    }

    /* --- Public method --- */

    public boolean isValid() {
        return StringUtils.isNoneBlank(name, version);
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

    public static String getUniqueDependencyName(String name, String version) {
        return name + "@" + version.replace("v", "");
    }

    public String getUniqueDependencyName() {
        return getUniqueDependencyName(name, version);
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getOptionalDependencies() {
        return optionalDependencies;
    }
}