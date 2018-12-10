package org.whitesource.agent.dependency.resolver.docker;

import java.util.Objects;

/**
 * @author chen.luigi
 */
public class DockerImage {

    /* --- Members --- */

    private String repository;
    private String tag;
    private String id;

    /* --- Constructors --- */

    public DockerImage() {
    }

    public DockerImage(String repository, String tag, String id) {
        this.repository = repository;
        this.tag = tag;
        this.id = id;
    }

    /* --- Overridden methods --- */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerImage)) return false;
        DockerImage that = (DockerImage) o;
        return Objects.equals(repository, that.repository) &&
                Objects.equals(tag, that.tag) &&
                Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(repository, tag, id);
    }

    @Override
    public String toString() {
        return "DockerImage{" +
                "repository='" + repository + '\'' +
                ", tag='" + tag + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

    /* --- Getters / Setters --- */

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
