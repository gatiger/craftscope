package io.github.gatiger.craftscope.client;

import io.github.gatiger.craftscope.Constants;
import io.github.gatiger.craftscope.production.CraftScopeMobDropRuntimeRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/*
 * Physical-client-only NeoForge lifecycle events.
 *
 * Runtime server data must never survive a disconnect because the
 * next server may have completely different mods, datapacks, or loot
 * tables.
 */
@EventBusSubscriber(
        modid = Constants.MOD_ID,
        value = Dist.CLIENT
)
public final class CraftScopeNeoForgeClientEvents {

    private CraftScopeNeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        CraftScopeMobDropRuntimeRegistry.clear();

        Constants.LOG.debug(
                "Cleared CraftScope runtime mob-drop definitions"
        );
    }
}