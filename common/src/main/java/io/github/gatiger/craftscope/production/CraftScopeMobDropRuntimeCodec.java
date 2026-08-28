package io.github.gatiger.craftscope.production;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/*
 * Shared network serialization format for CraftScope runtime
 * mob-drop definitions.
 *
 * This class deliberately contains no Fabric or NeoForge code.
 *
 * Later:
 *
 *     server loot discovery
 *              ↓
 *     List<MobDefinition>
 *              ↓
 *     CraftScopeMobDropRuntimeCodec
 *              ↓
 *     Fabric / NeoForge packet
 *              ↓
 *     CraftScopeMobDropRuntimeRegistry.replaceAll(...)
 *
 * Keeping the serialization in common code guarantees that both
 * loaders interpret runtime mob/drop data identically.
 */
public final class CraftScopeMobDropRuntimeCodec {

    /*
     * Increment this only if the encoded structure changes in an
     * incompatible way.
     */
    private static final int FORMAT_VERSION =
            1;

    /*
     * Defensive limits.
     *
     * Runtime data can eventually originate from a multiplayer
     * server, so malformed packets must not be allowed to allocate
     * arbitrarily large collections.
     */
    private static final int MAX_MOBS =
            4096;

    private static final int MAX_DROPS_PER_MOB =
            1024;

    private static final int MAX_REQUIREMENTS =
            128;

    private static final int MAX_SOURCE_MOD_ID_LENGTH =
            128;

    private static final int MAX_REQUIREMENT_LENGTH =
            1024;

    private CraftScopeMobDropRuntimeCodec() {
    }

    public static int getFormatVersion() {
        return FORMAT_VERSION;
    }

