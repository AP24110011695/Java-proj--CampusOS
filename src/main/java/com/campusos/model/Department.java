package com.campusos.model;

import java.time.LocalDateTime;

/** Academic department. One department can contain many users. */
public class Department {

    private Long id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private long memberCount;       // populated via JOIN aggregate for display only
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Department() {
        this.active = true;
    }

    public Department(String name, String code, String description) {
        this();
        this.name = name;
        this.code = code;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
