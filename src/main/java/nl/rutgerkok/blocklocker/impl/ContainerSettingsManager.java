package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.ContainerSetting;
import nl.rutgerkok.blocklocker.ProtectionSign;
import nl.rutgerkok.blocklocker.SignType;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores per-protection settings.
 *
 * <p>Tile blocks store their own settings directly. Non-tile protections such
 * as doors, trapdoors and fence gates cannot hold persistent data themselves,
 * so their settings are stored on the protection sign instead.</p>
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

    public boolean canStoreSettings(Block block, Protection protection) {
        return getStorageBlock(block, protection).isPresent();
    }

    public Optional<Boolean> getOverride(Block block, ContainerSetting setting) {
        return getOverrideFromStorage(block, setting);
    }

    public Optional<Boolean> getOverride(Block block, Protection protection, ContainerSetting setting) {
        return getStorageBlock(block, protection).flatMap(storage -> getOverrideFromStorage(storage, setting));
    }

    public boolean getEffective(Block block, Protection protection, ContainerSetting setting) {
        Optional<Boolean> override = getOverride(block, protection, setting);
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
        set(block, protection, setting, newValue);
        return newValue;
    }

    public void set(Block block, ContainerSetting setting, boolean value) {
        setStorage(block, setting, value);
    }

    public void set(Block block, Protection protection, ContainerSetting setting, boolean value) {
        Block storageBlock = getStorageBlock(block, protection)
                .orElseThrow(() -> new IllegalArgumentException("Protection cannot store container settings: " + block.getType()));
        setStorage(storageBlock, setting, value);
    }

    private Optional<Boolean> getOverrideFromStorage(Block storageBlock, ContainerSetting setting) {
        BlockState state = storageBlock.getState();
        if (!(state instanceof TileState tileState)) {
            return Optional.empty();
        }
        Byte value = tileState.getPersistentDataContainer().get(keys.get(setting), PersistentDataType.BYTE);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value != 0);
    }

    private Optional<Block> getStorageBlock(Block block, Protection protection) {
        if (canStoreSettings(block)) {
            return Optional.of(block);
        }
        return protection.getSigns().stream()
                .sorted(Comparator
                        .comparing((ProtectionSign sign) -> sign.getType() != SignType.PRIVATE)
                        .thenComparing(sign -> sign.getLocation().getWorld().getName())
                        .thenComparingInt(sign -> sign.getLocation().getBlockX())
                        .thenComparingInt(sign -> sign.getLocation().getBlockY())
                        .thenComparingInt(sign -> sign.getLocation().getBlockZ()))
                .map(sign -> sign.getLocation().getBlock())
                .filter(this::canStoreSettings)
                .findFirst();
    }

    private void setStorage(Block storageBlock, ContainerSetting setting, boolean value) {
        BlockState state = storageBlock.getState();
        if (!(state instanceof TileState tileState)) {
            throw new IllegalArgumentException("Block cannot store container settings: " + storageBlock.getType());
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        container.set(keys.get(setting), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        tileState.update();
    }
}
