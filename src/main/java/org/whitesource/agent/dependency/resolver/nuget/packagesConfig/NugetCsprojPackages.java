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
    private List<NugetCsprojItemGroup> nugets = new LinkedList<>();

    /* --- Getters / Setters --- */

    public List<NugetCsprojItemGroup> getNugets() {
        return nugets;
    }

    public void setNugets(List<NugetCsprojItemGroup> nugets) {
        this.nugets = (List<NugetCsprojItemGroup>) nugets;
    }
}
