package org.whitesource.fs;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.Pair;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;

import static org.whitesource.agent.ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY;
import static org.whitesource.agent.ConfigPropertyKeys.NPM_RUN_PRE_STEP;
import static org.whitesource.agent.ConfigPropertyKeys.PROJECT_PER_SUBFOLDER;

public class MainTest {

    @Test
    public void shouldRunMixedMainOnFolder() {
        File directory = new File(TestHelper.FOLDER_WITH_MIX_FOLDERS);
        Arrays.stream(directory.listFiles()).filter(dir -> dir.isDirectory()).forEach(dir -> {
            File file = TestHelper.getFileFromResources("whitesource-fs-agent.config");
            String config = file.getAbsolutePath();
            String[] args = ("-c " + config + " -d " + dir.getPath() + " -product " + "fsAgentMain" + " -project " + dir.getName()).split(" ");
            int result = Main.execute(args);
            Assert.assertEquals(result, 0);
        });
    }

    @Test
    public void shouldRunMavenWithoutName() {
        File dir = new File(TestHelper.FOLDER_WITH_MVN_PROJECTS);
        File file = TestHelper.getTempFileWithProperty(INCLUDES_PATTERN_PROPERTY_KEY,"**/*.java");
        String config = file.getAbsolutePath();
        String projectName = dir.getName();
        String[] args = ("-c " + config + " -d " + dir.getPath() + " -product " + "fsAgentMain" + " -project " + projectName).split(" ");

        // read configuration config
        FSAConfiguration FSAConfiguration = new FSAConfiguration(args);
        ProjectsCalculator projectsCalculator = new ProjectsCalculator();
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = projectsCalculator.getAllProjects(FSAConfiguration);

        Assert.assertEquals(projects.getKey().size(), 1);
        String nameActual = projects.getKey().stream().findFirst().get().getCoordinates().getArtifactId();
        Assert.assertEquals(nameActual, projectName);
    }

    @Test
    public void shouldWorkWithProjectPerFolder() throws IOException {
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String[] commandLineArgs = new String[]{"-c", config.getAbsolutePath(), "-d", new File(TestHelper.FOLDER_WITH_MIX_FOLDERS).getAbsolutePath(), "-"+ PROJECT_PER_SUBFOLDER, "true"};

        FSAConfiguration FSAConfiguration = new FSAConfiguration(commandLineArgs);
        ProjectsCalculator projectsCalculator = new ProjectsCalculator();
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = projectsCalculator.getAllProjects(FSAConfiguration);

        Assert.assertTrue(projects.getKey().size() > 1);
    }

    @Test
    public void shouldRunNpmInstallAsPreStep() throws IOException {
        // arrange
        File config = TestHelper.getTempFileWithProperty(NPM_RUN_PRE_STEP, true);
        File mainDir = new File(TestHelper.FOLDER_WITH_NPN_PROJECTS);

        File npmDir = Arrays.stream(mainDir.listFiles()).filter(d->d.isDirectory()).findFirst().get();
        long countBefore = Arrays.stream(npmDir.listFiles()).filter(x->x.isDirectory()).count();

        if (countBefore > 0) {
            Arrays.stream(npmDir.listFiles()).filter(x -> x.isDirectory()).forEach(d -> {
                try {
                    FileUtils.deleteDirectory(d);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        String[] commandLineArgs = new String[]{"-c", config.getAbsolutePath(), "-d", mainDir.getAbsolutePath()};

        FSAConfiguration FSAConfiguration = new FSAConfiguration(commandLineArgs);
        ProjectsCalculator projectsCalculator = new ProjectsCalculator();
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = projectsCalculator.getAllProjects(FSAConfiguration);

        projects.getKey().stream().findFirst().get().getDependencies().stream().allMatch(dependencyInfo -> StringUtils.isNotBlank(dependencyInfo.getSha1()));
        long countAfter = Arrays.stream(npmDir.listFiles()).filter(x->x.isDirectory()).count();
        Assert.assertTrue(countAfter > 0);
    }
}
