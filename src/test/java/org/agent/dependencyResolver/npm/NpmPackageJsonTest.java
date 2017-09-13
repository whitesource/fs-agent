package org.agent.dependencyResolver.npm;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.BomFile;
import org.whitesource.agent.dependency.resolver.npm.NpmBomParser;

import java.nio.file.Paths;

/**
 *@author eugen.horovitz
 */
public class NpmPackageJsonTest {

    @Test
    public void shouldLoadOptionalDependencies() {
        String testFolder = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        String path = Paths.get(testFolder, TestHelper.SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES).toString();
        BomFile file = new NpmBomParser().parseBomFile(path);
        Assert.assertTrue(file.getOptionalDependencies().keySet().size() > 0);
        Assert.assertTrue(file.getDependencies().keySet().size() > 0);
    }
}