    /*
     * Encode a complete runtime snapshot.
     *
     * Null definitions inside the supplied collection are ignored.
     */
    public static void write(
            FriendlyByteBuf buffer,
            Collection<CraftScopeMobDropCatalog.MobDefinition> definitions
    ) {
        Objects.requireNonNull(
                buffer,
                "buffer"
        );

        List<CraftScopeMobDropCatalog.MobDefinition> snapshot =
                sanitizeDefinitions(
                        definitions
                );

        if (snapshot.size() > MAX_MOBS) {
            throw new IllegalArgumentException(
                    "Too many runtime mob definitions: "
                            + snapshot.size()
            );
        }

        buffer.writeInt(
                FORMAT_VERSION
        );

        buffer.writeInt(
                snapshot.size()
        );

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                snapshot) {

            writeMobDefinition(
                    buffer,
                    definition
            );
        }
    }

    /*
     * Decode a complete runtime snapshot.
     *
     * The returned list is immutable and can be passed directly to:
     *
     * CraftScopeMobDropRuntimeRegistry.replaceAll(...)
     */
    public static List<CraftScopeMobDropCatalog.MobDefinition> read(
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(
                buffer,
                "buffer"
        );

        int version =
                buffer.readInt();

        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported CraftScope mob-drop runtime format: "
                            + version
            );
        }

        int mobCount =
                readCount(
                        buffer,
                        "mob definitions",
                        MAX_MOBS
                );

        List<CraftScopeMobDropCatalog.MobDefinition> definitions =
                new ArrayList<>(
                        mobCount
                );

        for (int i = 0;
             i < mobCount;
             i++) {

            definitions.add(
                    readMobDefinition(
                            buffer
                    )
            );
        }

        return List.copyOf(
                definitions
        );
    }

    /*
     * Convenience method for the eventual client packet receiver.
     */
    public static void readAndApply(
            FriendlyByteBuf buffer
    ) {
        CraftScopeMobDropRuntimeRegistry.replaceAll(
                read(
                        buffer
                )
        );
    }

    private static void writeMobDefinition(
            FriendlyByteBuf buffer,
            CraftScopeMobDropCatalog.MobDefinition definition
    ) {
        buffer.writeResourceLocation(
                definition.entityTypeId()
        );

        buffer.writeUtf(
                definition.sourceModId(),
                MAX_SOURCE_MOD_ID_LENGTH
        );

        ResourceLocation iconItemId =
                definition.iconItemId();

        buffer.writeBoolean(
                iconItemId != null
        );

        if (iconItemId != null) {
            buffer.writeResourceLocation(
                    iconItemId
            );
        }

        buffer.writeInt(
                definition.priority()
        );

        writeStrings(
                buffer,
                definition.requirements()
        );

        List<CraftScopeMobDropCatalog.DropDefinition> drops =
                definition.drops();

        if (drops.size()
                > MAX_DROPS_PER_MOB) {

            throw new IllegalArgumentException(
                    "Too many drops for entity "
                            + definition.entityTypeId()
                            + ": "
                            + drops.size()
            );
        }

        buffer.writeInt(
                drops.size()
        );

        for (CraftScopeMobDropCatalog.DropDefinition drop :
                drops) {

            writeDropDefinition(
                    buffer,
                    drop
            );
        }
    }

    private static CraftScopeMobDropCatalog.MobDefinition
    readMobDefinition(
            FriendlyByteBuf buffer
    ) {
        ResourceLocation entityTypeId =
                buffer.readResourceLocation();

        String sourceModId =
                buffer.readUtf(
                        MAX_SOURCE_MOD_ID_LENGTH
                );

        ResourceLocation iconItemId =
                null;

        if (buffer.readBoolean()) {
            iconItemId =
                    buffer.readResourceLocation();
        }

        int priority =
                buffer.readInt();

        List<String> requirements =
                readStrings(
                        buffer
                );

        int dropCount =
                readCount(
                        buffer,
                        "mob drops",
                        MAX_DROPS_PER_MOB
                );

        List<CraftScopeMobDropCatalog.DropDefinition> drops =
                new ArrayList<>(
                        dropCount
                );

        for (int i = 0;
             i < dropCount;
             i++) {

            drops.add(
                    readDropDefinition(
                            buffer
                    )
            );
        }

        return new CraftScopeMobDropCatalog.MobDefinition(
                entityTypeId,
                sourceModId,
                iconItemId,
                priority,
                requirements,
                drops
        );
    }

    private static void writeDropDefinition(
            FriendlyByteBuf buffer,
            CraftScopeMobDropCatalog.DropDefinition drop
    ) {
        buffer.writeResourceLocation(
                drop.itemId()
        );

        buffer.writeByte(
                drop.mode().ordinal()
        );

        buffer.writeLong(
                drop.minimum()
        );

        buffer.writeLong(
                drop.maximum()
        );

        buffer.writeLong(
                drop.amount()
        );

        buffer.writeDouble(
                drop.chance()
        );

        writeStrings(
                buffer,
                drop.targetRequirements()
        );
    }

    private static CraftScopeMobDropCatalog.DropDefinition
    readDropDefinition(
            FriendlyByteBuf buffer
    ) {
        ResourceLocation itemId =
                buffer.readResourceLocation();

        int modeOrdinal =
                buffer.readUnsignedByte();

        CraftScopeMobDropCatalog.DropMode[] modes =
                CraftScopeMobDropCatalog.DropMode.values();

        if (modeOrdinal < 0
                || modeOrdinal >= modes.length) {

            throw new IllegalArgumentException(
                    "Invalid CraftScope mob-drop mode: "
                            + modeOrdinal
            );
        }

        CraftScopeMobDropCatalog.DropMode mode =
                modes[
                        modeOrdinal
                ];

        long minimum =
                buffer.readLong();

        long maximum =
                buffer.readLong();

        long amount =
                buffer.readLong();

        double chance =
                buffer.readDouble();

        List<String> targetRequirements =
                readStrings(
                        buffer
                );

        return new CraftScopeMobDropCatalog.DropDefinition(
                itemId,
                mode,
                minimum,
                maximum,
                amount,
                chance,
                targetRequirements
        );
    }

    private static void writeStrings(
            FriendlyByteBuf buffer,
            List<String> values
    ) {
        List<String> safeValues =
                values == null
                        ? List.of()
                        : values;

        if (safeValues.size()
                > MAX_REQUIREMENTS) {

            throw new IllegalArgumentException(
                    "Too many CraftScope mob-drop requirements: "
                            + safeValues.size()
            );
        }

        buffer.writeInt(
                safeValues.size()
        );

        for (String value :
                safeValues) {

            String safeValue =
                    value == null
                            ? ""
                            : value;

            buffer.writeUtf(
                    safeValue,
                    MAX_REQUIREMENT_LENGTH
            );
        }
    }

    private static List<String> readStrings(
            FriendlyByteBuf buffer
    ) {
        int count =
                readCount(
                        buffer,
                        "requirements",
                        MAX_REQUIREMENTS
                );

        List<String> values =
                new ArrayList<>(
                        count
                );

        for (int i = 0;
             i < count;
             i++) {

            values.add(
                    buffer.readUtf(
                            MAX_REQUIREMENT_LENGTH
                    )
            );
        }

        return List.copyOf(
                values
        );
    }

    private static int readCount(
            FriendlyByteBuf buffer,
            String description,
            int maximum
    ) {
        int count =
                buffer.readInt();

        if (count < 0
                || count > maximum) {

            throw new IllegalArgumentException(
                    "Invalid CraftScope "
                            + description
                            + " count: "
                            + count
            );
        }

        return count;
    }

    private static List<CraftScopeMobDropCatalog.MobDefinition>
    sanitizeDefinitions(
            Collection<CraftScopeMobDropCatalog.MobDefinition> definitions
    ) {
        if (definitions == null
                || definitions.isEmpty()) {

            return List.of();
        }

        List<CraftScopeMobDropCatalog.MobDefinition> result =
                new ArrayList<>();

        for (CraftScopeMobDropCatalog.MobDefinition definition :
                definitions) {

            if (definition != null) {
                result.add(
                        definition
                );
            }
        }

        return List.copyOf(
                result
        );
    }
}