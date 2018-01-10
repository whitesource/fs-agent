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
package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;

/**
 * @author yossi.weinberg
 */
public class NugetPackagesConfigXmlParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(NugetPackagesConfigXmlParser.class);

    private static final JAXBContext jaxbContext;

    /* --- Members --- */

    private File xml;

    static {
        JAXBContext tempJaxbContext = null;
        try {
            tempJaxbContext = JAXBContext.newInstance(NugetPackages.class);
        } catch (JAXBException e) {
            // todo
        }
        jaxbContext = tempJaxbContext;
    }

    /* --- Constructors --- */

    public NugetPackagesConfigXmlParser(File xml) {
        this.xml = xml;
    }

    /* --- Public methods --- */

    public NugetPackages parsePackagesConfigFile() {
        NugetPackages packages = null;
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            packages = (NugetPackages) unmarshaller.unmarshal(xml);
        } catch (Exception e) {
            logger.warn("Unable to parse suspected Nuget package senderConfiguration file {}", xml);
        }
        return packages;
    }
}
