package nl.rutgerkok.blocklocker.impl.event;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.rutgerkok.blocklocker.ContainerSetting;
import nl.rutgerkok.blocklocker.impl.BlockLockerPluginImpl;
import nl.rutgerkok.blocklocker.impl.ContainerSettingsManager;
import nl.rutgerkok.blocklocker.profile.PlayerProfile;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.google.common.base.Preconditions;

import nl.rutgerkok.blocklocker.Permissions;
import nl.rutgerkok.blocklocker.Translator.Translation;

public final class BlockLockerCommand implements TabExecutor {

    private final BlockLockerPluginImpl plugin;
    private final Map<UUID, Boolean> settingMode = new HashMap<>();
    private final Map<UUID, Location> selectedBlocks = new HashMap<>();

    public BlockLockerCommand(BlockLockerPluginImpl plugin) {
        this.plugin = Preconditions.checkNotNull(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return reloadCommand(sender);
        }
        if (args[0].equalsIgnoreCase("setting") || args[0].equalsIgnoreCase("settings")) {
            return settingCommand(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "setting");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setting")) {
            return Arrays.asList("on", "off", "toggle");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setting") && args[1].equalsIgnoreCase("toggle")) {
            return Arrays.stream(ContainerSetting.values()).map(ContainerSetting::getCommandName).toList();
        }
        return Collections.emptyList();
    }

    public boolean isSettingMode(Player player) {
        return settingMode.getOrDefault(player.getUniqueId(), false);
    }

    public void openSettings(Player player, Block block, Protection protection) {
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return;
        }
        if (!plugin.getContainerSettingsManager().canStoreSettings(block)) {
            player.sendMessage(ChatColor.RED + "This block can't store BlockLocker settings.");
            return;
        }
        selectedBlocks.put(player.getUniqueId(), block.getLocation());
        sendSettingsMenu(player, block, protection);
    }

    private boolean canEdit(Player player, Protection protection) {
        PlayerProfile profile = plugin.getProfileFactory().fromPlayer(player);
        return protection.isOwner(profile) || player.hasPermission(Permissions.CAN_ADMIN);
    }

    private Optional<SelectedProtection> getSelectedProtection(Player player) {
        Location location = selectedBlocks.get(player.getUniqueId());
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Block block = location.getBlock();
        Optional<Protection> protection = plugin.getProtectionFinder().findProtection(block);
        if (protection.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SelectedProtection(block, protection.get()));
    }

    private String onOff(boolean value) {
        return value ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    private void sendSettingsMenu(Player player, Block block, Protection protection) {
        ContainerSettingsManager manager = plugin.getContainerSettingsManager();
        player.sendMessage(ChatColor.GOLD + "BlockLocker settings for " + block.getType().name().toLowerCase(Locale.ROOT)
                + " at " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        player.sendMessage(ChatColor.GRAY + "Click [toggle] to change only this block.");
        for (ContainerSetting setting : ContainerSetting.values()) {
            boolean value = manager.getEffective(block, protection, setting);
            sendSettingLine(player, setting, value);
        }
    }

    private void sendSettingLine(Player player, ContainerSetting setting, boolean value) {
        String command = "/blocklocker setting toggle " + setting.getCommandName();
        player.sendMessage(Component.text("- " + setting.getDisplayName() + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value ? "ON" : "OFF", value ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  [toggle]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)))));
    }

    private boolean settingCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_CANNOT_BE_USED_BY_CONSOLE);
            return true;
        }
        if (!player.hasPermission(Permissions.CAN_PROTECT)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }

        if (args.length == 1) {
            boolean enabled = !isSettingMode(player);
            settingMode.put(player.getUniqueId(), enabled);
            player.sendMessage(enabled
                    ? ChatColor.GOLD + "BlockLocker setting mode ON. Right-click one of your protected containers."
                    : ChatColor.GOLD + "BlockLocker setting mode OFF.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("on")) {
            settingMode.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GOLD + "BlockLocker setting mode ON. Right-click one of your protected containers.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            settingMode.put(player.getUniqueId(), false);
            selectedBlocks.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GOLD + "BlockLocker setting mode OFF.");
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
            return toggleSetting(player, args[2]);
        }
        return false;
    }

    private boolean toggleSetting(Player player, String settingName) {
        ContainerSetting setting;
        try {
            setting = ContainerSetting.fromCommandName(settingName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown setting. Use hopper-input, hopper-output, public-access or golem-access.");
            return true;
        }

        Optional<SelectedProtection> selected = getSelectedProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No protected container selected. Use /blocklocker setting and right-click a container first.");
            return true;
        }
        Block block = selected.get().block();
        Protection protection = selected.get().protection();
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }
        if (!plugin.getContainerSettingsManager().canStoreSettings(block)) {
            player.sendMessage(ChatColor.RED + "This block can't store BlockLocker settings.");
            return true;
        }

        boolean newValue = plugin.getContainerSettingsManager().toggle(block, protection, setting);
        plugin.getProtectionCache().invalidate(block);
        player.sendMessage(ChatColor.GOLD + setting.getDisplayName() + " is now " + onOff(newValue) + ChatColor.GOLD + ".");
        sendSettingsMenu(player, block, protection);
        return true;
    }

    private boolean reloadCommand(CommandSender sender) {
        if (!sender.hasPermission(Permissions.CAN_RELOAD)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_NO_PERMISSION);
            return true;
        }

        plugin.reload();
        plugin.getLogger().info(plugin.getTranslator().getWithoutColor(Translation.COMMAND_PLUGIN_RELOADED));
        if (!(sender instanceof ConsoleCommandSender)) {
            // Avoid sending message twice to the console
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_PLUGIN_RELOADED);
        }
        return true;
    }

    private record SelectedProtection(Block block, Protection protection) {
    }
}
