package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author raz.nitzan
 */
public class PackageReference implements NugetPackageInterface {

    /* --- Members --- */

    private String pkgName;
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

    @XmlAttribute(name = "Include")
    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public String getPkgVersion() {
        return pkgVersion;
    }

    @XmlAttribute(name = "Version")
    public void setPkgVersion(String pkgVersion) {
        this.pkgVersion = pkgVersion;
    }
}
