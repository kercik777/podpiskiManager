package com.podpiski.manager;

public class Folder {
    private long id;
    private String name;
    private int position;

    public Folder() {}

    public Folder(String name, int position) {
        this.name = name;
        this.position = position;
    }

    public Folder(long id, String name, int position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}