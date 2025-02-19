package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.services.PlatformInfoAccess;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FabricInfoAccess implements PlatformInfoAccess {
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isFlawlessFramesActive() {
        return FlawlessFrames.isActive();
    }

    @Override
    public boolean platformHasEarlyLoadingScreen() {
        return false;
    }

    @Override
    public boolean isModInLoadingList(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
