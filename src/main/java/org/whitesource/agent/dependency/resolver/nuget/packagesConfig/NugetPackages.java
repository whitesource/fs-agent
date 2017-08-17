package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yossi.weinberg on 7/21/2017.
 */
@XmlRootElement(name = "packages")
public class NugetPackages {

    /* --- Members --- */

    private List<NugetPackage> nugets;

    /* --- Getters / Setters --- */

    @XmlElement(name = "package")
    public List<NugetPackage> getNugets() {
        return nugets;
    }

    public void setNugets(List<NugetPackage> nugets) {
        this.nugets = nugets;
    }
}
