package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author raz.nitzan
 */
@XmlRootElement(name = "Project")
public class NugetCsprojPackages {
    /* --- Members --- */

    private List<NugetCsprojItemGroup> nugets = new LinkedList<>();

    /* --- Getters / Setters --- */

    @XmlElement(name = "ItemGroup")
    public List<NugetCsprojItemGroup> getNugets() {
        return nugets;
    }

    public void setNugets(List<NugetCsprojItemGroup> nugets) {
        this.nugets = (List<NugetCsprojItemGroup>) nugets;
    }
}
