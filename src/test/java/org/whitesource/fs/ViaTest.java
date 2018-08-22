package org.whitesource.fs;

import ch.qos.logback.classic.util.ContextInitializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ProjectsSenderMock;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

import static org.whitesource.fs.Main.LOGBACK_FSA_XML;

/**
 * @author chen.luigi
 */
public class ViaTest {

    /* --- Static members --- */

    private static Logger logger;
    private static final String inputDir = File.separator + "test_input" + File.separator;
    private static final String INPUT_DIR = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(inputDir);
    private static final String CONFIG_PATH = inputDir + File.separator + "whitesource-fs-agent.config";
    private static final String CONFIG = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(CONFIG_PATH);
    private static final String KSA_EFFECTED_JAR = "ksa-web-core-3.9.0.jar";
    private static final String TARGET = "target";
    private static final String GRADLE = "gradle";
    private static final String GRADLE_EFFECTED_JAR = "elads-1.0-SNAPSHOT.jar";
    private static final String APP_PATH = "-appPath";

    /* --- Private Members --- */

    private FSAConfigProperties config;
    private FSAConfiguration fsaConfiguration;
    private ProjectsSenderMock projectsSender;

    /* --- Setup --- */

    @Before
    public void setUp() throws IOException {
        config = new FSAConfigProperties();
        config.load(new FileInputStream(CONFIG));
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, LOGBACK_FSA_XML);
        logger = LoggerFactory.getLogger(ViaTest.class);
        fsaConfiguration = new FSAConfiguration(config);
    }

    /* --- Test Methods --- */

    @Test
    public void testKsa() {
        logger.info("**** Starting Via Maven Test ****");
        config.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "via-test-ksa");
        String projectPath = INPUT_DIR + "ksa" + File.separator + "ksa-web-core" + File.separator;
        String args[] = {APP_PATH, projectPath + TARGET + File.separator + KSA_EFFECTED_JAR, "-d", projectPath, "-c", CONFIG};
        try {
            projectsSender = new ProjectsSenderMock(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), fsaConfiguration.getRequest(), new FileSystemAgentInfo());
            org.whitesource.fs.Main.endToEndIntegration(args, projectsSender);
        } catch (Exception e) {
            logger.error("Failed to send maven test request {}", e.getMessage());
        }
        String jsonResult = projectsSender.getJson();
        Assert.assertTrue(jsonResult.contains("com.ksa.web.struts2.views.freemarker.ShiroFreemarkerManager:forTest"));
    }

    @Test
    public void testGradle() {
        logger.info("**** Starting Via Gradle Test ****");
        config.setProperty(ConfigPropertyKeys.PROJECT_NAME_PROPERTY_KEY, "via-test-gradle");
        String projectPath = INPUT_DIR + GRADLE;
        String args[] = {APP_PATH, projectPath + File.separator + "build" + File.separator + "libs" + File.separator + GRADLE_EFFECTED_JAR, "-d", projectPath, "-c", CONFIG};
        try {
            projectsSender = new ProjectsSenderMock(fsaConfiguration.getSender(), fsaConfiguration.getOffline(), fsaConfiguration.getRequest(), new FileSystemAgentInfo());
            org.whitesource.fs.Main.endToEndIntegration(args, projectsSender);
        } catch (Exception e) {
            logger.error("Failed to send gradle test request {}", e.getMessage());
        }
        String jsonResult = projectsSender.getJson();
        Assert.assertTrue(jsonResult.contains("test.Hello2:eat"));
    }

}
