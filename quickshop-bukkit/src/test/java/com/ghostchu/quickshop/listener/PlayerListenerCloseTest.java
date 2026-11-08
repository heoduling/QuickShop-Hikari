package com.ghostchu.quickshop.listener;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.localization.text.ProxiedLocale;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.util.Util;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.tnemc.menu.core.manager.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerListenerCloseTest {
  @BeforeAll static void initializeShop() throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }

  @Test void foreignCloseClearsTrackingBeforeObtainingPlayerLocaleAndRefreshingChest() {
    QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    InventoryCloseEvent event = mock(InventoryCloseEvent.class, RETURNS_DEEP_STUBS);
    when(event.getPlayer()).thenReturn(player);
    Location location = new Location(null, 4, 64, 8);
    when(event.getInventory().getLocation()).thenReturn(location);
    AtomicBoolean ownsPlayer = new AtomicBoolean();
    AtomicBoolean ownsChest = new AtomicBoolean();
    AtomicReference<Consumer<ScheduledTask>> playerTask = new AtomicReference<>();
    AtomicReference<Consumer<ScheduledTask>> regionTask = new AtomicReference<>();
    EntityScheduler entityScheduler = mock(EntityScheduler.class);
    when(player.getScheduler()).thenReturn(entityScheduler);
    doAnswer(call -> { playerTask.set(call.getArgument(1)); return null; })
        .when(entityScheduler).run(any(), any(), isNull());
    RegionScheduler regionScheduler = mock(RegionScheduler.class);
    doAnswer(call -> { regionTask.set(call.getArgument(2)); return null; })
        .when(regionScheduler).run(any(), eq(location), any());
    Shop shop = mock(Shop.class);
    ProxiedLocale locale = mock(ProxiedLocale.class);
    when(plugin.text().findRelativeLanguages(player)).thenAnswer(call -> {
      assertTrue(ownsPlayer.get(), "locale must be read on the player owner");
      return locale;
    });
    when(plugin.getShopManager().getShopIncludeAttached(location)).thenAnswer(call -> {
      assertTrue(ownsChest.get(), "shop lookup must run on the chest owner");
      return shop;
    });
    try(var bukkit = mockStatic(Bukkit.class); var menus = mockStatic(MenuManager.class); var util = mockStatic(Util.class)) {
      bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(player)).thenAnswer(call -> ownsPlayer.get());
      bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(location)).thenAnswer(call -> ownsChest.get());
      bukkit.when(Bukkit::getRegionScheduler).thenReturn(regionScheduler);
      menus.when(MenuManager::instance).thenReturn(mock(MenuManager.class));
      util.when(() -> Util.isLoaded(location)).thenReturn(true);
      QuickShop.inShop.add(id);
      try {
        new PlayerListener(plugin).onInventoryClose(event);
        assertFalse(QuickShop.inShop.contains(id), "closed inventory tracking must be removed immediately");
        assertNotNull(playerTask.get());
        assertNull(regionTask.get());
        ownsPlayer.set(true);
        playerTask.get().accept(null);
        assertNotNull(regionTask.get());
        verify(shop, never()).setSignText(locale);
        ownsChest.set(true);
        regionTask.get().accept(null);
        verify(shop).setSignText(locale);
      } finally { QuickShop.inShop.remove(id); }
    }
  }

  @Test void closeWithoutInventoryLocationStillClearsTracking() {
    QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    InventoryCloseEvent event = mock(InventoryCloseEvent.class, RETURNS_DEEP_STUBS);
    UUID id = UUID.randomUUID();
    when(event.getPlayer().getUniqueId()).thenReturn(id);
    when(event.getInventory().getLocation()).thenReturn(null);
    try(var menus = mockStatic(MenuManager.class)) {
      menus.when(MenuManager::instance).thenReturn(mock(MenuManager.class));
      QuickShop.inShop.add(id);
      try {
        new PlayerListener(plugin).onInventoryClose(event);
        assertFalse(QuickShop.inShop.contains(id));
      } finally { QuickShop.inShop.remove(id); }
    }
  }
}
