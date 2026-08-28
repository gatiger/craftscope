package io.github.gatiger.craftscope.network;

import io.github.gatiger.craftscope.Constants;
import io.github.gatiger.craftscope.production.CraftScopeMobDropCatalog;
import io.github.gatiger.craftscope.production.CraftScopeMobDropSyncPayload;
import io.github.gatiger.craftscope.production.CraftScopeMobLootTableScanner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/*
 * Fabric transport for authoritative CraftScope mob-drop data.
 */
public final class CraftScopeFabricNetworking {

    private CraftScopeFabricNetworking() {
    }

    public static void initialize() {

        PayloadTypeRegistry
                .playS2C()
                .register(
                        CraftScopeMobDropSyncPayload.TYPE,
                        CraftScopeMobDropSyncPayload.STREAM_CODEC
                );

        /*
         * Initial world/server discovery.
         *
         * By the time players join, the merged runtime catalog is
         * already ready to send.
         */
        ServerLifecycleEvents
                .SERVER_STARTED
                .register(
                        CraftScopeMobLootTableScanner::scan
                );

        /*
         * Rebuild the authoritative discovery snapshot after a
         * successful datapack reload.
         */
        ServerLifecycleEvents
                .END_DATA_PACK_RELOAD
                .register(
                        (
                                server,
                                resourceManager,
                                success
                        ) -> {

                            if (!success) {

                                Constants.LOG.warn(
                                        "CraftScope skipped loot scan because server datapack reload failed"
                                );

                                return;
                            }

                            CraftScopeMobLootTableScanner.scan(
                                    server
                            );
                        }
                );

        /*
         * Send the server-selected merged catalog whenever Fabric is
         * synchronizing server datapack content to a player.
         */
        ServerLifecycleEvents
                .SYNC_DATA_PACK_CONTENTS
                .register(
                        (
                                player,
                                joined
                        ) -> sendSnapshot(
                                player,
                                joined
                        )
                );
    }

    private static void sendSnapshot(
            ServerPlayer player,
            boolean joined
    ) {
        if (player == null) {
            return;
        }

        if (!ServerPlayNetworking.canSend(
                player,
                CraftScopeMobDropSyncPayload.TYPE
        )) {

            Constants.LOG.debug(
                    "CraftScope mob-drop sync not available for {}",
                    player.getGameProfile().getName()
            );

            return;
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                getServerSnapshot();

        ServerPlayNetworking.send(
                player,
                new CraftScopeMobDropSyncPayload(
                        definitions
                )
        );

        Constants.LOG.info(
                "{} {} CraftScope mob-drop definitions to {}",
                joined
                        ? "Sent"
                        : "Re-sent",
                definitions.size(),
                player.getGameProfile().getName()
        );
    }

    /*
     * Runtime definitions override matching baseline entries while
     * unsupported/partial mobs continue using baseline fallbacks.
     */
    private static List<CraftScopeMobDropCatalog.MobDefinition>
    getServerSnapshot() {

        return CraftScopeMobDropCatalog
                .getDefinitions();
    }
}