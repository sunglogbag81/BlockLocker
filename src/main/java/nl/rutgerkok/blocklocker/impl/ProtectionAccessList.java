package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.ProtectionSign;
import nl.rutgerkok.blocklocker.SignType;
import nl.rutgerkok.blocklocker.profile.Profile;
import nl.rutgerkok.blocklocker.protection.Protection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Utility methods for keeping the visible sign access list compact. */
public final class ProtectionAccessList {

    public static final int VISIBLE_PROFILE_LINES = 3;
    public static final int MAX_PROFILES_PER_SIGN = 6;

    private ProtectionAccessList() {
    }

    public static List<ProtectionSign> getMoreUsersSigns(Protection protection) {
        return protection.getSigns().stream()
                .filter(sign -> sign.getType() == SignType.MORE_USERS)
                .sorted(Comparator
                        .comparing((ProtectionSign sign) -> sign.getLocation().getWorld().getName())
                        .thenComparingInt(sign -> sign.getLocation().getBlockX())
                        .thenComparingInt(sign -> sign.getLocation().getBlockY())
                        .thenComparingInt(sign -> sign.getLocation().getBlockZ()))
                .toList();
    }

    public static Optional<ProtectionSign> getPrivateSign(Protection protection) {
        return protection.getSigns().stream()
                .filter(sign -> sign.getType() == SignType.PRIVATE)
                .findFirst();
    }

    public static List<Profile> getAccessProfiles(Protection protection) {
        List<Profile> profiles = new ArrayList<>();
        Optional<Profile> owner = protection.getOwner();
        for (ProtectionSign sign : protection.getSigns()) {
            List<Profile> signProfiles = sign.getProfiles();
            for (int i = 0; i < signProfiles.size(); i++) {
                if (sign.getType() == SignType.PRIVATE && i == 0) {
                    continue;
                }
                Profile profile = signProfiles.get(i);
                if (!shouldSkip(profile, owner)) {
                    addUniqueProfile(profiles, profile);
                }
            }
        }
        return profiles;
    }

    public static boolean isSameProfile(Profile first, Profile second) {
        return first.includes(second) || second.includes(first)
                || first.getDisplayName().equalsIgnoreCase(second.getDisplayName());
    }

    public static boolean normalize(Protection protection, BlockLockerPluginImpl plugin) {
        List<ProtectionSign> moreUsersSigns = getMoreUsersSigns(protection);
        if (moreUsersSigns.isEmpty()) {
            return false;
        }

        boolean changed = false;
        Optional<Profile> owner = protection.getOwner();
        List<Profile> seen = new ArrayList<>();
        List<Profile> hiddenPool = new ArrayList<>();

        Optional<ProtectionSign> privateSign = getPrivateSign(protection);
        if (privateSign.isPresent()) {
            List<Profile> original = privateSign.get().getProfiles();
            List<Profile> desired = new ArrayList<>();
            if (!original.isEmpty()) {
                desired.add(original.get(0));
            }
            for (int i = 1; i < original.size(); i++) {
                Profile profile = original.get(i);
                if (shouldSkip(profile, owner) || containsProfile(seen, profile)) {
                    continue;
                }
                if (desired.size() < VISIBLE_PROFILE_LINES) {
                    desired.add(profile);
                    seen.add(profile);
                } else {
                    hiddenPool.add(profile);
                }
            }
            if (!sameProfiles(desired, original)) {
                plugin.getSignParser().saveSign(privateSign.get().withProfiles(desired));
                changed = true;
            }
        }

        List<SignContents> moreUsersContents = new ArrayList<>();
        for (ProtectionSign sign : moreUsersSigns) {
            List<Profile> desired = new ArrayList<>();
            List<Profile> original = sign.getProfiles();
            for (int i = 0; i < original.size(); i++) {
                Profile profile = original.get(i);
                if (shouldSkip(profile, owner) || containsProfile(seen, profile)) {
                    continue;
                }
                if (i < VISIBLE_PROFILE_LINES) {
                    desired.add(profile);
                    seen.add(profile);
                } else {
                    hiddenPool.add(profile);
                }
            }
            moreUsersContents.add(new SignContents(sign, desired));
        }

        hiddenPool.removeIf(profile -> containsProfile(seen, profile));
        for (SignContents contents : moreUsersContents) {
            while (contents.profiles.size() < VISIBLE_PROFILE_LINES && !hiddenPool.isEmpty()) {
                Profile profile = hiddenPool.remove(0);
                if (!containsProfile(seen, profile)) {
                    contents.profiles.add(profile);
                    seen.add(profile);
                }
            }
        }
        for (SignContents contents : moreUsersContents) {
            while (contents.profiles.size() < MAX_PROFILES_PER_SIGN && !hiddenPool.isEmpty()) {
                Profile profile = hiddenPool.remove(0);
                if (!containsProfile(seen, profile)) {
                    contents.profiles.add(profile);
                    seen.add(profile);
                }
            }
        }

        for (SignContents contents : moreUsersContents) {
            if (contents.profiles.isEmpty()) {
                contents.profiles.add(emptyProfile(plugin));
            }
            if (!sameProfiles(contents.profiles, contents.sign.getProfiles())) {
                plugin.getSignParser().saveSign(contents.sign.withProfiles(contents.profiles));
                changed = true;
            }
        }

        if (changed) {
            plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        }
        return changed;
    }

    private static boolean shouldSkip(Profile profile, Optional<Profile> owner) {
        return profile.getDisplayName().isBlank() || owner.filter(value -> isSameProfile(value, profile)).isPresent();
    }

    private static void addUniqueProfile(List<Profile> target, Profile profile) {
        if (!containsProfile(target, profile)) {
            target.add(profile);
        }
    }

    private static boolean containsProfile(List<Profile> profiles, Profile profile) {
        return profiles.stream().anyMatch(existing -> isSameProfile(existing, profile));
    }

    private static Profile emptyProfile(BlockLockerPluginImpl plugin) {
        return plugin.getProfileFactory().fromNameAndUniqueId("", java.util.Optional.empty());
    }

    private static boolean sameProfiles(List<Profile> first, List<Profile> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!first.get(i).getDisplayName().equals(second.get(i).getDisplayName())) {
                return false;
            }
        }
        return true;
    }

    private record SignContents(ProtectionSign sign, List<Profile> profiles) {
    }
}
