package com.tonywww.sbtwinattack.events;

import com.google.common.util.concurrent.AtomicDouble;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

import java.util.HashSet;

import static com.tonywww.sbtwinattack.SBTwinConfig.*;

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
        if (event.getUser() instanceof ServerPlayer player) {
            ItemStack stack = player.getOffhandItem();
            ItemStack slashblade = player.getMainHandItem();

            if (stack != null && !stack.isEmpty() &&
                    !(stack.getItem() instanceof ItemSlashBlade) &&
                    !player.getCooldowns().isOnCooldown(stack.getItem())) {
                // 获取伤害
                AtomicDouble damage = new AtomicDouble(1);
                AtomicDouble atkSpeed = new AtomicDouble(4);

                stack.getAttributeModifiers(EquipmentSlot.MAINHAND).forEach((k, v) -> {
                    if (k == Attributes.ATTACK_DAMAGE) {
                        if (v.getOperation() == Operation.ADDITION) {
                            damage.addAndGet(v.getAmount());
                        }
                    } else if (k == Attributes.ATTACK_SPEED) {
                        if (v.getOperation() == Operation.ADDITION) {
                            atkSpeed.addAndGet(v.getAmount());
                        }
                    }
                });

                double finalDamage = getAttributeScale(player.getAttribute(Attributes.ATTACK_DAMAGE), damage.get());
                double finalAtkSpeed = getAttributeScale(player.getAttribute(Attributes.ATTACK_SPEED), atkSpeed.get());

                finalAtkSpeed = (18.0D * twinAttackCDConstant.get() / finalAtkSpeed) + 2.0D;

//                SBTwinAttack.LOGGER.debug("d1: " + damage.get());
//                SBTwinAttack.LOGGER.debug("d2: " + finalDamage);
//                SBTwinAttack.LOGGER.debug("speed: " + atkSpeed.get());
//                SBTwinAttack.LOGGER.debug("cd: " + finalAtkSpeed);

                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), (float) (finalDamage * twinAttackDamageMultiplier.get()));

                fillPlayerAttackerStrengthTicker(player);
                player.swinging = false;
                player.swing(InteractionHand.OFF_HAND, true);

                // 特效
                stack.getItem().onLeftClickEntity(stack, player, target);
                player.getCooldowns().addCooldown(stack.getItem(), (int) Math.max(2, finalAtkSpeed * twinAttackCDMultiplier.get()));

                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 1f, 1f);

            }
        }
    }

    private static double getAttributeScale(AttributeInstance attackAttribute, double base) {
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.ADDITION)) {
            if (!blackList.contains(i.getName())) {
                base += i.getAmount();
            }
        }
        double finalDamage = base;
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.MULTIPLY_BASE)) {
            if (!blackList.contains(i.getName())) {
                finalDamage += base * i.getAmount();
            }
        }
        for (AttributeModifier i : attackAttribute.getModifiers(Operation.MULTIPLY_TOTAL)) {
            if (!blackList.contains(i.getName())) {
                finalDamage *= 1.0D + i.getAmount();
            }
        }
        return finalDamage;
    }

    private static void fillPlayerAttackerStrengthTicker(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.set(player, 100);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}