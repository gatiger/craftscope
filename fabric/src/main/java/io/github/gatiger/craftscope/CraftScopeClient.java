package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.production.CraftScopeMobDropRuntimeRegistry;
import io.github.gatiger.craftscope.production.CraftScopeMobDropSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/*
 * Fabric physical-client initialization.
 *
 * Client-only networking belongs here instead of CraftScope's common
 * ModInitializer so dedicated servers never need to load client
 * networking classes.
 */
public final class CraftScopeClient
        implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        ClientPlayNetworking.registerGlobalReceiver(
                CraftScopeMobDropSyncPayload.TYPE,
                (
                        payload,
                        context
                ) -> context.client().execute(
                        () -> {

                            CraftScopeMobDropRuntimeRegistry.replaceAll(
                                    payload.definitions()
                            );

                            Constants.LOG.info(
                                    "Received {} CraftScope mob-drop definitions from server",
                                    payload.definitions().size()
                            );
                        }
                )
        );

        /*
         * Never allow one server's runtime data to leak into another
         * server or back into the title screen.
         */
        ClientPlayConnectionEvents.DISCONNECT.register(
                (
                        handler,
                        client
                ) -> CraftScopeMobDropRuntimeRegistry.clear()
        );
    }
}