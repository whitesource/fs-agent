package org.whitesource.agent.dependency.resolver.php.phpModel;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author chen.luigi
 */
public class PackageSource implements Serializable {

    /* --- Static members --- */

    private static final long serialVersionUID = 7066960176089576432L;

    private String reference;

    /* --- Constructors --- */

    public PackageSource() {
    }

    public PackageSource(String reference) {
        this.reference = reference;
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PackageSource)) return false;
        PackageSource that = (PackageSource) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {

        return Objects.hash(reference);
    }

    @Override
    public String toString() {
        return "PackageSource{" +
                "reference='" + reference + '\'' +
                '}';
    }

    /* --- Getters / Setters --- */

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
