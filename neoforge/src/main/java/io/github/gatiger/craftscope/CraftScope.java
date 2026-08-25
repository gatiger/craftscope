package io.github.gatiger.craftscope;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Constants.MOD_ID)
public class CraftScope {

    public CraftScope(IEventBus eventBus) {

        // Load CraftScope's client-side configuration.
        CraftScopeClientConfigManager.load(
                FMLPaths.CONFIGDIR.get()
        );

        // Use NeoForge to bootstrap the Common mod.
        Constants.LOG.info("Hello NeoForge world!");
        CommonClass.init();
    }
}