import org.apache.rat.api.RatException;
import org.junit.Ignore;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.fs.DependencyInfoFactory;

import javax.xml.transform.TransformerConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Test class for creadur-rat.
 *
 * @author tom.shapira
 */
public class DependencyInfoFactoryTest {

    @Ignore
    @Test
    public void testRat() throws InterruptedException, RatException, TransformerConfigurationException, IOException {
        DependencyInfoFactory factory = new DependencyInfoFactory();
        Set<String> licenses = factory.scanLicenses(new File("C:\\WhiteSource\\FS Agent\\Fake Files\\license.txt"));
        for (String license : licenses) {
            System.out.println(license);
        }
    }

    @Test
    public void testCopyrights() {
        DependencyInfoFactory factory = new DependencyInfoFactory();
        DependencyInfo dependencyInfo = factory.createDependencyInfo(new File("C:\\WhiteSource\\FS Agent\\Fake Files"), "test-copyright.txt");
        dependencyInfo.getCopyrights();
    }
}
