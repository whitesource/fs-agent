package org.whitesource.agent.dependency.resolver.hex;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class HexDependencyResolverTest {

    HexDependencyResolver hexDependencyResolver;

    @Before
    public void setUp() throws Exception {
        hexDependencyResolver = new HexDependencyResolver(true, true,true);
    }
    @Test
    public void parseMixLoc() {
        HashMap<String, DependencyInfo> stringDependencyInfoHashMap = hexDependencyResolver.parseMixLoc(new File("C:\\Users\\ErezHuberman\\Documents\\===HEX===\\imager-master\\mix.lock"));
        Assert.assertTrue(stringDependencyInfoHashMap != null);
    }

    @Test
    public void parseTree(){
        HashMap<String, DependencyInfo> stringDependencyInfoHashMap = hexDependencyResolver.parseMixLoc(new File("C:\\Users\\ErezHuberman\\Documents\\===HEX===\\avia-develop\\mix.lock"));
        HashMap<String, List<DependencyInfo>> modulesMap = hexDependencyResolver.parseMixTree("C:\\Users\\ErezHuberman\\Documents\\===HEX===\\avia-develop", stringDependencyInfoHashMap);
        printTree(modulesMap);
        Assert.assertTrue(modulesMap != null);
    }

    private void printTree(HashMap<String, List<DependencyInfo>> map){
        for (String name : map.keySet()){
            System.out.println(name + ":");
            List<DependencyInfo> dependencyInfoList = map.get(name);
            for (DependencyInfo dependencyInfo : dependencyInfoList){
                printDependencyInfo(dependencyInfo,"");
            }
        }
    }

    private void printDependencyInfo(DependencyInfo dependencyInfo, String level){
        System.out.println(level + dependencyInfo.getArtifactId() + ":" + dependencyInfo.getVersion());
        for (DependencyInfo child : dependencyInfo.getChildren()){
            printDependencyInfo(child, "--" + level);
        }
    }
}