package io.github.gatiger.craftscope.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.gatiger.craftscope.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CraftScopeClientConfigManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static Path configDirectory;

    private CraftScopeClientConfigManager() {
    }

    public static void load(Path directory) {
        configDirectory = directory;

        Path configFile = directory.resolve("craftscope-client.json");

        if (!Files.exists(configFile)) {
            save();
            return;
        }

        try {
            String json = Files.readString(configFile);
            ConfigData data = GSON.fromJson(json, ConfigData.class);

            if (data == null) {
                return;
            }

            CraftScopeClientConfig.setPlacementMode(data.placementMode);
            CraftScopeClientConfig.setCustomXOffset(data.customXOffset);
            CraftScopeClientConfig.setCustomYOffset(data.customYOffset);

        } catch (Exception e) {
            Constants.LOG.error("Failed to load CraftScope client config", e);
        }
    }

    public static void save() {
        if (configDirectory == null) {
            return;
        }

        Path configFile = configDirectory.resolve("craftscope-client.json");

        ConfigData data = new ConfigData(
                CraftScopeClientConfig.getPlacementMode(),
                CraftScopeClientConfig.getCustomXOffset(),
                CraftScopeClientConfig.getCustomYOffset()
        );

        try {
            Files.createDirectories(configDirectory);
            Files.writeString(configFile, GSON.toJson(data));
        } catch (IOException e) {
            Constants.LOG.error("Failed to save CraftScope client config", e);
        }
    }

    private record ConfigData(
            CraftScopeClientConfig.PlacementMode placementMode,
            int customXOffset,
            int customYOffset
    ) {
    }
}