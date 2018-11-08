package org.whitesource.agent.dependency.resolver.hex;

import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.FileNotFoundException;
import java.util.*;

public class HexDependencyResolver extends AbstractDependencyResolver {

    private static final List<String> HEX_SCRIPT_EXTENSION = Arrays.asList(".ex", ".exs");
    private static final String MIX_LOCK_FILE = "mix.lock";

    private boolean ignoreSourceFiles;
    private boolean runPreStep;

    public HexDependencyResolver(boolean ignoreSourceFiles, boolean runPreStep){
        this.ignoreSourceFiles = ignoreSourceFiles;
        this.runPreStep = runPreStep;
    }

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException {
        if (this.runPreStep){
            runPreStep();
        }
        return null;
    }

    private void runPreStep(){

    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        if(ignoreSourceFiles) {
            for (String hexExtension : HEX_SCRIPT_EXTENSION) {
                excludes.add(Constants.PATTERN + hexExtension);
            }
        }
        return excludes;
    }

    @Override
    protected DependencyType getDependencyType() {
        return null; //DependencyType.HEX;
    }

    @Override
    protected String getDependencyTypeName() {
        return null;//DependencyType.HEX.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.PATTERN + MIX_LOCK_FILE};
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return HEX_SCRIPT_EXTENSION;
    }
}
