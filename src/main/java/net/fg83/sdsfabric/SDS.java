package net.fg83.sdsfabric;

import me.lucko.fabric.api.permissions.v0.Permissions;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.fg83.sdsfabric.mixin.PiglinEntityInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.item.NameTagItem;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class SDS implements ModInitializer {

    SDSConfig config; // Configuration instance

    List<VillagerEntity> noAiVillagers = new ArrayList<>();
    List<PiglinEntity> noAiPiglins = new ArrayList<>();

    int piglinTickInterval = 20;
    int restockInterval = 12000;

    @Override
    public void onInitialize() {
        ConfigManager.loadConfig(); // Load configuration
        config = ConfigManager.getConfig(); // Retrieve configuration

        // Configure restock interval based on settings
        if (config.NoAiVillagersTrade) {
            if (config.WorkingHoursOnly) {
                restockInterval = Math.round((float) (7000 / config.DailyRestockCount));
            } else if (config.DaytimeHoursOnly) {
                restockInterval = Math.round((float) (12000 / config.DailyRestockCount));
            } else {
                restockInterval = Math.round((float) (24000 / config.DailyRestockCount));
            }
        }

        // Register callback for entity interaction event
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hitResult == null) {
                return ActionResult.PASS; // Pass if no hit result
            }
            if (player instanceof ServerPlayerEntity serverPlayer) {
                return attemptScoop(entity, serverPlayer.getStackInHand(hand), serverPlayer);
            }
            return ActionResult.PASS;

        });

        AtomicInteger tickCounter = new AtomicInteger(0);

        // Register server tick event listener
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            long tick = (long) tickCounter.incrementAndGet();

            // Restock villagers at the specified interval
            if (tick == restockInterval) {
                tickCounter.set(0); // Reset the tick counter

                if (config.ConsoleShowsRestocking) {
                    System.out.println("[SDS] Restocking villagers...");
                }

                // Run restocking asynchronously
                CompletableFuture.runAsync(() -> {
                    server.getWorlds().forEach(world -> {
                        world.getEntitiesByType(EntityType.VILLAGER, entity -> true).forEach(villager -> {
                            if (villager.isAiDisabled()) {
                                noAiVillagers.add(villager); // Add if AI is disabled
                            } else {
                                if (!config.NoAiOnly) {
                                    noAiVillagers.add(villager); // Add if config allows non-AI villagers
                                }
                            }
                        });
                    });
                }).thenRun(() -> {
                    // Safely modify entities on the main thread
                    server.execute(() -> {
                        noAiVillagers.forEach(VillagerEntity::restock); // Restock all villagers
                        noAiVillagers.clear(); // Clear the list after restocking
                    });
                });
            }

            // Handle Piglin interactions every specified tick interval
            if (tick % piglinTickInterval == 0) {
                // Run Piglin checks asynchronously
                CompletableFuture.runAsync(() -> {
                    server.getWorlds().forEach(world -> {
                        world.getEntitiesByType(EntityType.PIGLIN, entity -> true).forEach(piglin -> {
                            if (piglin.isAiDisabled()) {
                                if (piglin.getWorld().getDimension().piglinSafe() || config.AllowNonNetherBarter) {
                                    noAiPiglins.add(piglin); // Add if AI is disabled and conditions are met
                                }
                            }
                        });
                    });
                }).thenRun(() -> {
                    // Safely modify entities on the main thread
                    server.execute(() -> {
                        if (config.NoAiPiglinsBarter) {
                            noAiPiglins.forEach(piglin -> {
                                ((PiglinEntityInvoker) piglin).doMobTick(); // Invoke mobTick on each Piglin
                            });
                        }
                    });
                });
            }
        });

    }

    /**
     * Attempts to modify the AI state of an entity based on a provided name tag.
     *
     * @param entity   The entity to be modified.
     * @param usedItem The item stack used to modify the entity's state.
     * @param player   The player attempting the modification.
     * @return The result of the action, indicating success or failure.
     */
    public ActionResult attemptScoop(Entity entity, ItemStack usedItem, ServerPlayerEntity player) {
        if (usedItem.getItem() instanceof NameTagItem) {
            boolean tagType;

            // Determine if the name tag is "nobrains" or "givebrains"
            if (usedItem.getName().getString().equals("nobrains")) {
                tagType = true; // Disable AI
            } else if (usedItem.getName().getString().equals("givebrains")) {
                tagType = false; // Enable AI
            } else {
                return ActionResult.PASS; // Pass if neither
            }

            // Handle interaction with players
            if (entity.getType().equals(EntityType.PLAYER)) {
                if (tagType) {
                    sendResponse(player, ">> That is a human being, you absolute monster.", true);
                } else {
                    sendResponse(player, ">> Are you trying to say they don't have a brain? Wow. Rude.", true);
                }
                return ActionResult.PASS;
            }
            // Handle interaction with mob entities
            else if (entity instanceof MobEntity mob) {
                boolean isVillager = entity instanceof VillagerEntity;
                boolean isPiglin = entity instanceof PiglinEntity;

                String permissionNode = "sds.";
                if (isVillager) {
                    permissionNode += "villager.";
                } else if (isPiglin) {
                    permissionNode += "piglin.";
                }

                permissionNode += (tagType ? "nobrains" : "givebrains");

                if (tagType) { // Tag type is "nobrains"
                    if (mob.isAiDisabled()) {
                        sendResponse(player, ">> There's nothing but air in here already!", true);
                        return ActionResult.FAIL;
                    } else {
                        if (Permissions.check(player, permissionNode) || Permissions.check(player, "sds.nobrains")) {
                            if (isPiglin && (!mob.getWorld().getDimension().piglinSafe() && !config.AllowNonNether)) {
                                sendResponse(player, ">> You cannot save this piglin's life. I'm sorry for your loss, doctor.", true);
                                return ActionResult.FAIL;
                            }
                            mob.setAiDisabled(true); // Disable AI
                            sendResponse(player, ">> Lobotomy successful! Head empty, no thoughts.", false);
                            return ActionResult.SUCCESS;
                        } else {
                            sendResponse(player, ">> You don't have permission to do that, \"Doctor\".", true);
                            return ActionResult.FAIL;
                        }
                    }
                } else { // Tag type is "givebrains"
                    if (mob.isAiDisabled()) {
                        if (Permissions.check(player, permissionNode) || Permissions.check(player, "sds.givebrains")) {
                            if (isPiglin && (!mob.getWorld().getDimension().piglinSafe() && !config.AllowNonNether)) {
                                sendResponse(player, ">> This Piglin was saved by divine intervention. You cannot do this.", true);
                                return ActionResult.FAIL;
                            }
                            mob.setAiDisabled(false); // Enable AI
                            sendResponse(player, ">> Lobotomy reversed! Hopefully there are no lasting effects.", false);
                            return ActionResult.SUCCESS;
                        } else {
                            sendResponse(player, ">> You don't have permission to do that, \"Doctor\".", true);
                            return ActionResult.FAIL;
                        }
                    } else {
                        sendResponse(player, ">> Slow down there, sport! There's already a brain in here!", true);
                        return ActionResult.FAIL;
                    }
                }
            } else {
                sendResponse(player, ">> That's not gonna.....just why?", true);
                return ActionResult.PASS;
            }
        } else {
            return ActionResult.PASS;
        }
    }

    /**
     * Sends a response message to the player.
     *
     * @param player  the player to send the message to
     * @param message the message content
     * @param isError true if it's an error message, false otherwise
     */
    public void sendResponse(PlayerEntity player, String message, boolean isError) {
        MutableText content;
        if (isError) {
            content = Text.literal(message).formatted(Formatting.RED); // Error message
        } else {
            content = Text.literal(message).formatted(Formatting.GREEN); // Success message
        }
        player.sendMessage(content, false); // Send message to player
    }
}
