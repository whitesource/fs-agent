package org.whitesource.agent.dependency.resolver.nuget;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetConfigFileType;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackage;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackages;
import org.whitesource.agent.dependency.resolver.nuget.packagesConfig.NugetPackagesConfigXmlParser;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by yossi.weinberg on 7/21/2017.
 */
public class NugetPackagesConfigXmlParserTest {

    /* --- Tests --- */

    @Ignore
    @Test
    public void parseNugetDependenciesFromXml() throws JAXBException {
        File xmlFile = TestHelper.getFileFromResources("resolver/nuget/project-name.config");
        JAXBContext jaxbContext = JAXBContext.newInstance(NugetPackages.class);

        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        NugetPackages testNugetPackages = createDummyObject();
        jaxbMarshaller.marshal(testNugetPackages, xmlFile);

        NugetPackagesConfigXmlParser parser = new NugetPackagesConfigXmlParser(xmlFile, NugetConfigFileType.CONFIG_FILE_TYPE);

        Set<DependencyInfo> dependecnies = parser.parsePackagesConfigFile(false, xmlFile.getAbsolutePath());
        Assert.assertNotNull("Object was deserialized", dependecnies);
        Assert.assertEquals(5, dependecnies.size());
    }


    /* --- Private methods --- */

    private NugetPackages createDummyObject() {
        NugetPackages packages = new NugetPackages();
        List<NugetPackage> nugetPackages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            NugetPackage nugetPackage = new NugetPackage("nuget" + i, "1.0." + i);
            nugetPackages.add(nugetPackage);
        }
        packages.setNugetPackages(nugetPackages);
        return packages;
    }
}
