package org.whitesource.agent.dependency.resolver.php.phpModel;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author chen.luigi
 */
public class PhpPackage implements Serializable {

    /* --- Static members --- */

    private static final long serialVersionUID = -1797919662008607242L;

    /* --- Private Members --- */

    private String name;
    private String version;

    @SerializedName("source")
    private PackageSource packageSource;

    @SerializedName("require")
    private HashMap<String, String> packageRequire;

    /* --- Constructors --- */

    public PhpPackage() {
        packageRequire = new HashMap<>();
    }

    public PhpPackage(String name, String version, PackageSource packageSource, HashMap<String, String> packageRequire) {
        this.name = name;
        this.version = version;
        this.packageSource = packageSource;
        this.packageRequire = packageRequire;
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhpPackage)) return false;
        PhpPackage that = (PhpPackage) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(version, that.version) &&
                Objects.equals(packageSource, that.packageSource) &&
                Objects.equals(packageRequire, that.packageRequire);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, version, packageSource, packageRequire);
    }

    /* --- Getters / Setters --- */

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public PackageSource getPackageSource() {
        return packageSource;
    }

    public void setPackageSource(PackageSource packageSource) {
        this.packageSource = packageSource;
    }

    public HashMap<String, String> getPackageRequire() {
        return packageRequire;
    }

    public void setPackageRequire(HashMap<String, String> packageRequire) {
        this.packageRequire = packageRequire;
    }
}
