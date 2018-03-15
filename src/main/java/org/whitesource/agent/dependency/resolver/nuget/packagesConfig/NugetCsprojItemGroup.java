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

    @ElementList(inline=true, required=false)
    private List<ReferenceTag> references = new LinkedList<>();

    /* --- Constructors --- */

    public NugetCsprojItemGroup(List<PackageReference> packagesReference, List<ReferenceTag> references) {
        this.packagesReference = packagesReference;
        this.references = references;
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

    public List<ReferenceTag> getReferences() {
        return this.references;
    }

    public void setReferences(List<ReferenceTag> references) {
        this.references = references;
    }
}
