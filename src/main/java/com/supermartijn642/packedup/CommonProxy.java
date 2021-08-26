package com.supermartijn642.packedup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmllegacy.network.NetworkHooks;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created 2/7/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonProxy {

    public static void openBackpackInventory(ItemStack stack, Player player, int bagSlot){
        BackpackType type = ((BackpackItem)stack.getItem()).type;
        CompoundTag compound = stack.getOrCreateTag();
        if(!compound.contains("packedup:invIndex") || BackpackStorageManager.getInventory(compound.getInt("packedup:invIndex")) == null){
            compound.putInt("packedup:invIndex", BackpackStorageManager.createInventoryIndex(type));
            stack.setTag(compound);
        }else
            BackpackStorageManager.getInventory(compound.getInt("packedup:invIndex")).adjustSize(type);
        int inventoryIndex = compound.getInt("packedup:invIndex");
        BackpackInventory inventory = BackpackStorageManager.getInventory(inventoryIndex);
        NetworkHooks.openGui((ServerPlayer)player, new BackpackItem.ContainerProvider(stack.getHoverName(), bagSlot, inventoryIndex, inventory, type), a -> {
            a.writeInt(bagSlot);
            a.writeInt(inventoryIndex);
            a.writeInt(type.ordinal());
            a.writeInt(inventory.bagsInThisBag.size());
            inventory.bagsInThisBag.forEach(a::writeInt);
            a.writeInt(inventory.bagsThisBagIsIn.size());
            inventory.bagsThisBagIsIn.forEach(a::writeInt);
            a.writeInt(inventory.layer);
        });
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDropsEvent e){
        if(e.getEntity() instanceof Player && PUConfig.keepBackpacksOnDeath.get() && !e.isCanceled()){
            List<ItemEntity> stacksToBeSaved = e.getDrops().stream()
                .filter(itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty() && itemEntity.getItem().getItem() instanceof BackpackItem)
                .collect(Collectors.toList());

            if(!stacksToBeSaved.isEmpty()){
                stacksToBeSaved.forEach(e.getDrops()::remove);

                ListTag itemData = new ListTag();
                stacksToBeSaved.stream().map(ItemEntity::getItem)
                    .forEach(stack -> itemData.add(stack.serializeNBT()));

                e.getEntity().getPersistentData().put("packedup:backpacks", itemData);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e){
        if(e.getOriginal().getPersistentData().contains("packedup:backpacks", Constants.NBT.TAG_LIST)){
            ListTag itemData = e.getOriginal().getPersistentData().getList("packedup:backpacks", Constants.NBT.TAG_COMPOUND);
            itemData.stream()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .map(ItemStack::of)
                .forEach(stack -> e.getPlayer().getInventory().placeItemBackInInventory(stack));
        }
    }
}
