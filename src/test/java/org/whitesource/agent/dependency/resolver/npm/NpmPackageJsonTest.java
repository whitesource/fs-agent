package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.BomFile;

import java.nio.file.Paths;

/**
 *@author eugen.horovitz
 */
public class NpmPackageJsonTest {

    @Test
    public void shouldLoadOptionalDependencies() {
        String path = TestHelper.SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES.getAbsolutePath();
        BomFile file = new NpmBomParser().parseBomFile(path);
        Assert.assertTrue(file.getOptionalDependencies().keySet().size() > 0);
        Assert.assertTrue(file.getDependencies().keySet().size() > 0);
    }
}