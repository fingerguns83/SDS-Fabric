package net.fg83.sdsfabric.mixin;


import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(VillagerEntity.class)
public class VillagerEntityMixin {
    @Inject(method = "afterUsing", at = @At(value = "TAIL"))
    private void afterUsing(CallbackInfo ci) {
        // Casts the current object to VillagerEntity type.
        VillagerEntity villagerEntity = (VillagerEntity) (Object) this;

        // Checks if the AI of the villager is disabled.
        if (villagerEntity.isAiDisabled()) {
            // Checks if the villager can level up using the Invoker interface.
            if (((VillagerEntityInvoker) villagerEntity).canLevelUp()) {
                // Resets level up timer and leveling up status.
                villagerEntity.levelUpTimer = 0;
                villagerEntity.levelingUp = false;

                // Levels up the villager using the Invoker interface.
                ((VillagerEntityInvoker) villagerEntity).levelUp();

                // Adds a regeneration status effect to the villager for 200 ticks with an amplifier of 0.
                villagerEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
            }
        }
    }
}
