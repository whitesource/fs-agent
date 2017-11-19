package org.whitesource.fs.configuration;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.scm.ScmType;

import java.nio.file.Paths;
import java.util.Collection;

public class ScmRepositoriesParserTest {

    @Test
    @Ignore
    public void shouldParse() {
        String currentDirectory = System.getProperty("user.dir");
        String repos = Paths.get(currentDirectory, "\\src\\test\\resources\\repos.json").toString();
        Collection<ScmConfiguration> configs = ScmRepositoriesParser.parseRepositoriesFile(repos, ScmType.GIT.toString(), "", "", "");

        Assert.assertNotNull(configs);
        Assert.assertTrue(configs.size() > 0);
    }
}
