package org.whitesource.agent;

/**
 * @author raz.nitzan
 */
public enum LanguageForVia {
    JAVA("java"),
    JAVA_SCRIPT("javascript");

    private final String language;

    LanguageForVia(String language) {
        this.language = language;
    }

    public String toString() {
        return this.language;
    }
}
