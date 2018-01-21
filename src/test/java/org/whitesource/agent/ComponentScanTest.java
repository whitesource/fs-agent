package org.whitesource.agent;

import org.junit.Test;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;
import org.whitesource.fs.ComponentScan;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created by anna.rozin
 */
public class ComponentScanTest {

    @Test
    public void shouldEnrichAllDependenciesWithSha1() {
        Properties props = TestHelper.getPropertiesFromFile();
        props.put("d", "C:\\Users\\AnnaRozin\\Desktop\\move data\\Bug fix\\myApp\\test fs");
        List<String> dirs = Arrays.asList(TestHelper.FOLDER_WITH_NPN_PROJECTS);
        ComponentScan componentScan = new ComponentScan(props);
        String scan = componentScan.scan();
        System.out.printf(scan);
    }
}
