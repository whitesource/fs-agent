package org.whitesource.agent.dependency.resolver.npm;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.BomFile;

/**
 *@author eugen.horovitz
 */
public class NpmPackageJsonTest {

    @Ignore
    @Test
    public void shouldLoadOptionalDependencies() {
        String path = TestHelper.SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES.getAbsolutePath();
        BomFile file = new NpmBomParser().parseBomFile(path);
        Assert.assertTrue(file.getOptionalDependencies().keySet().size() > 0);
        Assert.assertTrue(file.getDependencies().keySet().size() > 0);
    }
}