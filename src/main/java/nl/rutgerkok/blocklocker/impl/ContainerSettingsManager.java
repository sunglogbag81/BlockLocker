package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.ContainerSetting;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores per-container settings in the block's persistent data container.
 */
public final class ContainerSettingsManager {

    private final Map<ContainerSetting, NamespacedKey> keys = new EnumMap<>(ContainerSetting.class);
    private final BlockLockerPluginImpl plugin;

    ContainerSettingsManager(BlockLockerPluginImpl plugin) {
        this.plugin = plugin;
        for (ContainerSetting setting : ContainerSetting.values()) {
            keys.put(setting, new NamespacedKey(plugin, "setting_" + setting.getCommandName().replace('-', '_')));
        }
    }

    public boolean canStoreSettings(Block block) {
        return block.getState() instanceof TileState;
    }

    public Optional<Boolean> getOverride(Block block, ContainerSetting setting) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return Optional.empty();
        }
        Byte value = tileState.getPersistentDataContainer().get(keys.get(setting), PersistentDataType.BYTE);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value != 0);
    }

    public boolean getEffective(Block block, Protection protection, ContainerSetting setting) {
        Optional<Boolean> override = getOverride(block, setting);
        if (override.isPresent()) {
            return override.get();
        }
        return switch (setting) {
            case HOPPER_INPUT, HOPPER_OUTPUT -> protection.isAllowed(plugin.getProfileFactory().fromRedstone());
            case PUBLIC_ACCESS -> false;
            case GOLEM_ACCESS -> protection.isAllowed(plugin.getProfileFactory().fromGolem());
        };
    }

    public boolean toggle(Block block, Protection protection, ContainerSetting setting) {
        boolean newValue = !getEffective(block, protection, setting);
        set(block, setting, newValue);
        return newValue;
    }

    public void set(Block block, ContainerSetting setting, boolean value) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            throw new IllegalArgumentException("Block cannot store container settings: " + block.getType());
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        container.set(keys.get(setting), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        tileState.update();
    }
}
