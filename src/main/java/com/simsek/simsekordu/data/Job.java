package com.simsek.simsekordu.data;

public enum Job {
    NONE("none"),
    MINER("miner"),
    HUNTER("hunter"),
    SOLDIER("soldier");

    private final String translationKey;

    Job(String id) {
        this.translationKey = "job.simsekordu." + id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public static Job byId(int id) {
        Job[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    public static Job byName(String name) {
        for (Job job : values()) {
            if (job.name().equals(name)) {
                return job;
            }
        }
        return NONE;
    }
}
