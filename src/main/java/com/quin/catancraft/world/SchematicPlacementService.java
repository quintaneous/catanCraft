package com.quin.catancraft.world;

import com.mojang.brigadier.StringReader;
import com.quin.catancraft.CatanCraft;
import com.quin.catancraft.map.MapAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SchematicPlacementService {
    public record Result(boolean success, String message, int changedBlocks) {}

    private record SchematicData(
            int width,
            int height,
            int length,
            int anchorX,
            int anchorY,
            int anchorZ,
            BlockState[] palette,
            int[] states
    ) {}

    private static final Map<String, SchematicData> CACHE = new HashMap<>();

    private SchematicPlacementService() {}

    public static Result place(
            MinecraftServer server,
            String dimensionId,
            String templateName,
            MapAnchor worldAnchor
    ) {
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().toString().equals(dimensionId)) {
                level = candidate;
                break;
            }
        }
        if (level == null) {
            return new Result(
                    false,
                    "Target dimension is not loaded: " + dimensionId,
                    0
            );
        }
        return place(level, templateName, worldAnchor, true);
    }

    public static Result place(
            MinecraftServer server,
            String dimensionId,
            String templateName,
            MapAnchor worldAnchor,
            boolean applyAir
    ) {
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().toString().equals(dimensionId)) {
                level = candidate;
                break;
            }
        }
        if (level == null) {
            return new Result(
                    false,
                    "Target dimension is not loaded: " + dimensionId,
                    0
            );
        }
        return place(level, templateName, worldAnchor, applyAir);
    }

    public static Result place(
            ServerLevel level,
            String templateName,
            MapAnchor worldAnchor
    ) {
        return place(level, templateName, worldAnchor, true);
    }

    public static Result place(
            ServerLevel level,
            String templateName,
            MapAnchor worldAnchor,
            boolean applyAir
    ) {
        if (templateName == null || templateName.isBlank()) {
            return new Result(false, "No schematic template is configured.", 0);
        }
        if (worldAnchor == null) {
            return new Result(false, "No world anchor is configured.", 0);
        }

        final SchematicData schematic;
        try {
            schematic = load(templateName);
        } catch (Exception ex) {
            CatanCraft.LOGGER.error(
                    "Failed to load schematic {}", templateName, ex);
            return new Result(
                    false,
                    "Could not load schematic " + templateName + ": " + ex.getMessage(),
                    0
            );
        }

        Rotation rotation = rotation(worldAnchor.rotation());
        int changed = 0;
        int index = 0;

        for (int y = 0; y < schematic.height(); y++) {
            for (int z = 0; z < schematic.length(); z++) {
                for (int x = 0; x < schematic.width(); x++) {
                    int paletteIndex = schematic.states()[index++];
                    if (paletteIndex < 0 || paletteIndex >= schematic.palette().length) {
                        return new Result(
                                false,
                                "Schematic contains an invalid palette index.",
                                changed
                        );
                    }

                    int dx = x - schematic.anchorX();
                    int dz = z - schematic.anchorZ();
                    int rotatedX;
                    int rotatedZ;

                    switch (rotation) {
                        case CLOCKWISE_90 -> {
                            rotatedX = -dz;
                            rotatedZ = dx;
                        }
                        case CLOCKWISE_180 -> {
                            rotatedX = -dx;
                            rotatedZ = -dz;
                        }
                        case COUNTERCLOCKWISE_90 -> {
                            rotatedX = dz;
                            rotatedZ = -dx;
                        }
                        default -> {
                            rotatedX = dx;
                            rotatedZ = dz;
                        }
                    }

                    BlockPos worldPos = new BlockPos(
                            worldAnchor.x() + rotatedX,
                            worldAnchor.y() + (y - schematic.anchorY()),
                            worldAnchor.z() + rotatedZ
                    );

                    BlockState target = schematic.palette()[paletteIndex].rotate(rotation);
                    if (!applyAir && target.isAir()) continue;

                    BlockState current = level.getBlockState(worldPos);
                    if (current.equals(target)) continue;

                    // Flag 2 sends the block change to clients without cascading
                    // normal survival-style neighbor destruction during a bulk paste.
                    level.setBlock(worldPos, target, 2);
                    changed++;
                }
            }
        }

        CatanCraft.LOGGER.info(
                "Placed schematic {} at {},{},{} rotation {} ({} changed blocks)",
                templateName,
                worldAnchor.x(),
                worldAnchor.y(),
                worldAnchor.z(),
                worldAnchor.rotation(),
                changed
        );

        return new Result(
                true,
                "Placed " + templateName + " (" + changed + " changed blocks).",
                changed
        );
    }

    private static SchematicData load(String templateName) throws IOException {
        SchematicData cached = CACHE.get(templateName);
        if (cached != null) return cached;

        String resource = "/data/catancraft/schematics/" + templateName + ".schem";
        CompoundTag schematic;

        try (InputStream input =
                     SchematicPlacementService.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing bundled resource " + resource);
            }
            schematic = NbtIo.readCompressed(input);
        }

        int width = schematic.getShort("Width");
        int height = schematic.getShort("Height");
        int length = schematic.getShort("Length");
        int expected = width * height * length;

        int[] offset = schematic.getIntArray("Offset");
        if (offset.length != 3) {
            throw new IOException("schematic Offset must contain 3 integers");
        }

        int anchorX = -offset[0];
        int anchorY = -offset[1];
        int anchorZ = -offset[2];

        CompoundTag paletteTag = schematic.getCompound("Palette");
        int paletteSize = schematic.getInt("PaletteMax");
        BlockState[] palette = new BlockState[paletteSize];

        for (String stateText : paletteTag.getAllKeys()) {
            int paletteIndex = paletteTag.getInt(stateText);
            if (paletteIndex < 0 || paletteIndex >= palette.length) {
                throw new IOException("invalid palette index " + paletteIndex);
            }
            palette[paletteIndex] = parseBlockState(stateText);
        }

        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                throw new IOException("palette entry " + i + " is missing");
            }
        }

        int[] states = decodeVarInts(schematic.getByteArray("BlockData"), expected);

        SchematicData loaded = new SchematicData(
                width,
                height,
                length,
                anchorX,
                anchorY,
                anchorZ,
                palette,
                states
        );
        CACHE.put(templateName, loaded);
        return loaded;
    }

    private static int[] decodeVarInts(byte[] raw, int expected) throws IOException {
        int[] values = new int[expected];
        int count = 0;
        int value = 0;
        int shift = 0;

        for (byte signed : raw) {
            int current = signed & 0xFF;
            value |= (current & 0x7F) << shift;

            if ((current & 0x80) != 0) {
                shift += 7;
                if (shift > 28) {
                    throw new IOException("schematic VarInt is too large");
                }
                continue;
            }

            if (count >= expected) {
                throw new IOException("schematic contains too many block values");
            }
            values[count++] = value;
            value = 0;
            shift = 0;
        }

        if (shift != 0 || count != expected) {
            throw new IOException(
                    "schematic block count mismatch: expected " +
                            expected + ", decoded " + count);
        }
        return values;
    }

    private static BlockState parseBlockState(String text) throws IOException {
        String blockName = text;
        String propertiesText = "";

        int open = text.indexOf('[');
        if (open >= 0) {
            if (!text.endsWith("]")) {
                throw new IOException("invalid block state: " + text);
            }
            blockName = text.substring(0, open);
            propertiesText = text.substring(open + 1, text.length() - 1);
        }

        ResourceLocation id = ResourceLocation.tryParse(blockName);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IOException("unknown block in schematic: " + blockName);
        }

        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();

        if (!propertiesText.isBlank()) {
            for (String pair : propertiesText.split(",")) {
                String[] pieces = pair.split("=", 2);
                if (pieces.length != 2) {
                    throw new IOException("invalid block property: " + pair);
                }

                Property<?> property =
                        block.getStateDefinition().getProperty(pieces[0]);
                if (property == null) {
                    throw new IOException(
                            "unknown property " + pieces[0] + " for " + blockName);
                }

                state = withProperty(state, property, pieces[1], text);
            }
        }

        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(
            BlockState state,
            Property property,
            String value,
            String fullState
    ) throws IOException {
        Optional parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new IOException(
                    "invalid value " + value + " in " + fullState);
        }
        return state.setValue(property, (Comparable) parsed.get());
    }

    private static Rotation rotation(int degrees) {
        return switch (degrees) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
