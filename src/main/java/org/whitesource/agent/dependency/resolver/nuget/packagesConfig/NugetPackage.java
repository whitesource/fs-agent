package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * Created by yossi.weinberg on 7/21/2017.
 */
public class NugetPackage {

    /* --- Members --- */

    private String pkgName;
    private String pkgVersion;

    /* --- Constructors --- */

    public NugetPackage(String pkgName, String pkgVersion) {
        this.pkgName = pkgName;
        this.pkgVersion = pkgVersion;
    }

    public NugetPackage() {
    }

    /* --- Getters / Setters --- */

    public String getPkgName() {
        return pkgName;
    }

    @XmlAttribute(name = "id")
    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public String getPkgVersion() {
        return pkgVersion;
    }

    @XmlAttribute(name = "version")
    public void setPkgVersion(String pkgVersion) {
        this.pkgVersion = pkgVersion;
    }
}
