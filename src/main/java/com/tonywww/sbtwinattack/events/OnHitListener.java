package com.tonywww.sbtwinattack.events;

import com.google.common.util.concurrent.AtomicDouble;
import com.tonywww.sbtwinattack.SBTwinAttack;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.util.PlayerAttackHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

import java.util.HashSet;
import java.util.Objects;

import static com.tonywww.sbtwinattack.SBTwinConfig.*;
import static com.tonywww.sbtwinattack.tags.ModTags.Items.TWIN_ATTACK_BLACKLIST;
import static mods.flammpfeil.slashblade.util.PlayerAttackHelper.*;

@Mod.EventBusSubscriber
public class OnHitListener {

    static HashSet<String> blackList = new HashSet<>() {
        {
            add("SweepingDamageRatio");
            add("RankDamageBonus");
            add("SlashBladeDamageScale");
            add("Weapon modifier");
        }
    };

    @SubscribeEvent
    public static void onHitEvent(SlashBladeEvent.HitEvent event) {
        // 在这里处理事件
        LivingEntity target = event.getTarget();
        if (target == null) return;
        if (event.getUser() instanceof ServerPlayer player) {
            ItemStack offhandItem = player.getOffhandItem();

            if (offhandItem.isEmpty() || offhandItem.is(TWIN_ATTACK_BLACKLIST) || player.getCooldowns().isOnCooldown(offhandItem.getItem()))
                return;

            swapHands(player);

            AtomicDouble baseAtkSpeed = new AtomicDouble(4);
            AtomicDouble baseDamage = new AtomicDouble(1);


            offhandItem.getAttributeModifiers(EquipmentSlot.MAINHAND).forEach((k, v) -> {
                if (k == Attributes.ATTACK_SPEED) {
                    if (v.getOperation() == Operation.ADDITION) {
                        baseAtkSpeed.addAndGet(v.getAmount());
                    }
                } else if (k == Attributes.ATTACK_DAMAGE) {
                    if (v.getOperation() == Operation.ADDITION) {
                        baseDamage.addAndGet(v.getAmount());
                    }
                }
            });
            double finalAtkSpeed = getAttributeScale(Objects.requireNonNull(player.getAttribute(Attributes.ATTACK_SPEED)), baseAtkSpeed.get());
            double finalAtkDamage = getAttributeScale(Objects.requireNonNull(player.getAttribute(Attributes.ATTACK_DAMAGE)), baseDamage.get());
            double cd = (18.0D * twinAttackCDConstant.get() / finalAtkSpeed) + 2.0D;

            if (debugInfo.get()) {
                SBTwinAttack.LOGGER.info("attacker uuid: {},\n raw damage: {},\n attack speed: {},\n final cd: {},\n target: {},\n target uuid: {}",
                        player.getStringUUID(),
                        baseDamage,
                        finalAtkSpeed,
                        cd,
                        target,
                        target.getStringUUID());
            }

            // 攻击
            fillPlayerAttackerStrengthTicker(player);
            attack(player, target, offhandItem, finalAtkDamage * twinAttackDamageMultiplier.get());

            swapHands(player);

            player.swinging = false;
            player.swingTime = 0;
            player.swing(InteractionHand.OFF_HAND, true);

            player.getCooldowns().addCooldown(offhandItem.getItem(), (int) Math.max(1, cd * twinAttackCDMultiplier.get()));

            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 1f, 1f);

        }
    }

    public static void attack(Player attacker, LivingEntity target, ItemStack offhandItem, double damage) {
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(attacker, target));
        boolean eventResult = !offhandItem.getItem().onLeftClickEntity(offhandItem, attacker, target);
        if (!checkAttackEventResult.get() || eventResult) {
            if (checkIsAttackable.get() || (target.isAttackable() && !target.skipAttackInteraction(attacker))) {
                damage += getSweepingBonus(attacker);
                damage += getEnchantmentBonus(attacker, target);

                PlayerAttackHelper.FireAspectResult fireAspectResult = handleFireAspect(attacker, target);
                Vec3 originalMotion = target.getDeltaMovement();
                target.invulnerableTime = 0;
                if (debugInfo.get()) {
                    SBTwinAttack.LOGGER.info("Final Damage: {}", damage);
                }
                boolean damageSuccess = target.hurt(attacker.damageSources().playerAttack(attacker), (float) damage);
                if (damageSuccess) {
                    restoreTargetMotionIfNeeded(target, originalMotion);
                    if (consumeDurability.get()) handleEnchantmentsAndDurability(attacker, target);
                    handlePostAttackEffects(attacker, target, fireAspectResult, false);
                } else {
                    handleFailedAttack(attacker, target, fireAspectResult);
                }

            }
        }
    }

    private static void swapHands(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, offHand);
        player.setItemInHand(InteractionHand.OFF_HAND, mainHand);
    }

    private static double getAttributeScale(AttributeInstance attackAttribute, double base) {
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.ADDITION)) {
            if (!blackList.contains(i.getName())) {
                base += i.getAmount();
            }
        }
        double finalAmmount = base;
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.MULTIPLY_BASE)) {
            if (!blackList.contains(i.getName())) {
                finalAmmount += base * i.getAmount();
            }
        }
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.MULTIPLY_TOTAL)) {
            if (!blackList.contains(i.getName())) {
                finalAmmount *= 1.0D + i.getAmount();
            }
        }
        return finalAmmount;
    }

    private static void fillPlayerAttackerStrengthTicker(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("f_20922_");
            field.setAccessible(true);
            field.set(player, 100);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            if (debugInfo.get()) e.printStackTrace();
        } finally {

        }
    }
}