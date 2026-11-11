package com.ghostchu.quickshop.shop.display.virtual;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.shop.ContainerShop;
import com.ghostchu.quickshop.shop.display.AbstractDisplayItem;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VirtualDisplayRecipientsTest {
  @BeforeAll static void initializeShop() throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }

  @Test void publicSpawnDiscoversRecipientsOnTheirOwnersAndSendsOnce() throws Exception {
    try(var f = new Fixture()) {
      f.display.spawn();
      assertTrue(f.display.isSpawned());
      verify(f.player, never()).getLocation();
      assertEquals(1, f.callbacks.size());
      f.capture();
      assertEquals(Set.of(f.player.getUniqueId()), f.senders);
      verify(f.display, times(1)).sendFakeItem(f.player);
    }
  }

  @Test void removalDiscardsQueuedRecipientDiscovery() throws Exception {
    try(var f = new Fixture()) {
      f.display.spawn();
      f.display.remove(false);
      f.capture();
      assertFalse(f.display.isSpawned());
      assertTrue(f.senders.isEmpty());
      verify(f.display, never()).sendFakeItem(f.player);
    }
  }

  @Test void foreignShopRegionThenPlayerOwnerCompleteTheRecipientDelivery() throws Exception {
    try(var f = new Fixture()) {
      f.display.spawn();
      f.shopOwner.set(false);
      f.capture();
      assertTrue(f.senders.isEmpty());
      assertEquals(1, f.regionCallbacks.size());
      f.shopOwner.set(true);
      f.regionCallbacks.remove(0).accept(null);
      assertEquals(1, f.callbacks.size());
      verify(f.display, never()).sendFakeItem(f.player);
      f.shopOwner.set(false);
      f.capture();
      verify(f.display, times(1)).sendFakeItem(f.player);
    }
  }

  @Test void oldQueuedDestroyDoesNotRemoveARespawnedDisplay() throws Exception {
    try(var f = new Fixture()) {
      f.display.spawn();
      f.capture();
      doNothing().when(f.display).sendDestroyPacket(f.player);
      f.display.remove(false);
      f.display.spawn();
      f.capture();
      verify(f.display, times(2)).sendFakeItem(f.player);
      verify(f.display, never()).sendDestroyPacket(f.player);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    final MockedStatic<QuickShop> quickshop = mockStatic(QuickShop.class);
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final World world = mock(World.class);
    final Player player = mock(Player.class);
    final AtomicReference<Player> owner = new AtomicReference<>();
    final AtomicBoolean shopOwner = new AtomicBoolean(true);
    final List<Consumer<ScheduledTask>> callbacks = new ArrayList<>();
    final List<Consumer<ScheduledTask>> regionCallbacks = new ArrayList<>();
    final Set<UUID> senders = new ConcurrentSkipListSet<>();
    final VirtualDisplayItem<?> display;

    Fixture() throws Exception {
      when(plugin.getJavaPlugin().getName()).thenReturn("QuickShop");
      when(plugin.getJavaPlugin().isEnabled()).thenReturn(true);
      quickshop.when(QuickShop::getInstance).thenReturn(plugin);
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call->List.of(player));
      bukkit.when(Bukkit::getViewDistance).thenReturn(10);
      bukkit.when(()->Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenAnswer(call->shopOwner.get());
      bukkit.when(()->Bukkit.isOwnedByCurrentRegion(player)).thenAnswer(call->owner.get() == player);
      RegionScheduler regionScheduler = mock(RegionScheduler.class);
      bukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);
      doAnswer(call->{regionCallbacks.add(call.getArgument(2));return mock(ScheduledTask.class);})
          .when(regionScheduler).run(any(), any(Location.class), any());
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
      when(world.getName()).thenReturn("world");
      when(player.getUniqueId()).thenReturn(UUID.randomUUID());
      when(player.isOnline()).thenReturn(true);
      when(player.getWorld()).thenAnswer(call->{ assertSame(player, owner.get());return world; });
      when(player.getLocation()).thenAnswer(call->{ assertSame(player, owner.get());return new Location(world, 0, 64, 0); });
      EntityScheduler scheduler = mock(EntityScheduler.class);
      when(player.getScheduler()).thenReturn(scheduler);
      doAnswer(call->{callbacks.add(call.getArgument(1));return mock(ScheduledTask.class);})
          .when(scheduler).run(any(), any(), isNull());
      display = mock(VirtualDisplayItem.class, CALLS_REAL_METHODS);
      ContainerShop shop = mock(ContainerShop.class);
      when(shop.bukkitLocation()).thenReturn(new Location(world, 0, 64, 0));
      when(shop.isLoaded()).thenReturn(true);
      set(AbstractDisplayItem.class, "shop", shop);
      set(AbstractDisplayItem.class, "originalItemStack", mock(ItemStack.class));
      set(VirtualDisplayItem.class, "manager", mock(VirtualDisplayItemManager.class));
      set(VirtualDisplayItem.class, "packetSenders", senders);
      // Mockito skips constructors. Initialize the new lifecycle field when present.
      try { set(VirtualDisplayItem.class, "recipientGeneration", new AtomicLong()); }
      catch(NoSuchFieldException baseline) { }
      doAnswer(call->{assertSame(player, owner.get());return null;}).when(display).sendFakeItem(player);
    }

    void set(Class<?> type, String name, Object value) throws Exception {
      var field = type.getDeclaredField(name);
      field.setAccessible(true);
      field.set(display, value);
    }
    void capture() {
      owner.set(player);
      var ready = List.copyOf(callbacks);
      callbacks.clear();
      try { for(var callback : ready) callback.accept(null); }
      finally { owner.set(null); }
    }
    @Override public void close() { bukkit.close();quickshop.close(); }
  }
}
