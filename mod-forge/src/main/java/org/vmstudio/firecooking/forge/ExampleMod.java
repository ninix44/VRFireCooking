package org.vmstudio.firecooking.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.firecooking.core.client.ExampleAddonClient;
import org.vmstudio.firecooking.core.client.FireCookingLogic;
import org.vmstudio.firecooking.core.common.FireCookingNetworking;
import org.vmstudio.firecooking.core.common.VisorExample;
import org.vmstudio.firecooking.core.server.ExampleAddonServer;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;

import java.util.function.Supplier;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {

    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        FireCookingNetworking.COOK_ITEM_PACKET,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public ExampleMod() {
        CHANNEL.registerMessage(0, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);

        if (!ModLoader.get().isDedicatedServer()) {
            FireCookingLogic.bridge = new FireCookingLogic.NetworkBridge() {
                @Override
                public void sendCookEvent(boolean isMainHand) {
                    CHANNEL.sendToServer(new ActionPacket(isMainHand, 0));
                }

                @Override
                public void sendEatEvent(boolean isMainHand) {
                    CHANNEL.sendToServer(new ActionPacket(isMainHand, 1));
                }
            };

            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        }

        if(ModLoader.get().isDedicatedServer()){
            VisorAPI.registerAddon(
                    new ExampleAddonServer()
            );
        }else{
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FireCookingLogic.tick();
        }
    }

    public static class ActionPacket {
        private final boolean isMainHand;
        private final int action;

        public ActionPacket(boolean isMainHand, int action) {
            this.isMainHand = isMainHand;
            this.action = action;
        }

        public static void encode(ActionPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.isMainHand);
            buf.writeInt(msg.action);
        }

        public static ActionPacket decode(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readBoolean(), buf.readInt());
        }

        public static void handle(ActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player == null) return;

                InteractionHand hand = msg.isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if (msg.action == 0) {
                    if (!stack.isEmpty() && FireCookingLogic.COOKING_MAP.containsKey(stack.getItem())) {
                        Item cookedItem = FireCookingLogic.COOKING_MAP.get(stack.getItem());
                        stack.shrink(1);
                        ItemStack cookedStack = new ItemStack(cookedItem);
                        if (stack.isEmpty()) player.setItemInHand(hand, cookedStack);
                        else if (!player.getInventory().add(cookedStack)) player.drop(cookedStack, false);
                    }
                }
                else if (msg.action == 1) {
                    if (!stack.isEmpty() && stack.getItem().isEdible()) {
                        ItemStack result = stack.finishUsingItem(player.level(), player);
                        player.setItemInHand(hand, result);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
