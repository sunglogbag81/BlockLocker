package nl.rutgerkok.blocklocker.impl.event;

import java.util.Arrays;
import java.util.Collection;
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
import nl.rutgerkok.blocklocker.protection.ContainerProtection;
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
        if (args[0].equalsIgnoreCase("bypass")) {
            return bypassCommand(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "setting", "bypass");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setting")) {
            return Arrays.asList("on", "off", "toggle");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setting") && args[1].equalsIgnoreCase("toggle")) {
            return Arrays.stream(ContainerSetting.values()).map(ContainerSetting::getCommandName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bypass")) {
            return Arrays.asList("add", "remove", "list");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bypass") && args[1].equalsIgnoreCase("remove")) {
            return plugin.getBypassManager().getPlayerNames();
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
            player.sendMessage(ChatColor.RED + "이 블록에는 BlockLocker 설정을 저장할 수 없습니다.");
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
        return value ? ChatColor.GREEN + "켜짐" : ChatColor.RED + "꺼짐";
    }

    private void sendSettingsMenu(Player player, Block block, Protection protection) {
        ContainerSettingsManager manager = plugin.getContainerSettingsManager();
        player.sendMessage(ChatColor.GOLD + block.getType().name().toLowerCase(Locale.ROOT)
                + " 설정 - 위치: " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        player.sendMessage(ChatColor.GRAY + "[전환]을 클릭하면 이 보호에 포함된 모든 컨테이너 블록에 함께 적용됩니다.");
        for (ContainerSetting setting : ContainerSetting.values()) {
            boolean value = manager.getEffective(block, protection, setting);
            sendSettingLine(player, setting, value);
        }
    }

    private void sendSettingLine(Player player, ContainerSetting setting, boolean value) {
        String command = "/blocklocker setting toggle " + setting.getCommandName();
        player.sendMessage(Component.text("- " + setting.getDisplayName() + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value ? "켜짐" : "꺼짐", value ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  [전환]", NamedTextColor.GRAY)
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
                    ? ChatColor.GOLD + "BlockLocker 설정 모드가 켜졌습니다. 보호된 컨테이너를 우클릭하세요."
                    : ChatColor.GOLD + "BlockLocker 설정 모드가 꺼졌습니다.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("on")) {
            settingMode.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GOLD + "BlockLocker 설정 모드가 켜졌습니다. 보호된 컨테이너를 우클릭하세요.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            settingMode.put(player.getUniqueId(), false);
            selectedBlocks.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GOLD + "BlockLocker 설정 모드가 꺼졌습니다.");
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
            player.sendMessage(ChatColor.RED + "알 수 없는 설정입니다. hopper-input, hopper-output, public-access, golem-access 중 하나를 사용하세요.");
            return true;
        }

        Optional<SelectedProtection> selected = getSelectedProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "선택된 보호 컨테이너가 없습니다. 먼저 /blocklocker setting을 입력한 뒤 컨테이너를 우클릭하세요.");
            return true;
        }
        Block block = selected.get().block();
        Protection protection = selected.get().protection();
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }
        if (!plugin.getContainerSettingsManager().canStoreSettings(block)) {
            player.sendMessage(ChatColor.RED + "이 블록에는 BlockLocker 설정을 저장할 수 없습니다.");
            return true;
        }

        boolean newValue = plugin.getContainerSettingsManager().toggle(block, protection, setting);
        for (Block protectedBlock : getSettingBlocks(block, protection)) {
            if (!protectedBlock.equals(block) && plugin.getContainerSettingsManager().canStoreSettings(protectedBlock)) {
                plugin.getContainerSettingsManager().set(protectedBlock, setting, newValue);
            }
            plugin.getProtectionCache().invalidate(protectedBlock);
        }
        player.sendMessage(ChatColor.GOLD + setting.getDisplayName() + " 설정이 " + onOff(newValue) + ChatColor.GOLD + " 상태가 되었습니다.");
        sendSettingsMenu(player, block, protection);
        return true;
    }

    private Collection<Block> getSettingBlocks(Block selectedBlock, Protection protection) {
        if (protection instanceof ContainerProtection containerProtection) {
            return containerProtection.getProtectedBlocks();
        }
        return Collections.singletonList(selectedBlock);
    }

    private boolean bypassCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.CAN_MANAGE_BYPASS)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_NO_PERMISSION);
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            List<String> players = plugin.getBypassManager().getPlayerNames();
            if (players.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "BlockLocker 보호를 우회할 수 있는 플레이어가 없습니다.");
            } else {
                sender.sendMessage(ChatColor.GOLD + "BlockLocker 우회 허용 플레이어: " + ChatColor.YELLOW + String.join(", ", players));
            }
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
            String playerName = args[2];
            boolean changed = plugin.getBypassManager().add(playerName);
            plugin.getBypassManager().save(plugin);
            sender.sendMessage(ChatColor.GOLD + playerName + (changed
                    ? " 플레이어가 이제 BlockLocker 보호를 우회할 수 있습니다."
                    : " 플레이어는 이미 BlockLocker 보호 우회가 허용되어 있습니다."));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            String playerName = args[2];
            boolean changed = plugin.getBypassManager().remove(playerName);
            plugin.getBypassManager().save(plugin);
            sender.sendMessage(ChatColor.GOLD + playerName + (changed
                    ? " 플레이어는 더 이상 BlockLocker 보호를 우회할 수 없습니다."
                    : " 플레이어는 BlockLocker 우회 목록에 없었습니다."));
            return true;
        }

        sender.sendMessage(ChatColor.RED + "사용법: /blocklocker bypass <add|remove|list> [플레이어]");
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
