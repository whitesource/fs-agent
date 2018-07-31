package org.whitesource.fs;

import ch.qos.logback.classic.util.ContextInitializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ProjectsSenderMock;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import static org.whitesource.fs.Main.LOGBACK_FSA_XML;

/**
 * @author chen.luigi
 */
public class ViaTest {

    /* --- Static members --- */

    private static Logger logger = null;
    private static final String inputDir = File.separator + "test_input" + File.separator;
    private static final String INPUT_DIR = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(inputDir);
    private static final String CONFIG_PATH = inputDir + File.separator + "whitesource-fs-agent.ksa.config";
    private static final String CONFIG = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(CONFIG_PATH);

    /* --- Private Members --- */

    private Properties config;
    private FSAConfiguration fsaConfiguration;
    private ProjectsSenderMock projectsSender;

    /* --- Setup --- */

    @Before
    public void setUp() throws IOException {
        config = new Properties();
        config.load(new FileInputStream(CONFIG));
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, LOGBACK_FSA_XML);
        logger = LoggerFactory.getLogger(ViaTest.class);
        fsaConfiguration = new FSAConfiguration(config);
    }

    /* --- Test Methods --- */

    @Test
    public void testKsa() {
        logger.info("**** Starting Via Maven Test ****");
        String proj = INPUT_DIR + "ksa" + File.separator + "ksa-web-core" + File.separator;
        String configFile = INPUT_DIR + File.separator + "whitesource-fs-agent.ksa.config";
        String args[] = {"-appPath", proj + "target" + File.separator + "ksa-web-core-3.9.0.jar", "-d", proj, "-c", configFile};
        try {
            projectsSender = new ProjectsSenderMock(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), fsaConfiguration.getRequest(), new FileSystemAgentInfo());
            org.whitesource.fs.Main.endToEndIntegration(args, projectsSender);
        } catch (RuntimeException e){
            logger.error("Failed to send test request {}", e.getMessage());

        } catch (Exception e) {
            logger.error("Failed to send test request {}", e.getMessage());
        }
        String jsonResult = projectsSender.getJson();
        Assert.assertTrue(jsonResult.contains("com.ksa.web.struts2.views.freemarker.ShiroFreemarkerManager:forTest"));
    }

}
