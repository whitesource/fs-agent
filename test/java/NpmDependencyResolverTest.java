import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyResolutionService;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 *@author eugen.horovitz
 */
public class NpmDependencyResolverTest {

    @Test
    public void shouldReturnDependenciesTree() {
        String folderParent = TestHelper.FOLDER_WITH_NPN_PROJECTS;

        Properties props = new Properties();
        DependencyResolutionService dependencyResolutionService = new DependencyResolutionService(props);
        List<ResolutionResult> results = dependencyResolutionService.resolveDependencies(Arrays.asList(folderParent), new String[0]);

        results.forEach(resolutionResult -> {
            Assert.assertTrue(resolutionResult.getResolvedDependencies().size() > 0);
            List<DependencyInfo> dependencyInformation = resolutionResult
                    .getResolvedDependencies().stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
            Assert.assertTrue(dependencyInformation.size() > 0);
        });
    }
}