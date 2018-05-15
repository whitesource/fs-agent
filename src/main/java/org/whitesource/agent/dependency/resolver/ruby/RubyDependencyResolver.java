package org.whitesource.agent.dependency.resolver.ruby;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.Cli;

import java.io.*;
import java.util.*;

public class RubyDependencyResolver extends AbstractDependencyResolver {

    private static final String GEM_FILE_LOCK = "Gemfile.lock";
    private static final List<String> RUBY_SCRIPT_EXTENSION = Arrays.asList(".rb");
    private static final String BUNDLE         = "bundle";
    private static final String INSTALL        = "install";
    private static final String GEM            = "gem";
    private static final String ENVIRONMENT    = "environment gemdir";

    private final Logger logger = LoggerFactory.getLogger(RubyDependencyResolver.class);

    private RubyCli cli;

    public RubyDependencyResolver(){
        cli = new RubyCli();
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        List<DependencyInfo> dependencies = collectDependencies(topLevelFolder);
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        excludes.add(PATTERN + RUBY_SCRIPT_EXTENSION);
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return RUBY_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.RUBY;
    }

    @Override
    protected String getBomPattern() {
        return PATTERN + GEM_FILE_LOCK;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    private List<DependencyInfo> collectDependencies(String rootDirectory) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        File gemFileLock = new File(rootDirectory + fileSeparator + GEM_FILE_LOCK);
        if (gemFileLock.isFile() || cli.runCmd(rootDirectory, cli.getCommandParams(BUNDLE, INSTALL)) != null) {
            parseLines(gemFileLock, dependencyInfos, rootDirectory);
        }
        return dependencyInfos;
    }



    private void parseLines(File gemLockFile, List<DependencyInfo> dependencyInfos, String rootDirectory){
        String pathToGems = findPathToGems(rootDirectory);
        if (pathToGems == null){
            logger.error("Can't find path to gems' cache folder");
            return;
        }
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(gemLockFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String currLine;
            boolean insideGem = false;
            boolean insideSpecs = false;
            boolean resolveRepositoryPackages = false;
            boolean useParent = false;
            DependencyInfo dependencyInfo = null;
            ArrayList<String> repositoryPackages = null;
            while ((currLine = bufferedReader.readLine()) != null) {
                if (insideGem && insideSpecs){
                    if (currLine.isEmpty()) {
                        break;
                    } else {
                        String depName = currLine.split(" ")[0];
                    }
                } else if (currLine.contains("GEM")){
                    insideGem = true;
                } else if (insideGem && currLine.contains("specs:")){
                    insideSpecs = true;
                }
            }
        } catch (FileNotFoundException e){

        } catch (IOException e) {

        }
    }

    private String findPathToGems(String rootDirectory){
        String[] commandParams = cli.getCommandParams(GEM, ENVIRONMENT);
        List<String> lines = cli.runCmd(rootDirectory, commandParams);
        String path = null;
        if (lines != null){
            path = lines.get(0) + fileSeparator + "cache";
        }
        return path;
    }
}
