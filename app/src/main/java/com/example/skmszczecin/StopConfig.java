package com.example.skmszczecin;
import java.util.List;

public class StopConfig {
    public String id;
    public String name;
    public String groupName;
    public List<String> lines;

    public StopConfig(String id, String name, String groupName, List<String> lines) {
        this.id = id;
        this.name = name;
        this.groupName = groupName;
        this.lines = lines;
    }
}