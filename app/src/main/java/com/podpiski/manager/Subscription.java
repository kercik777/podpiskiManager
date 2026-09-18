package com.podpiski.manager;

public class Subscription {
    private long id;
    private String name;
    private String link;
    private long folderId;
    private int position;
    private String createdAt;

    public Subscription() {
        this.folderId = -1;
    }

    public Subscription(String name, String link, long folderId, int position, String createdAt) {
        this.name = name;
        this.link = link;
        this.folderId = folderId;
        this.position = position;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public long getFolderId() { return folderId; }
    public void setFolderId(long folderId) { this.folderId = folderId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}