package io.github.gatiger.craftscope.production;

import io.github.gatiger.craftscope.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/*
 * Server -> client snapshot of CraftScope's authoritative runtime
 * mob-drop definitions.
 *
 * The payload itself is loader-neutral. Fabric and NeoForge only
 * handle registration and transport.
 */
public record CraftScopeMobDropSyncPayload(
        List<CraftScopeMobDropCatalog.MobDefinition> definitions
) implements CustomPacketPayload {

    public static final Type<CraftScopeMobDropSyncPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            Constants.MOD_ID,
                            "mob_drop_sync"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CraftScopeMobDropSyncPayload
            > STREAM_CODEC =
            StreamCodec.of(
                    CraftScopeMobDropSyncPayload::encode,
                    CraftScopeMobDropSyncPayload::decode
            );

    public CraftScopeMobDropSyncPayload {
        definitions =
                definitions == null
                        ? List.of()
                        : List.copyOf(
                        definitions
                );
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            CraftScopeMobDropSyncPayload payload
    ) {
        CraftScopeMobDropRuntimeCodec.write(
                buffer,
                payload.definitions()
        );
    }

    private static CraftScopeMobDropSyncPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        return new CraftScopeMobDropSyncPayload(
                CraftScopeMobDropRuntimeCodec.read(
                        buffer
                )
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}