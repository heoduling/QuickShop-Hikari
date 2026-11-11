package com.ghostchu.quickshop.watcher;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.shop.ContainerShop;
import com.ghostchu.quickshop.shop.display.AbstractDisplayItem;
import com.ghostchu.quickshop.shop.display.virtual.VirtualDisplayItem;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisplayAutoDespawnWatcherTest {
  @BeforeAll static void initializeShop() throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }

  @Test void readsEachPlayerOnceOnItsOwnerAndUpdatesDisplaysOnShopRegions() {
    try(var f = new Fixture()) {
      var near = f.shop(f.world, 3, false);
      var far = f.shop(f.world, 100, true);
      var otherWorld = f.shop(mock(World.class), 0, true);
      f.watcher.run();
      assertEquals(2, f.captures.size());
      assertTrue(f.async.isEmpty());
      f.capture(f.players.get(0));
      assertTrue(f.async.isEmpty());
      f.capture(f.players.get(1));
      f.scan();
      assertEquals(3, f.regions.size());
      f.apply();
      verify(near).spawn();
      verify(far).remove(false);
      verify(otherWorld).remove(false);
      for(Player player : f.players) verify(player, times(1)).getLocation();
    }
  }

  @Test void pendingPlayerCaptureBoundsOverlappingSweepsAndStopDiscardsLateCallbacks() {
    try(var f = new Fixture()) {
      f.shop(f.world, 0, false);
      f.watcher.run();
      f.watcher.run();
      assertEquals(2, f.captures.size());
      f.watcher.stop();
      f.capture(f.players.get(0));
      f.capture(f.players.get(1));
      assertTrue(f.async.isEmpty());
      assertTrue(f.regions.isEmpty());
    }
  }

  @Test void queuedShopWorkUsesLatestSweepAndDoesNotGrowWithEverySweep() {
    try(var f = new Fixture()) {
      var display = f.shop(f.world, 0, true);
      f.watcher.run();
      f.captureAll();
      f.scan();
      assertEquals(1, f.regions.size());
      for(Player player : f.players) f.locations.put(player, new Location(f.world, 200, 64, 0));
      f.watcher.run();
      f.captureAll();
      f.scan();
      assertEquals(1, f.regions.size());
      f.apply();
      verify(display).remove(false);
      verify(display, never()).spawn();
    }
  }

  @Test void retiredPlayersAndRejectedSchedulersDoNotLeaveSweepPending() {
    try(var f = new Fixture()) {
      var display = f.shop(f.world, 0, true);
      f.watcher.run();
      for(Runnable retired : List.copyOf(f.retirements)) retired.run();
      f.captures.clear();
      f.scan();
      f.apply();
      verify(display).remove(false);
      f.acceptCaptures = false;
      f.watcher.run();
      assertEquals(1, f.async.size());
      f.scan();
      f.apply();
    }
  }

  @Test void unloadAndStopInvalidateQueuedDisplayChanges() {
    try(var f = new Fixture()) {
      var display = f.shop(f.world, 0, false);
      f.watcher.run();
      f.captureAll();
      f.scan();
      when(f.shops.iterator().next().isLoaded()).thenReturn(false);
      f.apply();
      verify(display, never()).spawn();
      when(f.shops.iterator().next().isLoaded()).thenReturn(true);
      f.watcher.run();
      f.captureAll();
      f.scan();
      f.watcher.stop();
      f.apply();
      verify(display, never()).spawn();
    }
  }

  @Test void oldGenerationCannotConsumeTheRestartedWatchersQueuedUpdate() {
    try(var f = new Fixture()) {
      var display = f.shop(f.world, 0, false);
      f.watcher.run();
      f.captureAll();
      f.scan();
      f.watcher.stop();
      f.watcher.start(0, 40);
      f.watcher.run();
      f.captureAll();
      f.scan();
      assertEquals(2, f.regions.size());
      f.apply();
      verify(display, times(1)).spawn();
    }
  }

  @Test void unchangedVirtualDisplaysDoNotQueueRegionWork() {
    try(var f = new Fixture()) {
      var display = f.shop(f.world, 0, true, true);
      f.watcher.run();
      f.captureAll();
      f.scan();
      assertTrue(f.regions.isEmpty());
      when(display.isSpawned()).thenReturn(false);
      f.watcher.run();
      f.captureAll();
      f.scan();
      assertEquals(1, f.regions.size());
      f.apply();
      verify(display).spawn();
    }
  }

  private static final class Fixture implements AutoCloseable {
    final QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    final World world = mock(World.class);
    final List<Player> players = List.of(mock(Player.class), mock(Player.class));
    final Set<Shop> shops = new LinkedHashSet<>();
    final Map<Player, Location> locations = new HashMap<>();
    final Map<Player, Consumer<ScheduledTask>> captures = new LinkedHashMap<>();
    final List<Runnable> retirements = new ArrayList<>();
    final List<Consumer<ScheduledTask>> async = new ArrayList<>();
    final List<Consumer<ScheduledTask>> regions = new ArrayList<>();
    final AtomicReference<Player> owner = new AtomicReference<>();
    final AtomicBoolean shopOwner = new AtomicBoolean();
    final MockedStatic<QuickShop> quickshop = mockStatic(QuickShop.class);
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final DisplayAutoDespawnWatcher watcher;
    boolean acceptCaptures = true;

    Fixture() {
      when(plugin.getJavaPlugin().getName()).thenReturn("QuickShop");
      when(plugin.getJavaPlugin().isEnabled()).thenReturn(true);
      when(plugin.getShopManager().getLoadedShops()).thenReturn(shops);
      quickshop.when(QuickShop::getInstance).thenReturn(plugin);
      FoliaLib folia = mock(FoliaLib.class);
      when(folia.getScheduler()).thenReturn(mock(PlatformScheduler.class));
      quickshop.when(QuickShop::folia).thenReturn(folia);
      bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call->players);
      bukkit.when(()->Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenAnswer(call->shopOwner.get());
      AsyncScheduler scheduler = mock(AsyncScheduler.class);
      bukkit.when(Bukkit::getAsyncScheduler).thenReturn(scheduler);
      doAnswer(call->{ async.add(call.getArgument(1));return mock(ScheduledTask.class); })
          .when(scheduler).runNow(any(), any());
      RegionScheduler regionScheduler = mock(RegionScheduler.class);
      bukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);
      doAnswer(call->{ regions.add(call.getArgument(2));return mock(ScheduledTask.class); })
          .when(regionScheduler).run(any(), any(Location.class), any());
      for(Player player : players) {
        locations.put(player, new Location(world, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenAnswer(call->{ assertSame(player, owner.get());return world; });
        when(player.getLocation()).thenAnswer(call->{ assertSame(player, owner.get());return locations.get(player).clone(); });
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        doAnswer(call->{
          if(!acceptCaptures) return null;
          captures.put(player, call.getArgument(1));
          retirements.add(call.getArgument(2));
          return mock(ScheduledTask.class);
        }).when(entityScheduler).run(any(), any(), any());
      }
      watcher = new DisplayAutoDespawnWatcher(plugin, 20);
      watcher.start(0, 40);
    }

    AbstractDisplayItem shop(World world, int x, boolean spawned) {
      return shop(world, x, spawned, false);
    }

    AbstractDisplayItem shop(World world, int x, boolean spawned, boolean virtual) {
      ContainerShop shop = mock(ContainerShop.class);
      when(shop.bukkitLocation()).thenReturn(new Location(world, x, 64, 0));
      when(shop.isLoaded()).thenReturn(true);
      AbstractDisplayItem display = virtual? mock(VirtualDisplayItem.class) : mock(AbstractDisplayItem.class);
      when(shop.getDisplayItem()).thenReturn(display);
      AtomicBoolean state = new AtomicBoolean(spawned);
      when(display.isSpawned()).thenAnswer(call->{ assertTrue(virtual || shopOwner.get());return state.get(); });
      doAnswer(call->{ assertTrue(shopOwner.get());state.set(true);return null; }).when(display).spawn();
      doAnswer(call->{ assertTrue(shopOwner.get());state.set(false);return null; }).when(display).remove(false);
      shops.add(shop);
      return display;
    }

    void capture(Player player) {
      owner.set(player);
      try { captures.remove(player).accept(null); }
      finally { owner.set(null); }
    }
    void captureAll() { for(Player player : players) capture(player); }
    void scan() { async.remove(0).accept(null); }
    void apply() {
      shopOwner.set(true);
      try { for(var callback : List.copyOf(regions)) callback.accept(null);regions.clear(); }
      finally { shopOwner.set(false); }
    }
    @Override public void close() { watcher.stop();bukkit.close();quickshop.close(); }
  }
}
