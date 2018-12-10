package org.whitesource.agent.dependency.resolver.npm;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.bower.BowerLsJsonDependencyCollector;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class LsDependencyResolversTest {
    private static String FOLDER_TO_TEST = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_NPN_PROJECTS);
    private NpmLsJsonDependencyCollector npmLsJsonDependencyCollector = new NpmLsJsonDependencyCollector(true,
            60, false, false);

    @Before
    public void setup(){

    }

    @Ignore
    @Test
    public void shouldReturnDependenciesTreeNpm() {
        AgentProjectInfo projectInfo = npmLsJsonDependencyCollector.collectDependencies(FOLDER_TO_TEST).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }

    @Ignore
    @Test
    public void shouldReturnDependenciesTreeBower() {
        String firstFolder = TestHelper.getFirstFolder(TestHelper.FOLDER_WITH_BOWER_PROJECTS);
        AgentProjectInfo projectInfo = new BowerLsJsonDependencyCollector(60).collectDependencies(firstFolder).stream().findFirst().get();
        Collection<DependencyInfo> dependencies = projectInfo.getDependencies();
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }

    // don't merge into integration
    @Ignore
    @Test
    public void npmLsJsonDependencyCollectorGetDependencies(){
        List<String> lines = readFileAsList("C:\\Users\\ErezHuberman\\Documents\\jira-tickets\\WSE-1116\\npmls.log");
        StringBuilder json = new StringBuilder();
        for (String line : lines) {
            json.append(line);
        }
        JSONObject jsonObject = new JSONObject(json.toString());
        Collection<DependencyInfo> dependencyInfos = new ArrayList<>();
        npmLsJsonDependencyCollector.getDependencies(jsonObject, lines, 1, dependencyInfos);
        Assert.assertTrue(dependencyInfos.isEmpty() == false);
    }

    private List<String> readFileAsList(String fileName) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                lines.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }
}