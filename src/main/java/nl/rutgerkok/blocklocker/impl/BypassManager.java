package nl.rutgerkok.blocklocker.impl;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Stores players that are allowed to bypass protections without being operators.
 */
public final class BypassManager {

    private final File file;
    private final Set<String> playerNames = new LinkedHashSet<>();

    BypassManager(BlockLockerPluginImpl plugin) {
        this.file = new File(plugin.getDataFolder(), "bypass.yml");
        load(plugin);
    }

    public boolean add(String playerName) {
        return playerNames.add(normalize(playerName));
    }

    public boolean canBypass(Player player) {
        return playerNames.contains(normalize(player.getName()));
    }

    public List<String> getPlayerNames() {
        return new ArrayList<>(playerNames);
    }

    public boolean remove(String playerName) {
        return playerNames.remove(normalize(playerName));
    }

    public void save(BlockLockerPluginImpl plugin) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("players", getPlayerNames());
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save bypass.yml", e);
        }
    }

    private void load(BlockLockerPluginImpl plugin) {
        playerNames.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String playerName : config.getStringList("players")) {
            if (!playerName.isBlank()) {
                playerNames.add(normalize(playerName));
            }
        }
    }

    private String normalize(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
