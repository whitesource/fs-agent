package org.whitesource.fs;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ProjectsSenderMock;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author chen.luigi
 */
public class ViaTest {

    private static final String inputDir = File.separator + "test_input" + File.separator;
    private static final String INPUT_DIR = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(inputDir);
    private static final String CONFIG_PATH = inputDir + File.separator + "whitesource-fs-agent-ksa.config";
    private static final String CONFIG = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(CONFIG_PATH);

    private Properties config;
    private FSAConfiguration fsaConfiguration;
    ProjectsSenderMock projectsSender;

    @Before
    public void setUp() throws IOException {
        config = new Properties();
        config.load(new FileInputStream(CONFIG));
        //config.setProperty(ConfigPropertyKeys.VIA_DEBUG, "true");
        fsaConfiguration = new FSAConfiguration(config);

    }

    @Test
    public void testKsa() {

        String proj = INPUT_DIR + "ksa" + File.separator + "ksa-web-core" + File.separator;
        String args[] = {"-appPath", proj + "target" + File.separator + "ksa-web-core-3.9.0.jar", "-d", proj, "-c", INPUT_DIR + File.separator + "whitesource-fs-agent-ksa.config"};
        try {
            projectsSender = new ProjectsSenderMock(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), fsaConfiguration.getRequest(), new FileSystemAgentInfo());
            org.whitesource.fs.Main.mainTest(args, projectsSender);
        } catch (Exception e) {

        }
        String jsonResult = projectsSender.getJson();

        System.out.println(new Gson().toJson(jsonResult));
        Assert.assertTrue(jsonResult.contains("com.ksa.web.struts2.views.freemarker.ShiroFreemarkerManager:test"));

    }
}
