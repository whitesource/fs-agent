package org.whitesource.agent.dependency.resolver.ruby;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.nio.file.Paths;

public class RubyDependencyResolverTest {

    RubyDependencyResolver rubyDependencyResolver;

    @Before
    public void setUp() throws Exception {
        rubyDependencyResolver = new RubyDependencyResolver(true, true, true);
    }

    @Test
    public void resolveDependencies() {
        String folderPath = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\ruby\\");
        ResolutionResult resolutionResult = rubyDependencyResolver.resolveDependencies(folderPath, folderPath, null);
        Assert.assertTrue(resolutionResult.getResolvedProjects().keySet().iterator().next().getDependencies().size() == 16);
    }
}