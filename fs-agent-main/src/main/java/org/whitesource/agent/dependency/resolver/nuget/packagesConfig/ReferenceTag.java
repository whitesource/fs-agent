package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * @author raz.nitzan
 */
@Root(name="Reference", strict=false)
public class ReferenceTag {

    /* --- Members --- */

    @Attribute(name="Include", required=false)
    private String name;

    @Attribute(name="Version", required=false)
    private String version;

    @Element(name="HintPath", required=false)
    private String hintPath;

    /* --- Constructors --- */

    public ReferenceTag(String name, String version, String hintPath) {
        this.name = name;
        this.version = version;
        this.hintPath = hintPath;
    }

    public ReferenceTag() {
    }

    /* --- Getters / Setters --- */

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String pkgVersion) {
        this.version = pkgVersion;
    }

    public String getHintPath() {
        return this.hintPath;
    }

    public void setHintPath(String hintPath) {
        this.hintPath = hintPath;
    }
}
