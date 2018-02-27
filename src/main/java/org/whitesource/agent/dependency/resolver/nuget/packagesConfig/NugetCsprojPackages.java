package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.LinkedList;
import java.util.List;

/**
 * @author raz.nitzan
 */
@Root(name="Project", strict=false)
public class NugetCsprojPackages {
    /* --- Members --- */

    @ElementList(inline=true, required=false)
    private List<NugetCsprojItemGroup> nugetItemGroups = new LinkedList<>();

    /* --- Getters / Setters --- */

    public List<NugetCsprojItemGroup> getNugetItemGroups() {
        return nugetItemGroups;
    }

    public void setNugetItemGroups(List<NugetCsprojItemGroup> nugetItemGroups) {
        this.nugetItemGroups = (List<NugetCsprojItemGroup>) nugetItemGroups;
    }
}
