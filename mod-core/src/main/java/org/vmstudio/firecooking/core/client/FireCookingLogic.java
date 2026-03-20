package org.vmstudio.firecooking.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
    }
    public static NetworkBridge bridge;

    private static int mainHandCookTicks = 0;
    private static int offHandCookTicks = 0;

    private static final int TARGET_COOK_TIME = 60;

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

        processHand(mc, InteractionHand.MAIN_HAND, HandType.MAIN, pose.getMainHand(), true);
        processHand(mc, InteractionHand.OFF_HAND, HandType.OFFHAND, pose.getOffhand(), false);
    }

    private static void processHand(Minecraft mc, InteractionHand hand, HandType handType, VRPose pose, boolean isMain) {
        ItemStack stack = mc.player.getItemInHand(hand);

        if (stack.isEmpty()) {
            resetTimers(isMain);
            return;
        }

        Vec3 foodPos = getFoodPos(pose);

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

        resetTimers(isMain);
    }

    private static void resetTimers(boolean isMain) {
        if (isMain) {
            mainHandCookTicks = 0;
        } else {
            offHandCookTicks = 0;
        }
    }

    private static Vec3 getFoodPos(VRPose handPose) {
        Vector3f offset = new Vector3f(0, 0.05f, -0.1f);
        Vector3f tipJoml = handPose.getCustomVector(offset).add(handPose.getPosition());
        return new Vec3(tipJoml.x(), tipJoml.y(), tipJoml.z());
    }
}
