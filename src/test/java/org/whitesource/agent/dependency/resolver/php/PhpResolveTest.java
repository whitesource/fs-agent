package org.whitesource.agent.dependency.resolver.php;

import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.FileNotFoundException;
import java.nio.file.Paths;

/**
 * @author chen.luigi
 */
public class PhpResolveTest {

    PhpDependencyResolver phpDependencyResolver;
    private static final String COMPOSER_LOCK = "composer.lock";

    @Before
    public void setUp() throws Exception {
        phpDependencyResolver = new PhpDependencyResolver(true, true);
    }

    @Test
    public void resolveDependencies() throws FileNotFoundException {
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\php\\");
/*        ResolutionResult resolutionResult = phpDependencyResolver.resolveDependencies(folderPath, folderPath, null);
        Assert.assertTrue(resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies().size() == 28);*/
        /*JsonReader jsonReader = new JsonReader(new FileReader(folderPath + COMPOSER_LOCK));
        PhpPackage phpPackage = new Gson().fromJson(jsonReader, PhpPackage.class);
        Collection<PackageDev> packagesDev = phpPackage.getPackagesDev();*/
        //ResolutionResult resolutionResult = phpDependencyResolver.resolveDependencies(folderPath, null, null);
        phpDependencyResolver.resolveDependencies(null,folderPath,null);
    }
}
