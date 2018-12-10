package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;


import javax.xml.bind.annotation.XmlTransient;

/**
 * @author raz.nitzan
 */
@XmlTransient
public interface NugetPackageInterface {
    String getPkgName();
    void setPkgName(String pkgName);
    String getPkgVersion();
    void setPkgVersion(String pkgVersion);
}
