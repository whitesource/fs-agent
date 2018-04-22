package org.whitesource.agent;

/**
 * @author raz.nitzan
 */
public enum ViaLanguage {
    JAVA("java"),
    JAVA_SCRIPT("javascript");

    private final String language;

    ViaLanguage(String language) {
        this.language = language;
    }

    public String toString() {
        return this.language;
    }
}
