package org.whitesource.agent;

import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.TestHelper;

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
        File file = TestHelper.getFileFromResources("ZedGraph.dll");
        DependencyInfo dependencyInfo = factory.createDependencyInfo(file.getParentFile(),file.getName());
        dependencyInfo.getCopyrights();
    }
}
