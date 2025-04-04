package net.fg83.sdsfabric.mixin;

import net.minecraft.entity.mob.PiglinEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PiglinEntity.class)
public interface PiglinEntityInvoker {
    @Invoker("mobTick")
    public abstract void doMobTick();
}
