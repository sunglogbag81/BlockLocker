package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.ProtectableBlocksSettings;
import nl.rutgerkok.blocklocker.ProtectionType;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;

import java.util.Locale;

/**
 * Runtime fallback that keeps BlockLocker compatible with new Minecraft blocks.
 *
 * <p>The config still decides the explicit defaults, but this class prevents new
 * containers/doors/trapdoors/fence gates from being missed just because the
 * bundled material list is stale.</p>
 */
final class UniversalProtectableBlocksSettings implements ProtectableBlocksSettings {

    @Override
    public boolean canProtect(Block block) {
        return canProtect(ProtectionType.CONTAINER, block)
                || canProtect(ProtectionType.DOOR, block)
                || canProtect(ProtectionType.ATTACHABLE, block);
    }

    @Override
    public boolean canProtect(ProtectionType type, Block block) {
        return switch (type) {
            case CONTAINER -> isContainerLike(block);
            case DOOR -> Tag.DOORS.isTagged(block.getType());
            case ATTACHABLE -> Tag.TRAPDOORS.isTagged(block.getType()) || Tag.FENCE_GATES.isTagged(block.getType());
        };
    }

    private boolean isContainerLike(Block block) {
        Material material = block.getType();
        if (block.getState() instanceof InventoryHolder) {
            return true;
        }
        String name = material.name().toLowerCase(Locale.ROOT);
        return name.endsWith("_shulker_box")
                || name.endsWith("_shelf")
                || name.endsWith("_anvil")
                || name.endsWith("_chest")
                || name.equals("anvil")
                || name.equals("barrel")
                || name.equals("beacon")
                || name.equals("brewing_stand")
                || name.equals("chest")
                || name.equals("chipped_anvil")
                || name.equals("crafter")
                || name.equals("crafting_table")
                || name.equals("damaged_anvil")
                || name.equals("decorated_pot")
                || name.equals("enchanting_table")
                || name.equals("jukebox")
                || name.equals("lectern")
                || name.equals("trapped_chest");
    }
}
