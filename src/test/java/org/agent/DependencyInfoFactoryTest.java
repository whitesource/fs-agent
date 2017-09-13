package org.whitesource.agent;

import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;

/**
 * Test class for creadur-rat.
 *
 * @author tom.shapira
 */
public class DependencyInfoFactoryTest {

    @Ignore
    @Test
    public void testCopyrights() {
        DependencyInfoFactory factory = new DependencyInfoFactory();
        DependencyInfo dependencyInfo = factory.createDependencyInfo(new File("C:\\Users\\Chen\\Desktop\\test2"), "ZedGraph.dll");
        dependencyInfo.getCopyrights();
    }
}
