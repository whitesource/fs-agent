import org.junit.Assert;
import org.junit.Test;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.dependency.resolver.npm.NpmDependencyResolver;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by eugen.horovitz on 6/28/2017.
 */
public class NpmDependencyResolverTest {

    @Test
    public void shouldReturnDependenciesTree() {
        NpmDependencyResolver npmLsFileResolver = new NpmDependencyResolver();

        String folderParent = "C:\\Users\\eugen\\WebstormProjects\\good3\\";
        FilesScanner fs = new FilesScanner();

        Map<String, String[]> map = fs.findAllFiles(Arrays.asList(folderParent), new NpmDependencyResolver().getBomPattern(), new String[0]);
        map.forEach((folder, files) -> {
            ResolutionResult resolutionResult = npmLsFileResolver.resolveDependencies(folder, folder, Arrays.stream(files).map(file ->
                    Paths.get(folder, file).toString()).collect(Collectors.toList()));
            Assert.assertTrue(resolutionResult.getResolvedDependencies().size() > 0);

            List<DependencyInfo> dependencyInformation = resolutionResult
                    .getResolvedDependencies().stream().filter(x -> x.getChildren().size() > 0).collect(Collectors.toList());
            Assert.assertTrue(dependencyInformation.size() > 0);
        });
    }
}
