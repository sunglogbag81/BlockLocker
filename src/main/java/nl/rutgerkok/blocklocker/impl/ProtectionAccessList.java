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
                if (shouldSkip(profile, owner)) {
                    continue;
                }
                addUniqueProfile(profiles, profile);
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
        List<Profile> seenAdditionalProfiles = new ArrayList<>();

        List<Profile> privateOverflow = new ArrayList<>();
        Optional<ProtectionSign> privateSign = getPrivateSign(protection);
        if (privateSign.isPresent()) {
            List<Profile> privateProfiles = new ArrayList<>(privateSign.get().getProfiles());
            List<Profile> cleanedPrivateProfiles = new ArrayList<>();
            for (int i = 0; i < privateProfiles.size(); i++) {
                Profile profile = privateProfiles.get(i);
                if (i == 0) {
                    cleanedPrivateProfiles.add(profile);
                    continue;
                }
                if (shouldSkip(profile, owner) || seenAdditionalProfiles.stream().anyMatch(seen -> isSameProfile(seen, profile))) {
                    continue;
                }
                if (cleanedPrivateProfiles.size() < VISIBLE_PROFILE_LINES) {
                    cleanedPrivateProfiles.add(profile);
                    seenAdditionalProfiles.add(profile);
                } else {
                    privateOverflow.add(profile);
                }
            }
            if (!sameProfiles(cleanedPrivateProfiles, privateProfiles)) {
                plugin.getSignParser().saveSign(privateSign.get().withProfiles(cleanedPrivateProfiles));
                changed = true;
            }
        }

        for (ProtectionSign moreUsersSign : moreUsersSigns) {
            List<Profile> cleanedProfiles = new ArrayList<>();
            for (Profile profile : moreUsersSign.getProfiles()) {
                if (shouldSkip(profile, owner) || seenAdditionalProfiles.stream().anyMatch(seen -> isSameProfile(seen, profile))) {
                    continue;
                }
                cleanedProfiles.add(profile);
                seenAdditionalProfiles.add(profile);
            }
            if (cleanedProfiles.isEmpty()) {
                cleanedProfiles.add(plugin.getProfileFactory().fromNameAndUniqueId("", java.util.Optional.empty()));
            }
            if (!sameProfiles(cleanedProfiles, moreUsersSign.getProfiles())) {
                plugin.getSignParser().saveSign(moreUsersSign.withProfiles(cleanedProfiles));
                changed = true;
            }
        }

        for (Profile overflowProfile : privateOverflow) {
            if (seenAdditionalProfiles.stream().noneMatch(seen -> isSameProfile(seen, overflowProfile))) {
                changed |= appendToFirstMoreUsersSign(moreUsersSigns, overflowProfile, plugin);
                seenAdditionalProfiles.add(overflowProfile);
            }
        }

        if (changed) {
            plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        }
        return changed;
    }

    private static boolean appendToFirstMoreUsersSign(List<ProtectionSign> moreUsersSigns, Profile profile,
            BlockLockerPluginImpl plugin) {
        for (ProtectionSign moreUsersSign : moreUsersSigns) {
            ProtectionSign latestSign = plugin.getSignParser().parseSign(moreUsersSign.getLocation().getBlock())
                    .orElse(moreUsersSign);
            List<Profile> profiles = new ArrayList<>(latestSign.getProfiles());
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).getDisplayName().isBlank()) {
                    profiles.set(i, profile);
                    plugin.getSignParser().saveSign(latestSign.withProfiles(profiles));
                    return true;
                }
            }
            if (profiles.size() < MAX_PROFILES_PER_SIGN) {
                profiles.add(profile);
                plugin.getSignParser().saveSign(latestSign.withProfiles(profiles));
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSkip(Profile profile, Optional<Profile> owner) {
        return profile.getDisplayName().isBlank() || owner.filter(value -> isSameProfile(value, profile)).isPresent();
    }

    private static void addUniqueProfile(List<Profile> target, Profile profile) {
        if (target.stream().noneMatch(existing -> isSameProfile(existing, profile))) {
            target.add(profile);
        }
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
}
