package org.whitesource.fs.via;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.Constants;
import org.whitesource.agent.ProjectsSender;
import org.whitesource.agent.ViaComponents;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.InitializeConfiguration;
import org.whitesource.fs.ProjectsDetails;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author chen.luigi
 */
public class MavenTest {

    /* --- Static members --- */

    private static final String APP_PATH_JAR = "\\src\\test\\resources\\via\\maven\\ksa\\ksa-core\\target\\ksa-core-3.9.0.jar";
    private static final String APP_PATH = Paths.get(Constants.DOT).toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath(APP_PATH_JAR);
    private static final String[] args = new String[]{"-appPath", APP_PATH};

    /* --- Private Members --- */

    private ProjectsDetails projectsDetails;
    private ProjectsSender projectsSender;
    private Map<AgentProjectInfo, LinkedList<ViaComponents>> projectToViaComponents;
    private InitializeConfiguration initializeConfiguration;

    @Before
    public void setUp() throws IOException {
        projectsDetails = new ProjectsDetails();
        initializeConfiguration = new InitializeConfiguration();
        initializeConfiguration.setArgs(args);
        initializeConfiguration.setUp();
        projectToViaComponents = initializeConfiguration.getProjectToViaComponents();
        projectsSender = initializeConfiguration.getProjectsSender();

    }

    @Ignore
    @Test
    public void viaWithMaven() {
        projectsDetails.setProjectToViaComponents(projectToViaComponents);
        //initializeConfiguration.sendProjects(projectsSender,projectsDetails);

    }
}
