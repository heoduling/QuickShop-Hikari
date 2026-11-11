package com.ghostchu.quickshop.shop.display.virtual;

/*
 * QuickShop-Hikari
 * Copyright (C) 2025 Daniel "creatorfromhell" Vidmar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.event.display.DisplayApplicableCheckEvent;
import com.ghostchu.quickshop.api.event.display.ShopDisplayItemSpawnEvent;
import com.ghostchu.quickshop.api.event.packet.send.PacketHandlerSendDestroyEvent;
import com.ghostchu.quickshop.api.event.packet.send.PacketHandlerSendMetaEvent;
import com.ghostchu.quickshop.api.event.packet.send.PacketHandlerSendSpawnEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.ShopChunk;
import com.ghostchu.quickshop.api.shop.display.DisplayType;
import com.ghostchu.quickshop.api.shop.display.PacketFactory;
import com.ghostchu.quickshop.shop.SimpleShopChunk;
import com.ghostchu.quickshop.shop.display.AbstractDisplayItem;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import com.ghostchu.simplereloadlib.Reloadable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

public class VirtualDisplayItem<T> extends AbstractDisplayItem implements Reloadable {

  private final int entityID;
  private final int textEntityID;
  //The List which store packet sender
  private final Set<UUID> packetSenders = new ConcurrentSkipListSet<>();
  private final PacketFactory<T> packetFactory;

  private final VirtualDisplayItemManager manager;

  private final T spawnPacket;
  private final T metaPacket;
  private final T textSpawnPacket;
  private final T textMetaPacket;
  private final T velocityPacket;
  private final T destroyPacket;
  private final T textDestroyPacket;

  //cache chunk x and z
  private ShopChunk chunkLocation;

  //If packet initialized
  private volatile boolean isSpawned = false;
  private final AtomicLong recipientGeneration = new AtomicLong();
  //packets

  VirtualDisplayItem(final VirtualDisplayItemManager manager, final PacketFactory<T> packetFactory, final Shop shop) {

    super(shop);

    this.entityID = manager.generateEntityId();
    this.textEntityID = manager.generateEntityId();
    this.manager = manager;
    this.manager.shopEntities.put(shop.getShopId(), entityID);
    this.packetFactory = packetFactory;

    if(getDisplayLocation() != null) {

      this.spawnPacket = packetFactory.createSpawnPacket(entityID, getDisplayLocation());
      this.metaPacket = packetFactory.createMetaDataPacket(entityID, checkEnchants(getOriginalItemStack().clone()));

      //text
      this.textSpawnPacket = packetFactory.createTextDisplaySpawnPacket(textEntityID,
                                                                        getDisplayLocation().clone().add(0,
                                                                                                         QuickShop.getInstance().getConfig().getDouble("shop.text-display.y-offset", 0.8), 0));
      this.textMetaPacket = packetFactory.createTextDisplayVisiblePacket(textEntityID, shop, getOriginalItemStack().clone());

      this.velocityPacket = packetFactory.createVelocityPacket(entityID);
      this.destroyPacket = packetFactory.createDestroyPacket(entityID);
      this.textDestroyPacket = packetFactory.createDestroyPacket(textEntityID);

    } else {
      this.spawnPacket = null;
      this.metaPacket = null;
      this.textSpawnPacket = null;
      this.textMetaPacket = null;
      this.velocityPacket = null;
      this.destroyPacket = null;
      this.textDestroyPacket = null;
    }

    load();
  }

  public ItemStack checkEnchants(final ItemStack itemStack) {

    final ItemStack cloned = itemStack.asOne();
    if(cloned.getEnchantments().isEmpty()) return cloned;

    if(!manager.allowEnchants()) {

      cloned.getEnchantments().clear();
      return cloned;
    }
    return (manager.packetHandler() != null)? manager.packetHandler().filterEnchantments(cloned) : cloned;
  }

  @Override
  public boolean checkDisplayIsMoved() {

    return false;
  }

  @Override
  public boolean checkDisplayNeedRegen() {

    return false;
  }

  @Override
  public boolean checkIsShopEntity(@NotNull final Entity entity) {

    return false;
  }

  @Override
  public void fixDisplayMoved() {

  }

  @Override
  public void fixDisplayNeedRegen() {

  }

  @Override
  public @Nullable Entity getDisplay() {

    return null;
  }

  @Override
  public boolean isSpawned() {

    return isSpawned;
  }

  @Override
  public boolean isApplicableForPlayer(final Player player) {

    final DisplayApplicableCheckEvent event = new DisplayApplicableCheckEvent(shop, player.getUniqueId());

    event.setApplicable(true);
    event.callEvent();

    return event.isApplicable();
  }

  @Override
  public void remove(final boolean dontTouchWorld) {

    recipientGeneration.incrementAndGet();
    final Iterator<UUID> iterator = packetSenders.iterator();
    while(iterator.hasNext()) {

      final Player nextPlayer = Bukkit.getPlayer(iterator.next());
      if(nextPlayer == null) {

        iterator.remove();
      } else {

        sendDestroyPacket(nextPlayer);
      }
    }

    if(isSpawned()) {

      unload();
      isSpawned = false;
    }
  }

  @Override
  public boolean removeDupe() {

    return false;
  }

  @Override
  public void respawn() {

    Util.ensureThread(false);
    remove(false);
    spawn();
  }

  @Override
  public void safeGuard(@Nullable final Entity entity) {

  }

  @Override
  public void spawn() {

    Util.ensureThread(false);
    if(isSpawned || !shop.isLoaded()) {
      return;
    }
    if(new ShopDisplayItemSpawnEvent(shop, originalItemStack, DisplayType.VIRTUALITEM).callCancellableEvent()) {
      Log.debug(
              "Canceled the displayItem spawning because a plugin setCancelled the spawning event, usually this is a QuickShop Add on");
      return;
    }
    load();

    // Can't rely on the attachedShop cache to be accurate
    // So just try it and if it fails, no biggie
        /*try {
            shop.getAttachedShop().updateAttachedShop();
        } catch (NullPointerException ignored) {
        }*/

    isSpawned = true;
    final long currentGeneration = recipientGeneration.incrementAndGet();
    for(final Player player : Bukkit.getOnlinePlayers()) {
      onPlayerOwner(player, ()->discoverRecipient(player, currentGeneration));
    }
  }


  //Due to the delay task in ChunkListener
  //We must move load task to first spawn to prevent some bug and make the check lesser
  private void load() {

    Util.ensureThread(false);
    //some time shop can be loaded when world isn't loaded
    chunkLocation = SimpleShopChunk.fromLocation(shop.bukkitLocation());
    manager.put(chunkLocation, entityID, this);
  }

  private void discoverRecipient(final Player player, final long currentGeneration) {

    if(!isSpawned || recipientGeneration.get() != currentGeneration || !player.isOnline()) {
      return;
    }
    final Location location = shop.bukkitLocation();
    if(player.getWorld() != location.getWorld()
       || player.getLocation().distance(location) > Bukkit.getViewDistance() * 16) {
      return;
    }
    final Runnable addRecipient = ()->{
      if(!isSpawned || recipientGeneration.get() != currentGeneration || !shop.isLoaded()
         || !Bukkit.isOwnedByCurrentRegion(shop.bukkitLocation())) {
        return;
      }
      if(packetSenders.add(player.getUniqueId())) {
        onPlayerOwner(player, ()->{
          if(isSpawned && recipientGeneration.get() == currentGeneration && player.isOnline()
             && isApplicableForPlayer(player) && recipientGeneration.get() == currentGeneration) {
            sendFakeItem(player);
          } else {
            forgetRecipient(player.getUniqueId(), currentGeneration);
          }
        });
      }
    };
    if(Bukkit.isOwnedByCurrentRegion(location)) {
      addRecipient.run();
    } else if(QuickShop.getInstance().getJavaPlugin().isEnabled()) {
      Bukkit.getRegionScheduler().run(QuickShop.getInstance().getJavaPlugin(), location, task->addRecipient.run());
    }
  }

  private void onPlayerOwner(final Player player, final Runnable action) {

    if(Bukkit.isOwnedByCurrentRegion(player)) {
      action.run();
    } else if(QuickShop.getInstance().getJavaPlugin().isEnabled()) {
      player.getScheduler().run(QuickShop.getInstance().getJavaPlugin(), task->action.run(), null);
    }
  }

  private void forgetRecipient(final UUID player, final long currentGeneration) {

    final Runnable remove = ()->{
      if(recipientGeneration.get() == currentGeneration) {
        packetSenders.remove(player);
      }
    };
    final Location location = shop.bukkitLocation();
    if(Bukkit.isOwnedByCurrentRegion(location)) {
      remove.run();
    } else if(QuickShop.getInstance().getJavaPlugin().isEnabled()) {
      Bukkit.getRegionScheduler().run(QuickShop.getInstance().getJavaPlugin(), location, task->remove.run());
    }
  }

  private void queuePlayerPacket(final Player player, final Runnable action) {

    // Keep destroys and replacement spawns ordered even when a caller already owns the player.
    if(QuickShop.getInstance().getJavaPlugin().isEnabled()) {
      player.getScheduler().run(QuickShop.getInstance().getJavaPlugin(), task->action.run(), null);
    }
  }

  public void sendSpawnPacket(@NotNull final Player player) {

    final PacketHandlerSendSpawnEvent<T> event = new PacketHandlerSendSpawnEvent<>(manager.packetHandler(),
                                                                                   this.packetFactory,
                                                                                   spawnPacket);
    if(event.callCancellableEvent()) {

      Log.debug("Canceled the sending of the spawn packet: " + shop.getShopId());
    } else {

      this.packetFactory.sendPacket(player, event.spawnPacket());
    }
  }

  public void sendMetaPacket(@NotNull final Player player) {

    final PacketHandlerSendMetaEvent<T> event = new PacketHandlerSendMetaEvent<>(manager.packetHandler(),
                                                                                 this.packetFactory,
                                                                                 metaPacket);
    if(event.callCancellableEvent()) {

      Log.debug("Canceled the sending of the meta packet: " + shop.getShopId());
    } else {

      this.packetFactory.sendPacket(player, event.metaPacket());
    }
  }
  //TODO: Events
  public void sendTextSpawnPacket(@NotNull final Player player) {
    this.packetFactory.sendPacket(player, textSpawnPacket);
  }

  public void sendTextMetaPacket(@NotNull final Player player) {
    this.packetFactory.sendPacket(player, textMetaPacket);
  }

  public void sendDestroyPacket(@NotNull final Player player) {

    queuePlayerPacket(player, ()->sendDestroyPacketOwned(player));
  }

  private void sendDestroyPacketOwned(final Player player) {

    final PacketHandlerSendDestroyEvent<T> event = new PacketHandlerSendDestroyEvent<>(manager.packetHandler(),
                                                                                       this.packetFactory,
                                                                                       destroyPacket);
    if(event.callCancellableEvent()) {

      Log.debug("Canceled the sending of the destroy packet: " + shop.getShopId());
    } else {

      this.packetFactory.sendPacket(player, event.destroyPacket());
      this.packetFactory.sendPacket(player, textDestroyPacket);
    }
  }

  public void sendFakeItem(@NotNull final Player player) {

    final long currentGeneration = recipientGeneration.get();
    queuePlayerPacket(player, ()->{
      if(!isSpawned || recipientGeneration.get() != currentGeneration || !player.isOnline()) {
        return;
      }
      final Location location = shop.bukkitLocation();
      if(player.getWorld() != location.getWorld()
         || player.getLocation().distance(location) > Bukkit.getViewDistance() * 16) {
        forgetRecipient(player.getUniqueId(), currentGeneration);
        return;
      }
      sendFakeItemOwned(player);
    });
  }

  private void sendFakeItemOwned(final Player player) {

    this.sendDestroyPacketOwned(player);
    this.sendSpawnPacket(player);
    this.sendMetaPacket(player);

    if(QuickShop.getInstance().getConfig().getBoolean("shop.text-display.enabled")) {

      this.sendTextSpawnPacket(player);
      this.sendTextMetaPacket(player);
    }

    if(velocityPacket != null) {

      this.packetFactory.sendPacket(player, velocityPacket);
    }
  }

  public void sendFakeItemToAll() {

    final Iterator<UUID> iterator = packetSenders.iterator();
    while(iterator.hasNext()) {

      final Player nextPlayer = Bukkit.getPlayer(iterator.next());
      if(nextPlayer == null) {

        iterator.remove();
      } else {

        sendFakeItem(nextPlayer);
      }
    }
  }

  private void unload() {

    packetSenders.clear();
    manager.remove(chunkLocation, entityID, this);
  }

  @NotNull
  public Set<UUID> getPacketSenders() {

    return packetSenders;
  }
}
