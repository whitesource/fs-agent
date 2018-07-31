package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.scm.ScmType;

import java.util.Collection;

public class ScmRepositoriesParserTest {

    @Ignore
    @Test
    public void shouldParse() {
        String repos = TestHelper.getFileFromResources("repos.json").getAbsolutePath();
        Collection<ScmConfiguration> configs = new ScmRepositoriesParser().parseRepositoriesFile(repos,
                ScmType.GIT.toString(), Constants.EMPTY_STRING, Constants.EMPTY_STRING, Constants.EMPTY_STRING);
        Assert.assertNotNull(configs);
        Assert.assertTrue(configs.size() > 0);
    }
}
