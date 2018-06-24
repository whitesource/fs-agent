package org.whitesource.agent.dependency.resolver.php.phpModel;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

/**
 * @author chen.luigi
 */
public class PhpModel implements Serializable {

    /* --- Private Members --- */

    private static final long serialVersionUID = 3790948889265089807L;

    @SerializedName("packages")
    private Collection<PhpPackage> phpPackages;

    @SerializedName("packages-dev")
    private Collection<PhpPackage> phpPackagesDev;

    /* --- Constructors --- */

    public PhpModel() {
        phpPackages = new LinkedList<>();
        phpPackagesDev = new LinkedList<>();
    }

    public PhpModel(Collection<PhpPackage> phpPackages, Collection<PhpPackage> phpPackagesDev) {
        this.phpPackages = phpPackages;
        this.phpPackagesDev = phpPackagesDev;
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhpModel)) return false;
        PhpModel phpModel = (PhpModel) o;
        return Objects.equals(phpPackages, phpModel.phpPackages) &&
                Objects.equals(phpPackagesDev, phpModel.phpPackagesDev);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phpPackages, phpPackagesDev);
    }

    /* --- Getters / Setters --- */

    public Collection<PhpPackage> getPhpPackages() {
        return phpPackages;
    }

    public void setPhpPackages(Collection<PhpPackage> phpPackages) {
        this.phpPackages = phpPackages;
    }

    public Collection<PhpPackage> getPhpPackagesDev() {
        return phpPackagesDev;
    }

    public void setPhpPackagesDev(Collection<PhpPackage> phpPackagesDev) {
        this.phpPackagesDev = phpPackagesDev;
    }
}
