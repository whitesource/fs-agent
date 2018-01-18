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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.sax.SAXSource;
import java.io.*;
import java.util.LinkedList;
import java.util.List;

/**
 * @author yossi.weinberg
 */
public class NugetPackagesConfigXmlParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(NugetPackagesConfigXmlParser.class);

    private static final JAXBContext jaxbContext;

    /* --- Members --- */

    private File xml;

    private NugetConfigFileType nugetConfigFileType;

    static {
        JAXBContext tempJaxbContext = null;
        try {
            tempJaxbContext = JAXBContext.newInstance(PackageReference.class, NugetPackages.class, NugetCsprojPackages.class);
        } catch (JAXBException e) {
            // todo
        }
        jaxbContext = tempJaxbContext;
    }

    /* --- Constructors --- */

    public NugetPackagesConfigXmlParser(File xml, NugetConfigFileType nugetConfigFileType) {
        this.xml = xml;
        this.nugetConfigFileType = nugetConfigFileType;
    }

    /* --- Public methods --- */

    public NugetPackages parsePackagesConfigFile() {
        NugetPackages packages = null;
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            if (this.nugetConfigFileType == NugetConfigFileType.CONFIG_FILE_TYPE) {
                packages = (NugetPackages) unmarshaller.unmarshal(xml);
            } else {
                NugetCsprojPackages csprojPackages = (NugetCsprojPackages) unmarshallWithFilter(unmarshaller);
                packages = getNugetPackagesFromCsproj(csprojPackages);
            }
        } catch (JAXBException | IOException | SAXException e) {
            logger.warn("Unable to parse suspected Nuget package senderConfiguration file {}", xml, e.getMessage());
        }
        return packages;
    }

    private NugetPackages getNugetPackagesFromCsproj(NugetCsprojPackages csprojPackages) {
        List<NugetPackage> nugetPackages = new LinkedList<>();
        for (NugetCsprojItemGroup csprojPackage : csprojPackages.getNugets()) {
            for(PackageReference packageReference : csprojPackage.getPackageReference()) {
                if (packageReference != null && packageReference.getPkgName() != null && packageReference.getPkgVersion() != null) {
                    nugetPackages.add(new NugetPackage(packageReference.getPkgName(), packageReference.getPkgVersion()));
                }
            }
        }
        NugetPackages nugetPackagesResult = new NugetPackages();
        nugetPackagesResult.setNugets(nugetPackages);
        return nugetPackagesResult;
    }

    public Object unmarshallWithFilter(Unmarshaller unmarshaller) throws IOException, JAXBException, SAXException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        NamespaceFilter inFilter = new NamespaceFilter(null, false);
        inFilter.setParent(reader);
        //Prepare the input, in this case a java.io.File (output)
        InputSource is = new InputSource(new FileInputStream(this.xml));
        //Create a SAXSource specifying the filter
        SAXSource source = new SAXSource(inFilter, is);
        return unmarshaller.unmarshal(source);
    }

    /* --- Nested Classes --- */

    // helper class that ignores namespaces in xml
    public class NamespaceFilter extends XMLFilterImpl {
        private String usedNamespaceUri;
        private boolean addNamespace;
        private boolean addedNamespace = false;

        public NamespaceFilter(String namespaceUri,
                               boolean addNamespace) {
            super();
            if (addNamespace) {
                this.usedNamespaceUri = namespaceUri;
            } else {
                this.usedNamespaceUri = "";
            }
            this.addNamespace = addNamespace;
        }

        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            if (addNamespace) {
                startControlledPrefixMapping();
            }
        }

        @Override
        public void startElement(String arg0, String arg1, String arg2,
                                 Attributes arg3) throws SAXException {
            super.startElement(this.usedNamespaceUri, arg1, arg2, arg3);
        }

        @Override
        public void endElement(String arg0, String arg1, String arg2)
                throws SAXException {
            super.endElement(this.usedNamespaceUri, arg1, arg2);
        }

        @Override
        public void startPrefixMapping(String prefix, String url) throws SAXException {
            if (addNamespace) {
                this.startControlledPrefixMapping();
            }
        }

        private void startControlledPrefixMapping() throws SAXException, SAXException {
            if (this.addNamespace && !this.addedNamespace) {
                //We should add namespace since it is set and has not yet been done.
                super.startPrefixMapping("", this.usedNamespaceUri);
                //Make sure we dont do it twice
                this.addedNamespace = true;
            }
        }
    }
}
