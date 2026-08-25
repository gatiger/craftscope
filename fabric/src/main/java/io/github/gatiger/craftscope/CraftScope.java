package io.github.gatiger.craftscope;

import net.fabricmc.api.ModInitializer;
import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;

public class CraftScope implements ModInitializer {

    @Override
    public void onInitialize() {

        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        CraftScopeClientConfigManager.load(
                FabricLoader.getInstance().getConfigDir()
        );

        CraftScopeProjectManager.initialize(
                FabricLoader.getInstance().getConfigDir()
        );

        // Use Fabric to bootstrap the Common mod.
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
    }
}
