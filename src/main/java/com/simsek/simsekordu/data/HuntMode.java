package com.simsek.simsekordu.data;

/** What a hunter goes after. */
public enum HuntMode {
    ANIMAL("animal"),
    MONSTER("monster"),
    BOTH("both");

    private final String translationKey;

    HuntMode(String id) {
        this.translationKey = "hunt_mode.simsekordu." + id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public boolean includesAnimals() {
        return this != MONSTER;
    }

    public boolean includesMonsters() {
        return this != ANIMAL;
    }

    public static HuntMode byId(int id) {
        HuntMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : ANIMAL;
    }

    public static HuntMode byName(String name) {
        for (HuntMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return ANIMAL;
    }
}
