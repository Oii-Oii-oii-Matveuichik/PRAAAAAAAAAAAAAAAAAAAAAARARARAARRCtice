package com.ancientcivilization.config;

import com.ancientcivilization.AncientCivilization;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private static final Logger LOGGER = LogManager.getLogger(ConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {
    }

    public static ModConfig load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(AncientCivilization.MODID + ".json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(new ModConfig()));
                LOGGER.info("First run: wrote default config to {}", path.toAbsolutePath());
            }
            return GSON.fromJson(Files.readString(path), ModConfig.class);
        } catch (IOException e) {
            LOGGER.error("Failed to load config from {}, using in-memory defaults", path, e);
            return new ModConfig();
        }
    }
}
