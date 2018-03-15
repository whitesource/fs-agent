package org.whitesource.agent.utils;

public class Pair <X, Y>{

    /* --- Private members --- */
    private final X key;
    private final Y value;

    /* --- Constructor --- */
    public Pair(X key, Y value) {
        this.key = key;
        this.value = value;
    }

    /* --- Getters --- */
    public X getKey() {
        return key;
    }
    public Y getValue() {
        return value;
    }
}
