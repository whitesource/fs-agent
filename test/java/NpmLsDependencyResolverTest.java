import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by eugen.horovitz on 6/28/2017.
 */
public class NpmLsDependencyResolverTest {

    @Test
    public void shouldReturnDependenciesTree() {
        Collection<DependencyInfo> dependencies = new NpmLsJsonDependencyCollector().collectDependencies(TestHelper.FOLDER_TO_TEST);
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }

    //@Test
    //public void shouldReturnTheSameAsNpmLsTest() {
    //    Collection<DependencyInfo> dependencies = new NpmLsJsonDependencyCollector().collectDependencies(TestHelper.FOLDER_TO_TEST);
    //    Assert.assertTrue(dependencies.size() > 0);
    //    List<String> npmLsFromFileResolver = dependencies.stream()
    //            .map(x -> TestHelper.getShortNameByTgz(x))
    //            .sorted().distinct().collect(Collectors.toList());
    //    Stream<String> distinctDependenciesTest = TestHelper.getDependenciesWithNpm(TestHelper.FOLDER_TO_TEST).sorted();
    //    List<String> npmLsFromFromTest = distinctDependenciesTest.collect(Collectors.toList());
    //    TestHelper.assertListEquals(npmLsFromFileResolver, npmLsFromFromTest);
    //}
}