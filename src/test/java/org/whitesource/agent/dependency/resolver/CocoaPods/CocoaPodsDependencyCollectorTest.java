package org.whitesource.agent.dependency.resolver.CocoaPods;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.go.GoDependencyResolverTest;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.agent.utils.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collection;

import static org.junit.Assert.*;

public class CocoaPodsDependencyCollectorTest {

    private final Logger logger = LoggerFactory.getLogger(CocoaPodsDependencyCollectorTest.class);

    private CocoaPodsDependencyCollector cocoaPodsDependencyCollector;
    @Before
    public void setUp() {
        cocoaPodsDependencyCollector = new CocoaPodsDependencyCollector();
    }

    @Test
    public void collectDependencies() {
        String podFilePath = Paths.get(".").toAbsolutePath().normalize().toString() + TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\cocoaPods\\Podfile.lock");
        Collection<AgentProjectInfo> agentProjectInfos = cocoaPodsDependencyCollector.collectDependencies(podFilePath);
        /*for (AgentProjectInfo agentProjectInfo : agentProjectInfos){
            for (DependencyInfo dependencyInfo : agentProjectInfo.getDependencies()){
                printDependency(dependencyInfo);
            }
        }*/
        Assert.assertTrue(agentProjectInfos != null);
    }

    private void printDependency(DependencyInfo dependencyInfo){
        logger.info(dependencyInfo.getGroupId());
        for (DependencyInfo child : dependencyInfo.getChildren()){
            printDependency(child);
        }
    }
}