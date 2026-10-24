package com.simsek.simsekordu.data;

public enum Rank {
    ER("er", 0, 1.0D, 1.0D, 1.0D, 1.0D, 14.0F),
    ONBASI("onbasi", 5, 1.1D, 1.1D, 1.02D, 1.15D, 11.0F),
    CAVUS("cavus", 10, 1.25D, 1.2D, 1.04D, 1.3D, 8.0F),
    TEGMEN("tegmen", 20, 1.4D, 1.35D, 1.06D, 1.45D, 5.0F),
    YUZBASI("yuzbasi", 35, 1.6D, 1.5D, 1.08D, 1.6D, 2.0F);

    private final String translationKey;
    private final int minLevel;
    private final double maxHealthMultiplier;
    private final double attackDamageMultiplier;
    private final double movementSpeedMultiplier;
    private final double miningSpeedMultiplier;
    private final float bowInaccuracy;

    Rank(String id, int minLevel, double maxHealthMultiplier, double attackDamageMultiplier, double movementSpeedMultiplier,
         double miningSpeedMultiplier, float bowInaccuracy) {
        this.translationKey = "rank.simsekordu." + id;
        this.minLevel = minLevel;
        this.maxHealthMultiplier = maxHealthMultiplier;
        this.attackDamageMultiplier = attackDamageMultiplier;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        this.miningSpeedMultiplier = miningSpeedMultiplier;
        this.bowInaccuracy = bowInaccuracy;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public int getMinLevel() {
        return this.minLevel;
    }

    public double getMaxHealthMultiplier() {
        return this.maxHealthMultiplier;
    }

    public double getAttackDamageMultiplier() {
        return this.attackDamageMultiplier;
    }

    public double getMovementSpeedMultiplier() {
        return this.movementSpeedMultiplier;
    }

    /** Block breaking speed multiplier used by miners (not an attribute: applied directly by the mining goal). */
    public double getMiningSpeedMultiplier() {
        return this.miningSpeedMultiplier;
    }

    /** Arrow spread (as in {@code Projectile.shoot}): high for privates, low for captains. */
    public float getBowInaccuracy() {
        return this.bowInaccuracy;
    }

    /** How much tighter than a private's the archery spread is, as a percentage (0 for a private). */
    public int getAimBonusPercent() {
        return Math.round((ER.bowInaccuracy - this.bowInaccuracy) / ER.bowInaccuracy * 100.0F);
    }

    public static Rank fromLevel(int level) {
        Rank result = ER;
        for (Rank rank : values()) {
            if (level >= rank.minLevel) {
                result = rank;
            }
        }
        return result;
    }

    public static Rank byId(int id) {
        Rank[] values = values();
        return id >= 0 && id < values.length ? values[id] : ER;
    }
}
