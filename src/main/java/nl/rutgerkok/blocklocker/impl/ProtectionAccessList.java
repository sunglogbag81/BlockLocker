package nl.rutgerkok.blocklocker.impl;

import nl.rutgerkok.blocklocker.ProtectionSign;
import nl.rutgerkok.blocklocker.SignType;
import nl.rutgerkok.blocklocker.profile.Profile;
import nl.rutgerkok.blocklocker.protection.Protection;

import java.util.ArrayList;
import java.util.Collection;
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
                if (profile.getDisplayName().isBlank() || owner.filter(value -> isSameProfile(value, profile)).isPresent()) {
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
        List<Profile> profilesForMoreUsers = new ArrayList<>();
        for (ProtectionSign moreUsersSign : moreUsersSigns) {
            addAllUniqueNonBlank(profilesForMoreUsers, moreUsersSign.getProfiles(), owner);
        }

        Optional<ProtectionSign> privateSign = getPrivateSign(protection);
        if (privateSign.isPresent()) {
            List<Profile> privateProfiles = new ArrayList<>(privateSign.get().getProfiles());
            if (privateProfiles.size() > VISIBLE_PROFILE_LINES) {
                addAllUniqueNonBlank(profilesForMoreUsers,
                        privateProfiles.subList(VISIBLE_PROFILE_LINES, privateProfiles.size()), owner);
                privateProfiles = new ArrayList<>(privateProfiles.subList(0, VISIBLE_PROFILE_LINES));
                plugin.getSignParser().saveSign(privateSign.get().withProfiles(privateProfiles));
                changed = true;
            }
        }

        int index = 0;
        for (ProtectionSign moreUsersSign : moreUsersSigns) {
            List<Profile> signProfiles = new ArrayList<>();
            while (index < profilesForMoreUsers.size() && signProfiles.size() < MAX_PROFILES_PER_SIGN) {
                signProfiles.add(profilesForMoreUsers.get(index));
                index++;
            }
            if (signProfiles.isEmpty()) {
                signProfiles.add(plugin.getProfileFactory().fromNameAndUniqueId("", java.util.Optional.empty()));
            }
            if (!sameProfiles(signProfiles, moreUsersSign.getProfiles())) {
                plugin.getSignParser().saveSign(moreUsersSign.withProfiles(signProfiles));
                changed = true;
            }
        }
        if (changed) {
            plugin.getProtectionCache().invalidate(protection.getSomeProtectedBlock());
        }
        return changed;
    }

    private static void addAllUniqueNonBlank(List<Profile> target, Collection<Profile> profiles, Optional<Profile> owner) {
        for (Profile profile : profiles) {
            if (profile.getDisplayName().isBlank() || owner.filter(value -> isSameProfile(value, profile)).isPresent()) {
                continue;
            }
            addUniqueProfile(target, profile);
        }
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
