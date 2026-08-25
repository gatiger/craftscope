package io.github.gatiger.craftscope.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.gatiger.craftscope.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CraftScopeProjectManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final List<CraftScopeProject> projects =
            new ArrayList<>();

    private static Path projectFile;

    private CraftScopeProjectManager() {
    }

    public static void initialize(Path configDirectory) {
        Path craftScopeDirectory =
                configDirectory.resolve("craftscope");

        projectFile =
                craftScopeDirectory.resolve("projects.json");

        load();
    }

    public static List<CraftScopeProject> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    public static CraftScopeProject createProject(
            String name,
            String targetItemId,
            int targetCount
    ) {
        CraftScopeProject project =
                new CraftScopeProject(
                        UUID.randomUUID().toString(),
                        name,
                        targetItemId,
                        targetCount
                );

        projects.add(project);
        save();

        return project;
    }

    public static void deleteProject(String id) {
        projects.removeIf(
                project -> project.getId().equals(id)
        );

        save();
    }

    public static CraftScopeProject getProject(String id) {
        for (CraftScopeProject project : projects) {
            if (project.getId().equals(id)) {
                return project;
            }
        }

        return null;
    }

    public static void save() {
        if (projectFile == null) {
            return;
        }

        try {
            Files.createDirectories(projectFile.getParent());

            ProjectData data =
                    new ProjectData(projects);

            Files.writeString(
                    projectFile,
                    GSON.toJson(data)
            );

        } catch (IOException e) {
            Constants.LOG.error(
                    "Failed to save CraftScope projects",
                    e
            );
        }
    }

    private static void load() {
        projects.clear();

        if (projectFile == null
                || !Files.exists(projectFile)) {
            save();
            return;
        }

        try {
            String json =
                    Files.readString(projectFile);

            ProjectData data =
                    GSON.fromJson(
                            json,
                            ProjectData.class
                    );

            if (data != null && data.projects != null) {
                projects.addAll(data.projects);
            }

        } catch (Exception e) {
            Constants.LOG.error(
                    "Failed to load CraftScope projects",
                    e
            );
        }
    }

    private static class ProjectData {

        private List<CraftScopeProject> projects;

        private ProjectData(
                List<CraftScopeProject> projects
        ) {
            this.projects = projects;
        }
    }
}