package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yossi.weinberg on 7/21/2017.
 */
public class NugetPackagesConfigXmlParser {

    /* --- Static members --- */

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
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return packages;
    }
}
