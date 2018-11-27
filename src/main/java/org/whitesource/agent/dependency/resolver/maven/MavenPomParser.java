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
package org.whitesource.agent.dependency.resolver.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.IBomParser;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents an MAVEN pom.xml file.
 *
 * @author eugen.horovitz
 */
public class MavenPomParser extends MavenTreeDependencyCollector implements IBomParser {
    public final String COULD_NOT_PARSE_POM_FILE = "Could not parse pom file ";

    /* --- Static members --- */
    private static final String VERSION_REGEX = "(\\d+\\.\\d+(\\.\\d+)?(?:-\\w+(?:\\.\\w+)*)?(?:\\+\\w+)?)";

    private final Logger logger = LoggerFactory.getLogger(MavenPomParser.class);
    private boolean ignorePomModules;

    /* --- Constructor --- */

    public MavenPomParser(boolean ignorePomModules) {
        reader = new MavenXpp3Reader();
        this.ignorePomModules = ignorePomModules;
    }

    /* --- Members --- */

    private final MavenXpp3Reader reader;

    @Override
    public BomFile parseBomFile(String bomPath) {
        Model model = getModel(bomPath);
        if (model != null && model.getArtifactId() != null) {
            return new BomFile(model.getGroupId(), model.getArtifactId(),model.getVersion(),bomPath);
        }
        return null;
    }

    public List<DependencyInfo> parseDependenciesFromPomXml(String bomPath) {
        Model model = getModel(bomPath);
        if (model != null && model.getArtifactId() != null && (!ignorePomModules || !model.getPackaging().equals(Constants.POM))) {
            // ignoring POM modules if 'ignorePosModule=true'
            List<Dependency> directDependencies = Collections.emptyList();
            List<Dependency> managementDependencies = Collections.emptyList();

            if (model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null) {
                managementDependencies = model.getDependencyManagement().getDependencies();
            }
            if (model.getDependencies() != null) {
                directDependencies = model.getDependencies();
            }
            List<Dependency> dependencies = new LinkedList<>();
            dependencies.addAll(directDependencies);
            dependencies.addAll(managementDependencies);
            List<DependencyInfo> dependenciesInfo = new LinkedList<>();
            //in case the 'properties' node contains version data - extract it to be used later
            HashMap<String, String> versionDependencyMap = new HashMap<>();
            String key, value;
            for (Map.Entry<Object, Object> versionDependency : model.getProperties().entrySet()) {
                key = Constants.DOLLAR + Constants.OPEN_CURLY_BRACKET + String.valueOf(versionDependency.getKey()) + Constants.CLOSE_CURLY_BRACKET;
                value = String.valueOf(versionDependency.getValue());
                if (!value.contains(Constants.DOLLAR)) {
                    versionDependencyMap.put(key, value);
                }
            }
            Pattern versionPattern = Pattern.compile(VERSION_REGEX);
            Matcher matcher;
            for (Dependency dependency : dependencies) {
                String version;
                if (versionDependencyMap.containsKey(dependency.getVersion())){
                    version = versionDependencyMap.get(dependency.getVersion());
                } else {
                    version = dependency.getVersion();
                }
                // ignoring dependencies without version or not a valid version (e.g.  <version>${dependency.alfresco-messaging-repo.version}</version>)
                if (version == null){
                    continue;
                } else {
                    matcher = versionPattern.matcher(version);
                    if (matcher.find() == false){
                        continue;
                    }
                }

                // extracting the dependency's JAR (or WAR, TGZ, ect) file
                String shortName;
                if (StringUtils.isBlank(dependency.getClassifier())) {
                    shortName = dependency.getArtifactId() + Constants.DASH + dependency.getVersion() + Constants.DOT + dependency.getType();
                } else {
                    String nodePackaging = dependency.getType();
                    if (nodePackaging.equals(TEST_JAR)) {
                        nodePackaging = Constants.JAR;
                    }
                    shortName = dependency.getArtifactId() + Constants.DASH + dependency.getVersion() + Constants.DASH + dependency.getClassifier() + Constants.DOT + nodePackaging;
                }
                if (StringUtils.isBlank(M2Path)){
                    this.M2Path = getMavenM2Path(Constants.DOT);
                }
                String filePath = Paths.get(M2Path, dependency.getGroupId().replace(Constants.DOT, File.separator), dependency.getArtifactId(), dependency.getVersion(), shortName).toString();
                if (new File(filePath).exists()) {
                    String sha1 = getSha1(filePath);
                    if (!sha1.isEmpty()) {
                        DependencyInfo dependencyInfo = new DependencyInfo(dependency.getGroupId(), dependency.getArtifactId(), version);
                        dependencyInfo.setDependencyType(DependencyType.MAVEN);
                        dependencyInfo.setScope(dependency.getScope());
                        dependencyInfo.setType(dependency.getType());
                        dependencyInfo.setSystemPath(filePath);
                        dependencyInfo.setSha1(sha1);
                        dependencyInfo.setDependencyFile(bomPath);
                        dependenciesInfo.add(dependencyInfo);
                    }
                }
            }
            return dependenciesInfo;
        }
        return Collections.emptyList();
    }

    private Model getModel(String bomPath){
        Model model = null;
        try {
            try (FileReader fileReader = new FileReader(bomPath)) {
                model = reader.read(fileReader);
            }
        } catch (Exception e) {
            logger.debug(COULD_NOT_PARSE_POM_FILE + bomPath);
        }
        return model;
    }
}