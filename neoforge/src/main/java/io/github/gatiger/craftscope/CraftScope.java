package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import io.github.gatiger.craftscope.network.CraftScopeNeoForgeNetworking;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Constants.MOD_ID)
public class CraftScope {

    public CraftScope(
            IEventBus eventBus
    ) {

        CraftScopeClientConfigManager.load(
                FMLPaths.CONFIGDIR.get()
        );

        CraftScopeProjectManager.initialize(
                FMLPaths.CONFIGDIR.get()
        );

        /*
         * Register CraftScope's optional multiplayer runtime-data
         * transport.
         */
        CraftScopeNeoForgeNetworking.initialize(
                eventBus
        );

        Constants.LOG.info(
                "Initializing CraftScope on NeoForge"
        );

        CommonClass.init();
    }
}