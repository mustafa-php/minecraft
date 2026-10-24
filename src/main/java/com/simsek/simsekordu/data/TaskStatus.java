package com.simsek.simsekordu.data;

/** What a soldier is currently doing (or why it is not working); synced to the client for the info tab. */
public enum TaskStatus {
    NONE("none"),
    IDLE("idle"),
    MINING("mining"),
    NEEDS_PICKAXE("needs_pickaxe"),
    SEARCHING("searching"),
    HUNTING("hunting"),
    COLLECTING("collecting"),
    RETREATING("retreating"),
    TO_STORAGE("to_storage"),
    STORAGE_FULL("storage_full"),
    NO_STORAGE("no_storage"),
    UNREACHABLE("unreachable");

    private final String translationKey;

    TaskStatus(String id) {
        this.translationKey = "status.simsekordu.task." + id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public static TaskStatus byId(int id) {
        TaskStatus[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
