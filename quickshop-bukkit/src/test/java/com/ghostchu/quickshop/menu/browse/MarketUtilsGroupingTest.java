package com.ghostchu.quickshop.menu.browse;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.ItemMatcher;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.shop.ContainerShop;
import com.ghostchu.quickshop.util.matcher.item.BukkitItemMatcherImpl;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketUtilsGroupingTest {
  @Test void customBuiltInMatcherSubclassKeepsItsOverriddenRule() {
    QuickShop plugin = mock(QuickShop.class);
    BukkitItemMatcherImpl custom = new BukkitItemMatcherImpl(plugin) {
      @Override public boolean matches(ItemStack original, ItemStack tester) {
        return original.getType() == tester.getType();
      }

      @Override public java.util.function.BiPredicate<ItemStack, ItemStack> createGroupingMatcher() {
        throw new AssertionError("Subclass must use its original matcher entry point");
      }
    };
    when(plugin.getItemMatcher()).thenReturn(custom);
    var a = shop(item(Material.STONE), 1, true, 1);
    var b = shop(item(Material.STONE), 2, true, 1);
    try(var quickshop = mockStatic(QuickShop.class)) {
      quickshop.when(QuickShop::getInstance).thenReturn(plugin);
      assertEquals(List.of(a, b), MarketUtils.groupShopsByItem(List.of(a, b)).get(0).getShops());
    }
  }

  @Test void materialLookupUsesExactContainerWithoutCloningItsItem() {
    ContainerShop shop = mock(ContainerShop.class);
    when(shop.getMaterial()).thenReturn(Material.DIAMOND);
    assertEquals(ContainerShop.class, shop.getClass());
    assertEquals(Material.DIAMOND, MarketUtils.getItemMaterial(shop));
    verify(shop, never()).getItem();
  }

  @Test void materialLookupRetainsUnknownContainerSubclassItemOverride() {
    ContainerShop shop = mock(ContainerShop.class, withSettings().extraInterfaces(Runnable.class));
    ItemStack overridden = item(Material.DIRT);
    when(shop.getMaterial()).thenReturn(Material.DIAMOND);
    when(shop.getItem()).thenReturn(overridden);
    assertNotEquals(ContainerShop.class, shop.getClass());
    assertEquals(Material.DIRT, MarketUtils.getItemMaterial(shop));
    verify(shop).getItem();
    verify(shop, never()).getMaterial();
  }

  @Test void materialLookupRetainsGenericShopEntryPoint() {
    Shop shop = mock(Shop.class);
    ItemStack original = item(Material.STONE);
    when(shop.getItem()).thenReturn(original);
    assertEquals(Material.STONE, MarketUtils.getItemMaterial(shop));
    verify(shop).getItem();
  }

  @BeforeAll static void initializeShop() throws Exception {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
      Class.forName(Shop.class.getName());
    }
  }

  private ItemStack item(Material type) {
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(type);
    when(item.clone()).thenReturn(item);
    return item;
  }

  private ContainerShop shop(ItemStack item, double price, boolean selling, int count) {
    ContainerShop shop = mock(ContainerShop.class, RETURNS_DEEP_STUBS);
    when(shop.getItem()).thenReturn(item);
    when(shop.getPrice()).thenReturn(price);
    when(shop.isSelling()).thenReturn(selling);
    when(shop.isBuying()).thenReturn(!selling);
    when(shop.getInventoryCountCache().getStock()).thenReturn(count);
    when(shop.getInventoryCountCache().getSpace()).thenReturn(count);
    return shop;
  }

  private QuickShop plugin() {
    QuickShop plugin = mock(QuickShop.class);
    ItemMatcher matcher = mock(ItemMatcher.class);
    when(matcher.matches(any(ItemStack.class), any(ItemStack.class)))
        .thenAnswer(call->call.getArgument(0) == call.getArgument(1));
    when(plugin.getItemMatcher()).thenReturn(matcher);
    return plugin;
  }

  @Test void oneItemReadPerShopPreservesMembershipOrderAndBothTradingStatistics() {
    QuickShop plugin = plugin();
    ItemStack first = item(Material.STONE);
    ItemStack second = item(Material.STONE);
    ItemStack third = item(Material.DIAMOND);
    var a = shop(first, 10, true, 3);
    var b = shop(first, 20, true, 4);
    var c = shop(second, 50, true, 2);
    var d = shop(third, 2, true, 1);
    var e = shop(first, 30, false, 7);
    try(var quickshop = mockStatic(QuickShop.class)) {
      quickshop.when(QuickShop::getInstance).thenReturn(plugin);
      var groups = MarketUtils.groupShopsByItem(List.of(a, b, c, d, e));
      assertEquals(3, groups.size());
      assertEquals(List.of(a, b, e), groups.get(0).getShops());
      assertEquals(List.of(c), groups.get(1).getShops());
      assertEquals(List.of(d), groups.get(2).getShops());
      var group = groups.get(0);
      assertEquals(10, group.getSellingMinPrice());
      assertEquals(20, group.getSellingMaxPrice());
      assertEquals(15, group.getSellingAvgPrice());
      assertEquals(15, group.getSellingMedianPrice());
      assertEquals(7, group.getSellingTotalStock());
      assertEquals(30, group.getBuyingMinPrice());
      assertEquals(30, group.getBuyingMaxPrice());
      assertEquals(30, group.getBuyingAvgPrice());
      assertEquals(30, group.getBuyingMedianPrice());
      assertEquals(7, group.getBuyingTotalSpace());
      for(Shop shop : List.of(a, b, c, d, e)) verify(shop, times(1)).getItem();
    }
  }

  @Test void laterGroupingReadsChangedItemsAgain() {
    QuickShop plugin = plugin();
    ItemStack first = item(Material.STONE);
    ItemStack changed = item(Material.STONE);
    var a = shop(first, 1, true, 1);
    var b = shop(first, 2, true, 1);
    try(var quickshop = mockStatic(QuickShop.class)) {
      quickshop.when(QuickShop::getInstance).thenReturn(plugin);
      assertEquals(1, MarketUtils.groupShopsByItem(List.of(a, b)).size());
      when(b.getItem()).thenReturn(changed);
      assertEquals(2, MarketUtils.groupShopsByItem(List.of(a, b)).size());
    }
  }
}
