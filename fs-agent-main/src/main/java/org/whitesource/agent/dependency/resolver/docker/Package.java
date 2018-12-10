package org.whitesource.agent.dependency.resolver.docker;

import java.util.Objects;

/**
 * @author chen.luigi
 */
public class Package {

    /* --- Private members --- */

    private String packageName;
    private String version;
    private String architecture;

    /* --- Constructors --- */

    public Package() {
    }

    public Package(String packageName, String version, String architecture) {
        this.packageName = packageName;
        this.version = version;
        this.architecture = architecture;
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Package)) return false;
        Package aPackage = (Package) o;
        return Objects.equals(packageName, aPackage.packageName) &&
                Objects.equals(version, aPackage.version) &&
                Objects.equals(architecture, aPackage.architecture);
    }

    @Override
    public int hashCode() {

        return Objects.hash(packageName, version, architecture);
    }

    /* --- Public methods --- */

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

}
