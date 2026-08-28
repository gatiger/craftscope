package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import io.github.gatiger.craftscope.network.CraftScopeFabricNetworking;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class CraftScope implements ModInitializer {

    @Override
    public void onInitialize() {

        CraftScopeClientConfigManager.load(
                FabricLoader.getInstance().getConfigDir()
        );

        CraftScopeProjectManager.initialize(
                FabricLoader.getInstance().getConfigDir()
        );

        /*
         * Registers CraftScope's optional server -> client runtime
         * synchronization channel.
         */
        CraftScopeFabricNetworking.initialize();

        Constants.LOG.info(
                "Initializing CraftScope on Fabric"
        );

        CommonClass.init();
    }
}