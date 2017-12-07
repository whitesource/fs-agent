package org.whitesource.agent;

import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        String currentDir = System.getProperty("user.dir");
        Path testPath = Paths.get(currentDir,"src\\test\\resources");
        DependencyInfo dependencyInfo = factory.createDependencyInfo(testPath.toFile(),"ZedGraph.dll");
        dependencyInfo.getCopyrights();
    }
}
