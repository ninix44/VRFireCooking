package org.vmstudio.firecooking.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.vmstudio.firecooking.core.client.ExampleAddonClient;
import org.vmstudio.firecooking.core.client.FireCookingLogic;
import org.vmstudio.firecooking.core.common.FireCookingNetworking;
import org.vmstudio.firecooking.core.server.ExampleAddonServer;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {

        ServerPlayNetworking.registerGlobalReceiver(FireCookingNetworking.COOK_ITEM_PACKET, (server, player, handler, buf, responseSender) -> {
            boolean isMainHand = buf.readBoolean();
            server.execute(() -> {
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if (!stack.isEmpty() && FireCookingLogic.COOKING_MAP.containsKey(stack.getItem())) {
                    Item cookedItem = FireCookingLogic.COOKING_MAP.get(stack.getItem());
                    stack.shrink(1);
                    ItemStack cookedStack = new ItemStack(cookedItem);
                    if (stack.isEmpty()) {
                        player.setItemInHand(hand, cookedStack);
                    } else if (!player.getInventory().add(cookedStack)) {
                        player.drop(cookedStack, false);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(FireCookingNetworking.EAT_ITEM_PACKET, (server, player, handler, buf, responseSender) -> {
            boolean isMainHand = buf.readBoolean();
            server.execute(() -> {
                InteractionHand hand = isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player.getItemInHand(hand);

                if (!stack.isEmpty() && stack.getItem().isEdible()) {
                    ItemStack result = stack.finishUsingItem(player.level(), player);
                    player.setItemInHand(hand, result);
                }
            });
        });

        if(ModLoader.get().isDedicatedServer()){
            VisorAPI.registerAddon(
                    new ExampleAddonServer()
            );
        }else{
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );

            FireCookingLogic.bridge = new FireCookingLogic.NetworkBridge() {
                @Override
                public void sendCookEvent(boolean isMainHand) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(isMainHand);
                    ClientPlayNetworking.send(FireCookingNetworking.COOK_ITEM_PACKET, buf);
                }

                @Override
                public void sendEatEvent(boolean isMainHand) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(isMainHand);
                    ClientPlayNetworking.send(FireCookingNetworking.EAT_ITEM_PACKET, buf);
                }
            };

            ClientTickEvents.END_CLIENT_TICK.register(client -> FireCookingLogic.tick());
        }
    }
}
