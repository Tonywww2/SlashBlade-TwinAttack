package com.tonywww.sbtwinattack;

import net.minecraftforge.common.ForgeConfigSpec;

public class SBTwinConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec COMMON_CONFIG;

    public static final ForgeConfigSpec.ConfigValue<Double> twinAttackDamageMultiplier;

    public static final ForgeConfigSpec.ConfigValue<Double> twinAttackCDConstant;
    public static final ForgeConfigSpec.ConfigValue<Double> twinAttackCDMultiplier;

    static {
        BUILDER.comment("Config for SlashBlade Twin Attack").push("SlashBlade Twin Attack");

        twinAttackDamageMultiplier = BUILDER.comment("\nThe damage multiplier of the twin attack. Range[0, 512] Default: 1.0")
                .defineInRange("twinAttackDamageMultiplier", 1.0d, 0.0d, 512.0d);

        twinAttackCDConstant = BUILDER.comment("\nThe cooldown constant of the twin attack. Range[0, 512] Default: 1.5")
                .defineInRange("twinAttackCDConstant", 1.5d, 0.0d, 512.0d);
        twinAttackCDMultiplier = BUILDER.comment("\nThe cooldown multiplier of the twin attack. Range[0, 512] Default: 1.0")
                .defineInRange("twinAttackCDMultiplier", 1.0d, 0.0d, 512.0d);

        BUILDER.pop();
        COMMON_CONFIG = BUILDER.build();

    }
}
