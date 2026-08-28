package io.github.gatiger.craftscope.network;

import io.github.gatiger.craftscope.Constants;
import io.github.gatiger.craftscope.production.CraftScopeMobDropCatalog;
import io.github.gatiger.craftscope.production.CraftScopeMobDropRuntimeRegistry;
import io.github.gatiger.craftscope.production.CraftScopeMobDropSyncPayload;
import io.github.gatiger.craftscope.production.CraftScopeMobLootTableScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/*
 * NeoForge transport for authoritative CraftScope mob-drop data.
 *
 * Server:
 *
 * live loot tables
 *      ↓
 * scanner
 *      ↓
 * conservative interpreter
 *      ↓
 * runtime registry
 *      ↓
 * merged fallback catalog
 *      ↓
 * network
 *
 * Client:
 *
 * synchronized authoritative snapshot
 *      ↓
 * runtime registry
 *      ↓
 * Recipe Tree / Process Diagram
 */
public final class CraftScopeNeoForgeNetworking {

    private static final String NETWORK_VERSION =
            "1";

    private static boolean initialized =
            false;

    private static boolean payloadHandlerRegistered =
            false;

    private CraftScopeNeoForgeNetworking() {
    }

    /*
     * Keep initialization idempotent so accidental duplicate loader
     * initialization cannot register the event handlers twice.
     */
    public static synchronized void initialize(
            IEventBus modEventBus
    ) {
        if (initialized) {

            Constants.LOG.warn(
                    "CraftScope NeoForge networking initialization was requested more than once; ignoring duplicate initialization"
            );

            return;
        }

        initialized =
                true;

        modEventBus.addListener(
                CraftScopeNeoForgeNetworking::registerPayloads
        );

        NeoForge.EVENT_BUS.addListener(
                CraftScopeNeoForgeNetworking::onDatapackSync
        );

        Constants.LOG.info(
                "Initialized CraftScope NeoForge networking"
        );
    }

    private static synchronized void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        if (payloadHandlerRegistered) {

            Constants.LOG.warn(
                    "CraftScope NeoForge payload registration was requested more than once; ignoring duplicate registration"
            );

            return;
        }

        payloadHandlerRegistered =
                true;

        PayloadRegistrar registrar =
                event
                        .registrar(
                                NETWORK_VERSION
                        )
                        .optional();

        registrar.playToClient(
                CraftScopeMobDropSyncPayload.TYPE,
                CraftScopeMobDropSyncPayload.STREAM_CODEC,
                CraftScopeNeoForgeNetworking::handleMobDropSync
        );

        Constants.LOG.info(
                "Registered CraftScope NeoForge mob-drop sync payload handler"
        );
    }

    private static void handleMobDropSync(
            CraftScopeMobDropSyncPayload payload,
            IPayloadContext context
    ) {
        CraftScopeMobDropRuntimeRegistry.replaceAll(
                payload.definitions()
        );

        Constants.LOG.info(
                "Received {} CraftScope mob-drop definitions from server",
                payload.definitions().size()
        );
    }

    private static void onDatapackSync(
            OnDatapackSyncEvent event
    ) {
        MinecraftServer server =
                event
                        .getPlayerList()
                        .getServer();

        /*
         * Rebuild discovery before synchronization so joins and
         * /reload always transmit data matching the current server.
         */
        CraftScopeMobLootTableScanner.scan(
                server
        );

        event
                .getRelevantPlayers()
                .forEach(
                        CraftScopeNeoForgeNetworking::sendSnapshot
                );
    }

    private static void sendSnapshot(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        if (!player.connection.hasChannel(
                CraftScopeMobDropSyncPayload.TYPE
        )) {

            Constants.LOG.debug(
                    "CraftScope mob-drop sync not available for {}",
                    player.getGameProfile().getName()
            );

            return;
        }

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                CraftScopeMobDropCatalog
                        .getDefinitions();

        player.connection.send(
                new CraftScopeMobDropSyncPayload(
                        definitions
                )
        );

        Constants.LOG.info(
                "Sent {} CraftScope mob-drop definitions to {}",
                definitions.size(),
                player.getGameProfile().getName()
        );
    }
}