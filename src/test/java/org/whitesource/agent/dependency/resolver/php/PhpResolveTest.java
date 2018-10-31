package org.whitesource.agent.dependency.resolver.php;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.FileNotFoundException;
import java.nio.file.Paths;

/**
 * @author chen.luigi
 */
public class PhpResolveTest {

    PhpDependencyResolver phpDependencyResolver;

    @Before
    public void setUp() throws Exception {
        phpDependencyResolver = new PhpDependencyResolver(false, true, false);
    }

    @Ignore
    @Test
    public void resolveDependencies() throws FileNotFoundException {
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\php\\");
        ResolutionResult resolutionResult = phpDependencyResolver.resolveDependencies(null, folderPath, null);
        Assert.assertTrue(resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies().size() == 5);
    }
}
