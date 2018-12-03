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
import org.slf4j.Logger;
import org.whitesource.agent.dependency.resolver.npm.RegistryType;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.Constants;

import java.util.Map;

/**
 * @author eugen.horovitz
 */
public class BomFile {

    /* --- Members --- */

    private final String name;
    private final String version;
    private String groupId;
    private String sha1;
    private String fileName;
    private final String localFileName;
    private final Map<String, String> dependencies;
    private Map<String, String> optionalDependencies;
    private String resolved;
    private boolean scopedPackage;
    private RegistryType registryType;

    public static String DUMMY_PARAMETER_SCOPE_PACKAGE = "{dummyParameterOfScopePackage}";
    private final Logger logger = LoggerFactory.getLogger(BomFile.class);
    private static String NPM_REGISTRY = "registry.npmjs.org";
    private static final String NPM_REGISTRY1 = "npm/registry/";
    private static final String SCOPED_PACKAGE = "@";
    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    /* --- Constructors --- */

    public BomFile(String name, String version, String sha1, String fileName, String localFileName,
                   Map<String, String> dependencies, Map<String, String> optionalDependencies, String resolved, RegistryType registryType) {
        this.name = name;
        this.version = version;
        this.sha1 = sha1;
        this.fileName = fileName;
        this.localFileName = localFileName;
        this.dependencies = dependencies;
        this.optionalDependencies = optionalDependencies;
        this.resolved = resolved;
        this.scopedPackage = false;
        this.groupId = null;
        this.registryType = registryType;
    }

    public BomFile(String groupId, String artifactId, String version, String bomPath) {
        this(artifactId, version, null, null, bomPath, null, null, null, null);
        this.groupId = groupId;
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

    public String getGroupId() {
        return groupId;
    }

    public static String getUniqueDependencyName(String name, String version) {
        return name + SCOPED_PACKAGE + version.replace("v", Constants.EMPTY_STRING);
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

    public RegistryType getRegistryType() {
        return this.registryType;
    }

    public String getRegistryPackageUrl() {
        String registryPackageUrl = null;
        if (StringUtils.isEmpty(this.resolved)) {
            logger.debug("resolved url in file is empty");
            return StringUtils.EMPTY;
        }
        logger.debug("resolved url in file = " + this.resolved);
        if (this.resolved.contains("git+") || this.resolved.contains("github:")) {
            // temp solution for WSE-204
            logger.info("This configuration - " + this.name + " (remote repository packages) is not supported by WhiteSource. Please use direct URL package references.");
            return StringUtils.EMPTY;
        }

        if (this.registryType == RegistryType.ARTIFACTORY) {
            // example: change this url: http://localhost:8081/artifactory/api/npm/npmExample/q-1.5.1.tgz to http://localhost:8081/artifactory/api/npm/npmExample/q-1.5.1.json
            registryPackageUrl = this.resolved.substring(0, this.resolved.length() - 3);
            registryPackageUrl = registryPackageUrl + "json";
        } else if (this.resolved.contains(SCOPED_PACKAGE) || this.registryType != RegistryType.NPM_REGISTRY) {
            // resolve rare cases where the package's name is a sub-string of the registry's url
            int npmRegistryIndex = this.resolved.indexOf(NPM_REGISTRY1);
            registryPackageUrl = this.resolved.substring(0, this.resolved.indexOf(this.name, npmRegistryIndex) + this.name.length());
            int lastSlashIndex = registryPackageUrl.lastIndexOf('/');
            registryPackageUrl = registryPackageUrl.substring(0, lastSlashIndex) + DUMMY_PARAMETER_SCOPE_PACKAGE + registryPackageUrl.substring(lastSlashIndex + 1);
            this.scopedPackage = true;
        } else {
            String urlName = Constants.FORWARD_SLASH + this.name + Constants.FORWARD_SLASH;
            registryPackageUrl = this.resolved.substring(0, this.resolved.indexOf(urlName) + urlName.length());
            registryPackageUrl = registryPackageUrl + this.version;
            if (registryPackageUrl.startsWith(HTTPS)) {
                String urlWithoutHttps = registryPackageUrl.substring(HTTPS.length());
                registryPackageUrl = HTTP + urlWithoutHttps;
            }
        }
        logger.debug("resolved url in link = " + registryPackageUrl);
        return registryPackageUrl;
    }

    public boolean isScopedPackage() {
        return this.scopedPackage;
    }

    @Override
    public String toString() {
        return String.join(Constants.DOT, getGroupId(), getName(), getVersion());
    }
}