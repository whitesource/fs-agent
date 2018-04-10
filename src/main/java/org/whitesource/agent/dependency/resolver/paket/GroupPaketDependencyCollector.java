package org.whitesource.agent.dependency.resolver.paket;

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
        return GROUP + SPACE + this.groupName;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + FORWARD_SLASH + this.groupName + FORWARD_SLASH + dependencyName;
    }
}
