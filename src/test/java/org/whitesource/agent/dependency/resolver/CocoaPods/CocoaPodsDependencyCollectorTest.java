package org.whitesource.agent.dependency.resolver.CocoaPods;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.go.GoDependencyResolverTest;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.utils.logger.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collection;

import static org.junit.Assert.*;

public class CocoaPodsDependencyCollectorTest {

    private final Logger logger = LoggerFactory.getLogger(CocoaPodsDependencyCollectorTest.class);

    private CocoaPodsDependencyCollector cocoaPodsDependencyCollector;
    private int index;
    @Before
    public void setUp() {
        cocoaPodsDependencyCollector = new CocoaPodsDependencyCollector();
    }

    @Test
    public void collectDependencies() {
        String podFilePath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\cocoaPods\\Podfile.lock");
        Collection<AgentProjectInfo> agentProjectInfos = cocoaPodsDependencyCollector.collectDependencies(podFilePath);
        index = 0;
        for (AgentProjectInfo agentProjectInfo : agentProjectInfos){
            for (DependencyInfo dependencyInfo : agentProjectInfo.getDependencies()){
                printDependency(dependencyInfo, 0);
            }
        }
        Assert.assertTrue(agentProjectInfos != null);
    }

    private void printDependency(DependencyInfo dependencyInfo, int level){
        String dash = "";
        for (int i = 0; i < level; i++){
            dash = dash.concat("-");
        }
        logger.info(index++ + ") "+ dash + dependencyInfo.getGroupId());
        level++;
        for (DependencyInfo child : dependencyInfo.getChildren()){
            printDependency(child, level);
        }
    }
}