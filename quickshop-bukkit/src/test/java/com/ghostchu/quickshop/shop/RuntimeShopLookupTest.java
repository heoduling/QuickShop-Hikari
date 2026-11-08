package com.ghostchu.quickshop.shop;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeShopLookupTest {
  @BeforeAll static void initializeShop() throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }

  private AbstractShopManager manager(Map<UUID, Shop> shops) throws Exception {
    AbstractShopManager manager = mock(AbstractShopManager.class, CALLS_REAL_METHODS);
    var field = AbstractShopManager.class.getDeclaredField("allShops");
    field.setAccessible(true);
    field.set(manager, shops);
    return manager;
  }

  @Test void missingOrDeletedUuidReturnsNullForBothOverloads() throws Exception {
    UUID id = UUID.randomUUID();
    var shops = new HashMap<UUID, Shop>();
    var manager = manager(shops);
    assertNull(manager.getShopFromRuntimeRandomUniqueId(id));
    assertNull(manager.getShopFromRuntimeRandomUniqueId(id, true));
    shops.put(id, mock(Shop.class));
    shops.remove(id);
    assertNull(manager.getShopFromRuntimeRandomUniqueId(id));
  }

  @Test void existingShopStillHonorsValidityFilter() throws Exception {
    UUID id = UUID.randomUUID();
    Shop shop = mock(Shop.class);
    var manager = manager(Map.of(id, shop));
    when(shop.isValid()).thenReturn(false);
    assertNull(manager.getShopFromRuntimeRandomUniqueId(id));
    assertSame(shop, manager.getShopFromRuntimeRandomUniqueId(id, true));
    when(shop.isValid()).thenReturn(true);
    assertSame(shop, manager.getShopFromRuntimeRandomUniqueId(id));
  }
}
