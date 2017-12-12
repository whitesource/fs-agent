package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.scm.ScmType;

import java.nio.file.Paths;
import java.util.Collection;

public class ScmRepositoriesParserTest {

    @Test
    public void shouldParse() {
        String repos = TestHelper.getFileFromResources("repos.json").getAbsolutePath();
        Collection<ScmConfiguration> configs = ScmRepositoriesParser.parseRepositoriesFile(repos, ScmType.GIT.toString(), "", "", "");

        Assert.assertNotNull(configs);
        Assert.assertTrue(configs.size() > 0);
    }
}
