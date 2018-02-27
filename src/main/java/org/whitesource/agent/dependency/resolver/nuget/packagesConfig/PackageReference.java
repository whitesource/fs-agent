package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 * @author raz.nitzan
 */
@Root(name="PackageReference", strict=false)
public class PackageReference implements NugetPackageInterface {

    /* --- Members --- */

    @Attribute(name="Include", required=false)
    private String pkgName;
    @Attribute(name="Version", required=false)
    private String pkgVersion;

    /* --- Constructors --- */

    public PackageReference(String pkgName, String pkgVersion) {
        this.pkgName = pkgName;
        this.pkgVersion = pkgVersion;
    }

    public PackageReference() {
    }

    /* --- Getters / Setters --- */

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public String getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(String pkgVersion) {
        this.pkgVersion = pkgVersion;
    }
}
