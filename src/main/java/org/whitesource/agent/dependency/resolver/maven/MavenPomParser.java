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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.IBomParser;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * This class represents an MAVEN pom.xml file.
 *
 * @author eugen.horovitz
 */
public class MavenPomParser implements IBomParser {
    public final String COULD_NOT_PARSE_POM_FILE = "Could not parse pom file ";

    /* --- Static members --- */

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
        Model model = null;
        try {
            try(FileReader fileReader = new FileReader(bomPath)) {
                model = reader.read(fileReader);
            }
        } catch (IOException e) {
            logger.debug(COULD_NOT_PARSE_POM_FILE + bomPath);
        } catch (XmlPullParserException e) {
            logger.debug(COULD_NOT_PARSE_POM_FILE + bomPath);
        }
        if(model != null && model.getArtifactId() != null) {
            return new BomFile(model.getGroupId(), model.getArtifactId(),model.getVersion(),bomPath);
        }
        return null;
    }

    public List<DependencyInfo> parseDependenciesFromPomXml(String bomPath) {
        Model model = null;
        try {
            try(FileReader fileReader = new FileReader(bomPath)) {
                model = reader.read(fileReader);
            }
        } catch (IOException e) {
            logger.debug(COULD_NOT_PARSE_POM_FILE + bomPath);
        } catch (XmlPullParserException e) {
            logger.debug(COULD_NOT_PARSE_POM_FILE + bomPath);
        }
        if(model != null && model.getArtifactId() != null && (!ignorePomModules || !model.getPackaging().equals(Constants.POM))) {
            List<Dependency> directDependencies = Collections.emptyList();
            List<Dependency> managementDependencies = Collections.emptyList();

            if(model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null) {
                managementDependencies = model.getDependencyManagement().getDependencies();
            }
            if(model.getDependencies() != null) {
                directDependencies = model.getDependencies();
            }
            List<Dependency> dependencies = new LinkedList<>();
            dependencies.addAll(directDependencies);
            dependencies.addAll(managementDependencies);
            List<DependencyInfo> dependenciesInfo = new LinkedList<>();
            //extract Dependency:Version map
            HashMap<String,String> versionDependencyMap = new HashMap<>();
            String key;
            String value;
            for (Map.Entry<Object, Object> versionDependency : model.getProperties().entrySet()) {
                key = Constants.DOLLAR + Constants.OPEN_CURVY_BRACKET + String.valueOf(versionDependency.getKey()) + Constants.CLOSE_CURVY_BRACKET;
                value = String.valueOf(versionDependency.getValue());
                if(!value.contains(Constants.DOLLAR)) {
                    versionDependencyMap.put(key, value);
                }
            }
            for (Dependency dependency : dependencies) {
                String version;
                if (versionDependencyMap.containsKey(dependency.getVersion())){
                    version = versionDependencyMap.get(dependency.getVersion());
                } else {
                    version = dependency.getVersion();
                }
                DependencyInfo dependencyInfo = new DependencyInfo(dependency.getGroupId(), dependency.getArtifactId(),version);
                dependencyInfo.setDependencyType(DependencyType.MAVEN);
                dependencyInfo.setScope(dependency.getScope());
                dependencyInfo.setType(dependency.getType());
                dependencyInfo.setSystemPath(bomPath);
                dependenciesInfo.add(dependencyInfo);
            }
            return dependenciesInfo;
        }
        return Collections.emptyList();
    }
}