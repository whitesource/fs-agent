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

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.CommandLineAgent;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.IBomParser;

import java.io.FileReader;
import java.io.IOException;

/**
 * This class represents an MAVEN pom.xml file.
 *
 * @author eugen.horovitz
 */
public class MavenParser implements IBomParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(MavenParser.class);

    /* --- Constructor --- */

    public MavenParser() {
        reader = new MavenXpp3Reader();
    }

    /* --- Members --- */

    private final MavenXpp3Reader reader;

    @Override
    public BomFile parseBomFile(String bomPath) {

        Model model = null;
        try {
            model = reader.read(new FileReader(bomPath));
        } catch (IOException e) {
            logger.debug("Could not parse pom file " + bomPath);
        } catch (XmlPullParserException e) {
            logger.debug("Could not parse pom file " + bomPath);
        }
        if(model!=null && model.getArtifactId()!=null) {
            return new BomFile(model.getGroupId(), model.getArtifactId(),model.getVersion(),bomPath);
        }
        return null;
    }

}
