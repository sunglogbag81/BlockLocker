package nl.rutgerkok.blocklocker;

/**
 * Per-container settings that can override the default sign-based behaviour.
 */
public enum ContainerSetting {
    HOPPER_INPUT("hopper-input", "Hopper input"),
    HOPPER_OUTPUT("hopper-output", "Hopper output"),
    PUBLIC_ACCESS("public-access", "Public access"),
    GOLEM_ACCESS("golem-access", "Golem access");

    private final String commandName;
    private final String displayName;

    ContainerSetting(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ContainerSetting fromCommandName(String name) {
        for (ContainerSetting setting : values()) {
            if (setting.commandName.equalsIgnoreCase(name)) {
                return setting;
            }
        }
        throw new IllegalArgumentException("Unknown container setting: " + name);
    }
}
