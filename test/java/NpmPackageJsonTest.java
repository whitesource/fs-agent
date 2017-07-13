import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.NpmPackageJsonFile;

import java.nio.file.Paths;

/**
 *@author eugen.horovitz
 */
public class NpmPackageJsonTest {

    @Test
    public void shouldLoadOptionalDependencies() {
        String path = Paths.get(TestHelper.FOLDER_TO_TEST, TestHelper.SUBFOLDER_WITH_OPTIONAL_DEPENDENCIES).toString();
        NpmPackageJsonFile file = NpmPackageJsonFile.parseNpmPackageJsonFile(path);
        Assert.assertTrue(file.getOptionalDependencies().keySet().size() > 0);
        Assert.assertTrue(file.getDependencies().keySet().size() > 0);
    }
}
