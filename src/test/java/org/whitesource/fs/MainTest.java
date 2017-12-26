package org.whitesource.fs;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.Pair;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;

import static org.whitesource.agent.ConfigPropertyKeys.NPM_RUN_PRE_STEP;
import static org.whitesource.agent.ConfigPropertyKeys.PROJECT_PER_SUBFOLDER;

public class MainTest {

    @Test
    public void shouldWorkWithProjectPerFolder() throws IOException {
        File config = TestHelper.getFileFromResources(CommandLineArgs.CONFIG_FILE_NAME);
        String[] commandLineArgs = new String[]{"-c", config.getAbsolutePath(), "-d", new File(TestHelper.FOLDER_WITH_MIX_FOLDERS).getAbsolutePath(), "-"+ PROJECT_PER_SUBFOLDER, "true"};

        FileSystemAgentConfiguration fileSystemAgentConfiguration = new FileSystemAgentConfiguration(commandLineArgs);
        ProjectsCalculator projectsCalculator = new ProjectsCalculator();
        Pair<Collection<AgentProjectInfo>,StatusCode> projects = projectsCalculator.getAllProjects(fileSystemAgentConfiguration);

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

        FileSystemAgentConfiguration fileSystemAgentConfiguration = new FileSystemAgentConfiguration(commandLineArgs);
        ProjectsCalculator projectsCalculator = new ProjectsCalculator();
        projectsCalculator.getAllProjects(fileSystemAgentConfiguration);

        long countAfter = Arrays.stream(npmDir.listFiles()).filter(x->x.isDirectory()).count();
        Assert.assertTrue(countAfter > 0);
    }
}
