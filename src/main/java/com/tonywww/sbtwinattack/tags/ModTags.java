package com.tonywww.sbtwinattack.tags;

import com.tonywww.sbtwinattack.SBTwinAttack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModTags {

    public static class Items {

        public static final TagKey<Item> TWIN_ATTACK_BLACKLIST = createTag("twin_attack_blacklist");

        private static TagKey<Item> createTag(String name) {

            return ItemTags.create(new ResourceLocation(SBTwinAttack.MODID, name));
        }

        private static TagKey<Item> createForgeTag(String name) {

            return ItemTags.create(new ResourceLocation("forge", name));
        }

    }
}
