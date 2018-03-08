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
    private String filename;
    private String systemPath;
    private String md5;

    /* --- Constructors --- */

    public Package() {
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Package)) return false;
        Package aPackage = (Package) o;
        return Objects.equals(packageName, aPackage.packageName) &&
                Objects.equals(version, aPackage.version) &&
                Objects.equals(architecture, aPackage.architecture) &&
                Objects.equals(filename, aPackage.filename) &&
                Objects.equals(systemPath, aPackage.systemPath) &&
                Objects.equals(md5, aPackage.md5);
    }

    @Override
    public int hashCode() {

        return Objects.hash(packageName, version, architecture, filename, systemPath, md5);
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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getSystemPath() {
        return systemPath;
    }

    public void setSystemPath(String systemPath) {
        this.systemPath = systemPath;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }
}
