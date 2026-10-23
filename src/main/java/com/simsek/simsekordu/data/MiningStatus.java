package com.simsek.simsekordu.data;

/** What a miner is currently doing (or why it is not working); synced to the client for the info tab. */
public enum MiningStatus {
    NONE("none"),
    IDLE("idle"),
    MINING("mining"),
    NEEDS_PICKAXE("needs_pickaxe"),
    TO_STORAGE("to_storage"),
    STORAGE_FULL("storage_full"),
    NO_STORAGE("no_storage"),
    UNREACHABLE("unreachable");

    private final String translationKey;

    MiningStatus(String id) {
        this.translationKey = "status.simsekordu.mining." + id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public static MiningStatus byId(int id) {
        MiningStatus[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
