import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.NpmPackageJsonFile;
/**
 * Created by eugen.horovitz on 6/26/2017.
 */
public class NpmPackageJsonTest {

    @Test
    public void shouldLoadOptionalDependencies() {
        NpmPackageJsonFile file = NpmPackageJsonFile.parseNpmPackageJsonFile("C:\\Users\\eugen\\WebstormProjects\\apostrophe-master\\node_modules\\chokidar\\package.json");
        Assert.assertTrue(file.getOptionalDependencies().keySet().size() > 0);
        Assert.assertTrue(file.getDependencies().keySet().size() > 0);
    }
}
