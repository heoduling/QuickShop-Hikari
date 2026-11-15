package com.ghostchu.quickshop.util.matcher.item;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.event.general.ShopItemMatchEvent;
import com.ghostchu.quickshop.api.shop.ItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * A simple impl for ItemMatcher
 *
 * @author Ghost_chu
 */
public class BukkitItemMatcherImpl implements ItemMatcher {

  private final QuickShop plugin;

  public BukkitItemMatcherImpl(final QuickShop plugin) {

    this.plugin = plugin;
  }

  /**
   * Gets the ItemMatcher provider name
   *
   * @return Provider name
   */
  @Override
  public @NotNull String getName() {

    return Bukkit.getServer().getName();
  }

  /**
   * Gets the ItemMatcher provider plugin instance
   *
   * @return Provider Plugin instance
   */
  @Override
  public @NotNull Plugin getPlugin() {

    return plugin.getJavaPlugin();
  }

  /**
   * Tests ItemStacks is matches BEWARE: Different order of itemstacks you might will got different
   * results
   *
   * @param original The original ItemStack
   * @param tester   The ItemStack will test matches with original itemstack.
   *
   * @return The result of tests
   */
  @Override
  public boolean matches(@Nullable ItemStack original, @Nullable ItemStack tester) {

    if(original == null && tester == null) {

      return true;
    }

    final boolean originalNull = original == null;
    final boolean testerNull = tester == null;
    if(originalNull || testerNull) {

      return false;
    }

    if(original.isSimilar(tester)) {

      return true;
    }

    final ShopItemMatchEvent shopItemMatchEvent = new ShopItemMatchEvent(original.clone(), tester.clone()); //clone so the originals don't get messed up by implementations.
    shopItemMatchEvent.callEvent();

    if(shopItemMatchEvent.matches()) {

      return true;
    }

    original = original.clone();
    original.setAmount(1);
    tester = tester.clone();
    tester.setAmount(1);

    final String shopIdOrigin = plugin.platform().getItemShopId(original);
    if(shopIdOrigin != null) {

      final String shopIdTester = plugin.platform().getItemShopId(tester);
      if(shopIdOrigin.equals(shopIdTester)) {

        return true;
      }
    }
    return tester.isSimilar(original);
  }

  /**
   * Creates comparison state for one grouping call over stable item snapshots.
   * Event listeners receive fresh copies on every comparison.
   */
  public BiPredicate<ItemStack, ItemStack> createGroupingMatcher() {

    final Map<ItemStack, PreparedItem> preparedItems = new IdentityHashMap<>();
    return (original, tester)->{
      if(original == null || tester == null) {
        return original == tester;
      }
      if(original.isSimilar(tester)) {
        return true;
      }

      final ShopItemMatchEvent event = new ShopItemMatchEvent(original.clone(), tester.clone());
      event.callEvent();
      if(event.matches()) {
        return true;
      }

      final PreparedItem preparedOriginal = preparedItems.computeIfAbsent(original, PreparedItem::new);
      final PreparedItem preparedTester = preparedItems.computeIfAbsent(tester, PreparedItem::new);
      final ItemStack normalizedOriginal = preparedOriginal.normalized();
      final ItemStack normalizedTester = preparedTester.normalized();
      final String originalShopId = preparedOriginal.shopId();
      if(originalShopId != null && originalShopId.equals(preparedTester.shopId())) {
        return true;
      }
      return normalizedTester.isSimilar(normalizedOriginal);
    };
  }

  private final class PreparedItem {
    private final ItemStack source;
    private ItemStack normalized;
    private String shopId;
    private boolean shopIdRead;

    private PreparedItem(final ItemStack source) {
      this.source = source;
    }

    private ItemStack normalized() {
      if(normalized == null) {
        normalized = source.clone();
        normalized.setAmount(1);
      }
      return normalized;
    }

    private String shopId() {
      if(!shopIdRead) {
        shopId = plugin.platform().getItemShopId(normalized());
        shopIdRead = true;
      }
      return shopId;
    }
  }
}
