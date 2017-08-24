package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;

/**
 * Created by yossi.weinberg on 7/21/2017.
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
            logger.warn("Unable to parse suspected Nuget package config file {}", xml);
        }
        return packages;
    }
}
