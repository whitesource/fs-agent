package org.whitesource.agent.dependency.resolver.paket;

import org.whitesource.agent.Constants;

import java.util.List;

/**
 * @author raz.nitzan
 */
public class MainGroupPaketDependencyCollector extends AbstractPaketDependencyCollector {

    /* --- Statics Members --- */

    private static final String MAIN = "Main";

    /* --- Constructors --- */

    public MainGroupPaketDependencyCollector(List<String> directDependenciesNames, String[] paketIgnoredGroups) {
        super(directDependenciesNames, paketIgnoredGroups);
    }

    /* --- Override protected methods --- */

    @Override
    protected String getGroupName() {
        return MAIN;
    }

    @Override
    protected String beginGroupLine() {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + Constants.FORWARD_SLASH + dependencyName;
    }
}
