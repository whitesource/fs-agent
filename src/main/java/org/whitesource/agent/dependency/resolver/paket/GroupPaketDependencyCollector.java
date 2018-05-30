package org.whitesource.agent.dependency.resolver.paket;

import org.whitesource.agent.Constants;

import java.util.List;

/**
 * @author raz.nitzan
 */
public class GroupPaketDependencyCollector extends AbstractPaketDependencyCollector {

    /* --- Statics Members --- */

    private static final String GROUP = "GROUP";

    /* --- Members -- */

    private String groupName;

    /* --- Constructors --- */

    public GroupPaketDependencyCollector(List<String> directDependenciesNames, String[] paketIgnoredGroups, String groupName) {
        super(directDependenciesNames, paketIgnoredGroups);
        this.groupName = groupName;
    }

    /* --- Override protected methods --- */

    @Override
    protected String getGroupName() {
        return this.groupName;
    }

    @Override
    protected String beginGroupLine() {
        return GROUP + Constants.WHITESPACE + this.groupName;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + Constants.FORWARD_SLASH + this.groupName + Constants.FORWARD_SLASH + dependencyName;
    }
}
