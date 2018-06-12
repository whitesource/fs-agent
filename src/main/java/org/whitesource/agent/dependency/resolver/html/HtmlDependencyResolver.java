package org.whitesource.agent.dependency.resolver.html;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Created by anna.rozin
 */
public class HtmlDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(HtmlDependencyResolver.class);

    /* --- Members --- */

    private boolean htmlRestoreDependencies;


    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) throws FileNotFoundException {

        return null;
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return  Arrays.asList(Constants.HTML);
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.NPM;
    }

    @Override
    protected String getBomPattern() {
        return "**/*" + Constants.DOT + Constants.HTML;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }
}
