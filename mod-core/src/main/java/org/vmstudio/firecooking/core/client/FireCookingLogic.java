package org.vmstudio.firecooking.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;

import java.util.Map;

public class FireCookingLogic {

    public interface NetworkBridge {
        void sendCookEvent(boolean isMainHand);
        void sendEatEvent(boolean isMainHand);
    }
    public static NetworkBridge bridge;

    private static int mainHandCookTicks = 0;
    private static int offHandCookTicks = 0;

    private static int mainHandEatTicks = 0;
    private static int offHandEatTicks = 0;

    private static int mainHandCooldown = 0;
    private static int offHandCooldown = 0;

    private static final int TARGET_COOK_TIME = 60;
    private static final int TARGET_EAT_TIME = 60;
    private static final double EAT_DISTANCE = 0.20;

    public static final Map<Item, Item> COOKING_MAP = Map.of(
        Items.BEEF, Items.COOKED_BEEF,
        Items.PORKCHOP, Items.COOKED_PORKCHOP,
        Items.CHICKEN, Items.COOKED_CHICKEN,
        Items.COD, Items.COOKED_COD,
        Items.SALMON, Items.COOKED_SALMON,
        Items.MUTTON, Items.COOKED_MUTTON,
        Items.RABBIT, Items.COOKED_RABBIT,
        Items.POTATO, Items.BAKED_POTATO,
        Items.KELP, Items.DRIED_KELP
    );

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) return;

        PlayerPoseClient pose = vrPlayer.getPoseData(PlayerPoseType.TICK);

        if (mainHandCooldown > 0) mainHandCooldown--;
        if (offHandCooldown > 0) offHandCooldown--;

        Vec3 headPos = jomlToVec3(pose.getHmd().getPosition());
        Vec3 mouthPos = headPos.subtract(0, 0.15, 0); // testing (maybe change)

        processHand(mc, InteractionHand.MAIN_HAND, HandType.MAIN, pose.getMainHand(), mouthPos, true);
        processHand(mc, InteractionHand.OFF_HAND, HandType.OFFHAND, pose.getOffhand(), mouthPos, false);
    }

    private static void processHand(Minecraft mc, InteractionHand hand, HandType handType, VRPose pose, Vec3 mouthPos, boolean isMain) {
        ItemStack stack = mc.player.getItemInHand(hand);

        if (stack.isEmpty()) {
            resetTimers(isMain);
            return;
        }

        Vec3 foodPos = getFoodPos(pose);

        //todo: particles during eating should be located "ONLY" above the food!!! (fix!)

        if (COOKING_MAP.containsKey(stack.getItem())) {
            BlockPos blockPos = BlockPos.containing(foodPos.x, foodPos.y, foodPos.z);
            BlockState state = mc.level.getBlockState(blockPos);

            if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                int cookTicks = isMain ? ++mainHandCookTicks : ++offHandCookTicks;

                if (cookTicks % 5 == 0) {
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        foodPos.x + (mc.level.random.nextDouble() - 0.5) * 0.15,
                        foodPos.y + 0.1,
                        foodPos.z + (mc.level.random.nextDouble() - 0.5) * 0.15,
                        0, 0.05, 0);
                }

                if (cookTicks % 10 == 0) {
                    mc.player.playSound(SoundEvents.CAMPFIRE_CRACKLE, 0.5f, 1.0f + (mc.level.random.nextFloat() * 0.5f));
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 120f, 0.2f, 0.05f);
                }

                if (cookTicks >= TARGET_COOK_TIME) {
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 300f, 1.0f, 0.2f);
                    mc.player.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5f, 1.5f);

                    for (int i = 0; i < 10; i++) {
                        mc.level.addParticle(ParticleTypes.SMOKE,
                            foodPos.x + (mc.level.random.nextDouble() - 0.5) * 0.2,
                            foodPos.y + 0.1,
                            foodPos.z + (mc.level.random.nextDouble() - 0.5) * 0.2,
                            0, 0.1, 0);
                    }

                    if (bridge != null) bridge.sendCookEvent(isMain);
                    resetTimers(isMain);
                }
                return;
            }
        }

        if (stack.getItem().isEdible() && mc.player.canEat(stack.getItem().getFoodProperties() != null && stack.getItem().getFoodProperties().canAlwaysEat())) {
            double distToMouth = foodPos.distanceTo(mouthPos);

            if (distToMouth < EAT_DISTANCE) {
                if (isMain && mainHandCooldown > 0) return;
                if (!isMain && offHandCooldown > 0) return;

                int eatTicks = isMain ? ++mainHandEatTicks : ++offHandEatTicks;

                if (eatTicks == 20 || eatTicks == 40) {
                    mc.player.playSound(SoundEvents.GENERIC_EAT, 0.4f, 1.0f + (mc.level.random.nextFloat() * 0.2f));
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 100f, 0.2f, 0.05f);

                    for (int i = 0; i < 4; i++) {
                        mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack),
                            foodPos.x + (mc.level.random.nextDouble() - 0.5) * 0.1,
                            foodPos.y + 0.05,
                            foodPos.z + (mc.level.random.nextDouble() - 0.5) * 0.1,
                            0, -0.05, 0);
                    }
                }

                if (eatTicks >= TARGET_EAT_TIME) {
                    mc.player.playSound(SoundEvents.PLAYER_BURP, 0.6f, 1.0f);
                    VisorAPI.client().getInputManager().triggerHapticPulse(handType, 300f, 1.0f, 0.2f);

                    if (bridge != null) bridge.sendEatEvent(isMain);
                    resetTimers(isMain);

                    if (isMain) mainHandCooldown = 40;
                    else offHandCooldown = 40;
                }
                return;
            }
        }

        resetTimers(isMain);
    }

    private static void resetTimers(boolean isMain) {
        if (isMain) {
            mainHandCookTicks = 0;
            mainHandEatTicks = 0;
        } else {
            offHandCookTicks = 0;
            offHandEatTicks = 0;
        }
    }

    private static Vec3 getFoodPos(VRPose handPose) {
        Vector3f offset = new Vector3f(0, 0.05f, -0.1f);
        Vector3f tipJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        return new Vec3(tipJoml.x(), tipJoml.y(), tipJoml.z());
    }

    private static Vec3 jomlToVec3(Vector3fc vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }
}
