package dev.JustRed23.Grandfather.versioning;

import static dev.JustRed23.Grandfather.utils.NumberUtils.getInt;

public class ProgramVersion {

    private final int majorVersion,
            minorVersion,
            patchVersion;

    public ProgramVersion(int majorVersion, int minorVersion, int patchVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.patchVersion = patchVersion;
    }

    public ProgramVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.patchVersion = 0;
    }

    public ProgramVersion(int majorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = 0;
        this.patchVersion = 0;
    }

    public static ProgramVersion fromString(String version) {
        String[] versionParts = version.split("\\.");
        switch (versionParts.length) {
            case 3:
                return new ProgramVersion(getInt(versionParts[0], 1), getInt(versionParts[1], 0), getInt(versionParts[2], 0));
            case 2:
                return new ProgramVersion(getInt(versionParts[0], 1), getInt(versionParts[1], 0));
            case 1:
                return new ProgramVersion(getInt(versionParts[0], 1));
        }

        return new ProgramVersion(1);
    }

    public boolean isHigherThan(ProgramVersion version) {
        if (version == null || this.getMajorVersion() > version.getMajorVersion()) {
            return true;
        } else if (this.getMajorVersion() == version.getMajorVersion()) {
            if (this.getMinorVersion() > version.getMinorVersion()) {
                return true;
            } else if (this.getMinorVersion() == version.getMinorVersion()) {
                if (this.getPatchVersion() > version.getPatchVersion()) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getPatchVersion() {
        return patchVersion;
    }

    public String toString() {
        if (getPatchVersion() == 0)
            return String.format("%s.%s", getMajorVersion(), getMinorVersion());
        return String.format("%s.%s.%s", getMajorVersion(), getMinorVersion(), getPatchVersion());
    }
}