package nl.rutgerkok.blocklocker.impl.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.base.Preconditions;
import nl.rutgerkok.blocklocker.ContainerSetting;
import nl.rutgerkok.blocklocker.Permissions;
import nl.rutgerkok.blocklocker.ProtectionSign;
import nl.rutgerkok.blocklocker.SignType;
import nl.rutgerkok.blocklocker.Translator.Translation;
import nl.rutgerkok.blocklocker.impl.BlockLockerPluginImpl;
import nl.rutgerkok.blocklocker.impl.ContainerSettingsManager;
import nl.rutgerkok.blocklocker.impl.ProtectionAccessList;
import nl.rutgerkok.blocklocker.profile.PlayerProfile;
import nl.rutgerkok.blocklocker.profile.Profile;
import nl.rutgerkok.blocklocker.protection.ContainerProtection;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class BlockLockerCommand implements TabExecutor, Listener {

    private static final String SETTINGS_TITLE = ChatColor.DARK_GREEN + "BlockLocker 설정";
    private static final Map<Integer, ContainerSetting> SETTINGS_BY_SLOT = Map.of(
            1, ContainerSetting.HOPPER_INPUT,
            3, ContainerSetting.HOPPER_OUTPUT,
            5, ContainerSetting.PUBLIC_ACCESS,
            7, ContainerSetting.GOLEM_ACCESS);

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
        if (args[0].equalsIgnoreCase("list")) {
            return listCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("trust")) {
            return trustCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("untrust")) {
            return untrustCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("transfer")) {
            return transferCommand(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "setting", "list", "trust", "untrust", "transfer", "bypass");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setting")) {
            return Arrays.asList("on", "off", "toggle");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setting") && args[1].equalsIgnoreCase("toggle")) {
            return Arrays.stream(ContainerSetting.values()).map(ContainerSetting::getCommandName).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")
                || args[0].equalsIgnoreCase("transfer"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bypass")) {
            return Arrays.asList("add", "remove", "list");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bypass") && args[1].equalsIgnoreCase("remove")) {
            return plugin.getBypassManager().getPlayerNames();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bypass") && args[1].equalsIgnoreCase("add")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
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
        openSettingsMenu(player, block, protection);
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

    private Optional<SelectedProtection> getLookedAtProtection(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            return Optional.empty();
        }
        Optional<Protection> protection = plugin.getProtectionFinder().findProtection(target);
        return protection.map(value -> new SelectedProtection(target, value));
    }

    private String onOff(boolean value) {
        return value ? ChatColor.GREEN + "켜짐" : ChatColor.RED + "꺼짐";
    }

    private void openSettingsMenu(Player player, Block block, Protection protection) {
        Inventory inventory = Bukkit.createInventory(new SettingsMenuHolder(block.getLocation()), 9, SETTINGS_TITLE);
        ContainerSettingsManager manager = plugin.getContainerSettingsManager();
        for (Map.Entry<Integer, ContainerSetting> entry : SETTINGS_BY_SLOT.entrySet()) {
            ContainerSetting setting = entry.getValue();
            boolean value = manager.getEffective(block, protection, setting);
            inventory.setItem(entry.getKey(), createSettingItem(setting, value));
        }
        player.openInventory(inventory);
    }

    private ItemStack createSettingItem(ContainerSetting setting, boolean value) {
        Material material = switch (setting) {
            case HOPPER_INPUT, HOPPER_OUTPUT -> Material.HOPPER;
            case PUBLIC_ACCESS -> Material.CHEST;
            case GOLEM_ACCESS -> Material.COPPER_INGOT;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + setting.getDisplayName() + ChatColor.GRAY + " - " + onOff(value));
            meta.setLore(List.of(ChatColor.GRAY + "클릭해서 전환합니다."));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SettingsMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }
        ContainerSetting setting = SETTINGS_BY_SLOT.get(event.getSlot());
        if (setting == null) {
            return;
        }
        toggleSetting(player, setting, true);
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
                    ? ChatColor.GOLD + "BlockLocker 설정 모드가 켜졌습니다. 보호된 컨테이너를 우클릭하면 설정 GUI가 열립니다."
                    : ChatColor.GOLD + "BlockLocker 설정 모드가 꺼졌습니다.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("on")) {
            settingMode.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GOLD + "BlockLocker 설정 모드가 켜졌습니다. 보호된 컨테이너를 우클릭하면 설정 GUI가 열립니다.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            settingMode.put(player.getUniqueId(), false);
            selectedBlocks.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "BlockLocker 설정 모드가 꺼졌습니다.");
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
            ContainerSetting setting;
            try {
                setting = ContainerSetting.fromCommandName(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "알 수 없는 설정입니다. hopper-input, hopper-output, public-access, golem-access 중 하나를 사용하세요.");
                return true;
            }
            return toggleSetting(player, setting, false);
        }
        return false;
    }

    private boolean toggleSetting(Player player, ContainerSetting setting, boolean reopenGui) {
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
        if (reopenGui) {
            openSettingsMenu(player, block, protection);
        }
        return true;
    }

    private Collection<Block> getSettingBlocks(Block selectedBlock, Protection protection) {
        if (protection instanceof ContainerProtection containerProtection) {
            return containerProtection.getProtectedBlocks();
        }
        return Collections.singletonList(selectedBlock);
    }

    private boolean listCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_CANNOT_BE_USED_BY_CONSOLE);
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "사용법: /blocklocker list");
            return true;
        }
        Optional<SelectedProtection> selected = getLookedAtProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "보호된 블록이나 표지판을 바라보고 /blocklocker list를 입력하세요.");
            return true;
        }
        Protection protection = selected.get().protection();
        player.sendMessage(ChatColor.GOLD + "BlockLocker 접근 목록");
        player.sendMessage(ChatColor.YELLOW + "소유자: " + ChatColor.WHITE + protection.getOwnerDisplayName());
        List<String> accessNames = ProtectionAccessList.getAccessProfiles(protection).stream()
                .map(Profile::getDisplayName)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        if (accessNames.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "추가 접근자: " + ChatColor.GRAY + "없음");
        } else {
            player.sendMessage(ChatColor.YELLOW + "추가 접근자: " + ChatColor.WHITE + String.join(", ", accessNames));
        }
        return true;
    }

    private boolean trustCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_CANNOT_BE_USED_BY_CONSOLE);
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "사용법: /blocklocker trust <플레이어>");
            return true;
        }
        Optional<SelectedProtection> selected = getLookedAtProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "보호된 블록이나 표지판을 바라보고 /blocklocker trust <플레이어>를 입력하세요.");
            return true;
        }
        Protection protection = selected.get().protection();
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }

        Profile profile = createPlayerProfile(args[1]);
        if (profile.getDisplayName().isBlank()) {
            player.sendMessage(ChatColor.RED + "플레이어 이름을 입력하세요.");
            return true;
        }
        if (protection.isAllowed(profile)) {
            player.sendMessage(ChatColor.GOLD + profile.getDisplayName() + " 플레이어는 이미 이 보호에 접근할 수 있습니다.");
            return true;
        }

        Optional<ProtectionSign> signWithSpace = findSignForTrust(protection);
        if (signWithSpace.isEmpty()) {
            player.sendMessage(ChatColor.RED + "남은 표지판 저장 공간이 없습니다. 보호 블록에 [추가 사용자] / [More Users] 표지판을 하나 더 설치한 뒤 다시 시도하세요.");
            return true;
        }
        ProtectionSign sign = signWithSpace.get();
        boolean hiddenLine = sign.getProfiles().size() >= ProtectionAccessList.VISIBLE_PROFILE_LINES;
        List<Profile> profiles = addProfileToFirstFreeLine(sign.getProfiles(), profile);
        plugin.getSignParser().saveSign(sign.withProfiles(profiles));
        plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        normalizeFresh(protection);
        player.sendMessage(ChatColor.GOLD + profile.getDisplayName() + " 플레이어를 이 보호에 추가했습니다.");
        if (hiddenLine) {
            player.sendMessage(ChatColor.YELLOW + "표지판 앞면의 표시 줄이 가득 차서 이름이 바로 보이지 않을 수 있습니다. [추가 사용자] / [More Users] 표지판을 추가하는 것을 권장합니다.");
        }
        return true;
    }

    private boolean untrustCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_CANNOT_BE_USED_BY_CONSOLE);
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "사용법: /blocklocker untrust <플레이어>");
            return true;
        }
        Optional<SelectedProtection> selected = getLookedAtProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "보호된 블록이나 표지판을 바라보고 /blocklocker untrust <플레이어>를 입력하세요.");
            return true;
        }
        Protection protection = selected.get().protection();
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }

        Profile profile = createPlayerProfile(args[1]);
        boolean changed = false;
        for (ProtectionSign sign : protection.getSigns()) {
            List<Profile> profiles = removeProfile(sign, profile);
            if (profiles.size() != sign.getProfiles().size() || !profiles.containsAll(sign.getProfiles())) {
                plugin.getSignParser().saveSign(sign.withProfiles(profiles));
                changed = true;
            }
        }

        if (!changed) {
            player.sendMessage(ChatColor.GOLD + profile.getDisplayName() + " 플레이어는 이 보호의 추가 접근 목록에 없습니다.");
            return true;
        }
        plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        normalizeFresh(protection);
        player.sendMessage(ChatColor.GOLD + profile.getDisplayName() + " 플레이어를 이 보호에서 제거했습니다.");
        return true;
    }

    private boolean transferCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getTranslator().sendMessage(sender, Translation.COMMAND_CANNOT_BE_USED_BY_CONSOLE);
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "사용법: /blocklocker transfer <플레이어>");
            return true;
        }
        Optional<SelectedProtection> selected = getLookedAtProtection(player);
        if (selected.isEmpty()) {
            player.sendMessage(ChatColor.RED + "보호된 블록이나 표지판을 바라보고 /blocklocker transfer <플레이어>를 입력하세요.");
            return true;
        }
        Protection protection = selected.get().protection();
        if (!canEdit(player, protection)) {
            plugin.getTranslator().sendMessage(player, Translation.COMMAND_NO_PERMISSION);
            return true;
        }
        Optional<ProtectionSign> mainSign = protection.getSigns().stream()
                .filter(sign -> sign.getType() == SignType.PRIVATE)
                .findFirst();
        if (mainSign.isEmpty()) {
            player.sendMessage(ChatColor.RED + "이 보호에는 소유권을 이전할 [개인] 표지판이 없습니다.");
            return true;
        }
        Profile newOwner = createPlayerProfile(args[1]);
        List<Profile> profiles = new ArrayList<>(mainSign.get().getProfiles());
        if (profiles.isEmpty()) {
            profiles.add(newOwner);
        } else {
            profiles.set(0, newOwner);
        }
        plugin.getSignParser().saveSign(mainSign.get().withProfiles(profiles));
        plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        player.sendMessage(ChatColor.GOLD + "보호 소유권을 " + newOwner.getDisplayName() + " 플레이어에게 이전했습니다.");
        return true;
    }

    private void normalizeFresh(Protection protection) {
        plugin.getProtectionFinder().findProtection(protection.getSomeProtectedBlock())
                .ifPresent(freshProtection -> ProtectionAccessList.normalize(freshProtection, plugin));
    }

    private List<Profile> removeProfile(ProtectionSign sign, Profile profileToRemove) {
        List<Profile> profiles = new ArrayList<>();
        List<Profile> existingProfiles = sign.getProfiles();
        for (int i = 0; i < existingProfiles.size(); i++) {
            Profile profile = existingProfiles.get(i);
            if (sign.getType() == SignType.PRIVATE && i == 0) {
                // The first line of the [Private] sign is the owner. Use /blocklocker transfer for that.
                profiles.add(profile);
                continue;
            }
            if (!isSamePlayer(profile, profileToRemove)) {
                profiles.add(profile);
            }
        }
        if (profiles.isEmpty()) {
            profiles.add(plugin.getProfileFactory().fromNameAndUniqueId("", Optional.empty()));
        }
        return profiles;
    }

    private boolean isSamePlayer(Profile first, Profile second) {
        return ProtectionAccessList.isSameProfile(first, second);
    }

    private Profile createPlayerProfile(String playerName) {
        Player onlinePlayer = plugin.getServer().getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return plugin.getProfileFactory().fromPlayer(onlinePlayer);
        }
        return plugin.getProfileFactory().fromNameAndUniqueId(playerName, Optional.empty());
    }

    private Optional<ProtectionSign> findSignForTrust(Protection protection) {
        List<ProtectionSign> moreUsersSigns = ProtectionAccessList.getMoreUsersSigns(protection);
        Optional<ProtectionSign> visibleMoreUsersSign = moreUsersSigns.stream()
                .filter(this::hasVisibleProfileLine)
                .findFirst();
        if (visibleMoreUsersSign.isPresent()) {
            return visibleMoreUsersSign;
        }
        Optional<ProtectionSign> hiddenMoreUsersSign = moreUsersSigns.stream()
                .filter(this::hasFreeProfileLine)
                .findFirst();
        if (hiddenMoreUsersSign.isPresent()) {
            return hiddenMoreUsersSign;
        }
        return protection.getSigns().stream()
                .filter(sign -> sign.getType() == SignType.PRIVATE)
                .filter(this::hasFreeProfileLine)
                .findFirst();
    }

    private boolean hasVisibleProfileLine(ProtectionSign sign) {
        return sign.getProfiles().size() < ProtectionAccessList.VISIBLE_PROFILE_LINES
                || sign.getProfiles().stream().limit(ProtectionAccessList.VISIBLE_PROFILE_LINES)
                .anyMatch(profile -> profile.getDisplayName().isBlank());
    }

    private boolean hasFreeProfileLine(ProtectionSign sign) {
        return sign.getProfiles().size() < ProtectionAccessList.MAX_PROFILES_PER_SIGN
                || sign.getProfiles().stream().anyMatch(profile -> profile.getDisplayName().isBlank());
    }

    private List<Profile> addProfileToFirstFreeLine(List<Profile> existingProfiles, Profile profile) {
        List<Profile> profiles = new ArrayList<>(existingProfiles);
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getDisplayName().isBlank()) {
                profiles.set(i, profile);
                return profiles;
            }
        }
        profiles.add(profile);
        return profiles;
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
            boolean changed = plugin.getBypassManager().add(playerName, plugin);
            plugin.getBypassManager().save(plugin);
            sender.sendMessage(ChatColor.GOLD + playerName + (changed
                    ? " 플레이어가 이제 BlockLocker 보호를 우회할 수 있습니다."
                    : " 플레이어는 이미 BlockLocker 보호 우회가 허용되어 있습니다."));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            String playerName = args[2];
            boolean changed = plugin.getBypassManager().remove(playerName, plugin);
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

    private record SettingsMenuHolder(Location location) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(null, 9);
        }
    }
}
