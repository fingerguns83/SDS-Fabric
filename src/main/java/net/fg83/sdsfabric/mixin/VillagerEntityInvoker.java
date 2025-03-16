package net.fg83.sdsfabric.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin (VillagerEntity.class)
public interface VillagerEntityInvoker {
    @Invoker("levelUp")
    public abstract void levelUp();
    @Invoker("canLevelUp")
    public abstract boolean canLevelUp();
}
