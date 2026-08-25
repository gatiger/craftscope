package io.github.gatiger.craftscope.project;

public class CraftScopeProject {

    private String id;
    private String name;
    private String targetItemId;
    private int targetCount;

    public CraftScopeProject(
            String id,
            String name,
            String targetItemId,
            int targetCount
    ) {
        this.id = id;
        this.name = name;
        this.targetItemId = targetItemId;
        this.targetCount = targetCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTargetItemId() {
        return targetItemId;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTargetItemId(String targetItemId) {
        this.targetItemId = targetItemId;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }
}