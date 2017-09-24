package org.agent.dependencyResolver.nuget;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackage;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackages;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackagesConfigXmlParser;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yossi.weinberg on 7/21/2017.
 */
public class NugetPackagesConfigXmlParserTest {

    /* --- Static members --- */

    private static final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");


    /* --- Tests --- */

    @Test
    public void parseNugetDependenciesFromXml() throws JAXBException {
        File xmlFile = new File(JAVA_TEMP_DIR + "file.xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(NugetPackages.class);

        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        NugetPackages testNugetPackages = createDummyObject();
        jaxbMarshaller.marshal(testNugetPackages, xmlFile);
//        jaxbMarshaller.marshal(testNugetPackages, System.out); // // console output pretty printed


        NugetPackagesConfigXmlParser parser = new NugetPackagesConfigXmlParser(xmlFile);

        NugetPackages packages = parser.parsePackagesConfigFile();
        Assert.assertNotNull("Object was deserialized", packages);
        Assert.assertEquals(5, packages.getNugets().size());

        xmlFile.delete();
    }


    /* --- Private methods --- */

    private NugetPackages createDummyObject() {
        NugetPackages packages = new NugetPackages();
        List<NugetPackage> nugetPackages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            NugetPackage nugetPackage = new NugetPackage("nuget" + i, "1.0." + i);
            nugetPackages.add(nugetPackage);
        }
        packages.setNugets(nugetPackages);
        return packages;
    }
}
