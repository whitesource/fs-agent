import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.npm.NpmLsJsonDependencyCollector;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class NpmLsDependencyResolverTest {

    @Test
    public void shouldReturnDependenciesTree() {
        Collection<DependencyInfo> dependencies = new NpmLsJsonDependencyCollector().collectDependencies(TestHelper.FOLDER_TO_TEST);
        Assert.assertTrue(dependencies.size() > 0);

        List<DependencyInfo> dependencyInformation = dependencies.stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
        Assert.assertTrue(dependencyInformation.size() > 0);
    }
}