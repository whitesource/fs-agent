package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.annotation.XmlElement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author raz.nitzan
 */
public class NugetCsprojItemGroup {
    /* --- Members --- */

    private List<PackageReference> packagesReference = new LinkedList<>();

    /* --- Constructors --- */

    public NugetCsprojItemGroup(List<PackageReference> packagesReference) {
        this.packagesReference = packagesReference;
    }

    public NugetCsprojItemGroup() {
    }

    /* --- Getters / Setters --- */

    public List<PackageReference> getPackageReference() {
        return packagesReference;
    }

    @XmlElement(name = "PackageReference")
    public void setPackageReference(List<PackageReference> packagesReference) {
        this.packagesReference = packagesReference;
    }
}
