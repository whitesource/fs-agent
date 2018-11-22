package org.whitesource.agent.dependency.resolver.hex;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

import java.io.File;
import java.nio.file.Paths;
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
        String mixLockPath = Paths.get(".").toAbsolutePath().normalize().toString() +
                TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\hex\\mix.lock");
        HashMap<String, DependencyInfo> stringDependencyInfoHashMap = hexDependencyResolver.parseMixLoc(new File(mixLockPath));
        Assert.assertTrue(stringDependencyInfoHashMap != null);
    }

    @Ignore
    @Test
    public void parseTree(){
        String folderPath = Paths.get(".").toAbsolutePath().normalize().toString() +
                TestHelper.getOsRelativePath("\\src\\test\\resources\\resolver\\hex\\imager");
        HashMap<String, DependencyInfo> stringDependencyInfoHashMap = hexDependencyResolver.parseMixLoc(new File(folderPath + "\\mix.lock"));
        HashMap<String, List<DependencyInfo>> modulesMap = hexDependencyResolver.parseMixTree(folderPath, stringDependencyInfoHashMap);
        //printTree(modulesMap);
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