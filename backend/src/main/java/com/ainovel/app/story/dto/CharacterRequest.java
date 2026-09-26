package com.ainovel.app.story.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

/** Omitted fields survive partial edits; explicit null/empty values clear them. */
public class CharacterRequest {
    @NotBlank private String name;
    private String synopsis;
    private String details;
    private String relationships;
    @Size(max = 255) private String role;
    @Size(max = 255) private String archetype;
    private final Set<String> supplied = new HashSet<>();

    public CharacterRequest() {}
    public CharacterRequest(String name, String synopsis, String details, String relationships) {
        setName(name); setSynopsis(synopsis); setDetails(details); setRelationships(relationships);
    }
    public boolean has(String field) { return supplied.contains(field); }
    public String name() { return name; }
    @JsonSetter("name") public void setName(String value) { supplied.add("name"); name = value; }
    public String synopsis() { return synopsis; }
    @JsonSetter("synopsis") public void setSynopsis(String value) { supplied.add("synopsis"); synopsis = value; }
    public String details() { return details; }
    @JsonSetter("details") public void setDetails(String value) { supplied.add("details"); details = value; }
    public String relationships() { return relationships; }
    @JsonSetter("relationships") public void setRelationships(String value) { supplied.add("relationships"); relationships = value; }
    public String role() { return role; }
    @JsonSetter("role") public void setRole(String value) { supplied.add("role"); role = value; }
    public String archetype() { return archetype; }
    @JsonSetter("archetype") public void setArchetype(String value) { supplied.add("archetype"); archetype = value; }
}
