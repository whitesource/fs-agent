package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import java.util.LinkedList;
import java.util.List;

/**
 * @author raz.nitzan
 */
@Root(name="ItemGroup", strict=false)
public class NugetCsprojItemGroup {
    /* --- Members --- */

    @ElementList(inline=true, required=false)
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

    public void setPackageReference(List<PackageReference> packagesReference) {
        this.packagesReference = packagesReference;
    }
}
