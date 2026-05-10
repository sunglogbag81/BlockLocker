package nl.rutgerkok.blocklocker;

/**
 * Per-container settings that can override the default sign-based behaviour.
 */
public enum ContainerSetting {
    HOPPER_INPUT("hopper-input", "호퍼 입력"),
    HOPPER_OUTPUT("hopper-output", "호퍼 출력"),
    PUBLIC_ACCESS("public-access", "공용 접근"),
    GOLEM_ACCESS("golem-access", "골렘 접근");

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
