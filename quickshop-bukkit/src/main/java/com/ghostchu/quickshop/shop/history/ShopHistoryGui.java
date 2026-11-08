package com.ghostchu.quickshop.shop.history;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.database.bean.DataRecord;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import net.kyori.adventure.text.Component;
import net.tnemc.menu.core.compatibility.MenuPlayer;
import net.tnemc.menu.core.manager.MenuManager;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_DATA_RECORDS;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_RECORDS;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_SUMMARY;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.SHOPS_DATA;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.SHOPS_HEADERS;

/**
 * Opens the shop transaction history GUI with every data key the history page requires.
 */
public final class ShopHistoryGui {

  private ShopHistoryGui() {

  }

  /**
   * Loads shop history asynchronously and opens {@code qs:history} for the player.
   *
   * @param player the player who should see the GUI
   * @param shops  shops whose purchase logs should be displayed
   */
  public static void open(@NotNull final Player player, @NotNull final List<Shop> shops) {

    final QuickShop plugin = QuickShop.getInstance();
    final UUID playerId = player.getUniqueId();
    final List<Shop> shopList = new ArrayList<>(shops);
    final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);

    Util.asyncThreadRun(()->{
      final ShopHistory shopHistory = new ShopHistory(plugin, shopList);
      try {
        final List<ShopHistory.ShopHistoryRecord> queryResult = shopHistory.query();
        final ShopHistory.ShopSummary summary = shopHistory.generateSummary().join();
        Log.debug(summary.toString());

        if(queryResult == null) {
          return;
        }

        final Map<Long, Component> shopHeaders = buildShopHeaders(shopList);
        final Map<Long, DataRecord> dataRecords = loadDataRecords(plugin, queryResult);

        Util.mainThreadRun(()->{
          if(!player.isOnline()) {
            return;
          }
          // Recreate the viewer after any previous menu close; TNMS drops viewers on inventory close.
          final MenuViewer viewer = new MenuViewer(playerId);
          MenuManager.instance().addViewer(viewer);
          viewer.addData(SHOPS_DATA, shopList);
          viewer.addData(SHOPS_HEADERS, shopHeaders);
          viewer.addData(HISTORY_RECORDS, queryResult);
          viewer.addData(HISTORY_DATA_RECORDS, dataRecords);
          viewer.addData(HISTORY_SUMMARY, summary);
          MenuManager.instance().open("qs:history", 1, menuPlayer);
        });
      } catch(final Exception e) {
        MenuManager.instance().removeViewer(playerId);
        plugin.text().of(playerId, "internal-error", playerId).send();
        plugin.logger().error("Couldn't query the shop history for shops {}.", shopHistory.shops(), e);
      }
    });
  }

  /**
   * Builds per-shop header components used by history list lore.
   *
   * @param shops shops included in the GUI
   *
   * @return map of shop id to display name
   */
  @NotNull
  public static Map<Long, Component> buildShopHeaders(@NotNull final List<Shop> shops) {

    final Map<Long, Component> shopHeader = HashMap.newHashMap(shops.size());
    for(final Shop shop : shops) {
      if(shop.getShopName() != null) {
        shopHeader.put(shop.getShopId(), QuickShop.getInstance().text().of("history.shop.header-icon-shop-name", shop.getShopName()).forLocale());
        continue;
      }
      final String world = (shop.bukkitLocation().getWorld() != null)? shop.bukkitLocation().getWorld().getName() : "World";
      shopHeader.put(shop.getShopId(), QuickShop.getInstance().text().of("history.shop.header-icon-shop-empty-name", world, shop.bukkitLocation().getBlockX(), shop.bukkitLocation().getBlockY(), shop.bukkitLocation().getBlockZ()).forLocale());
    }
    return shopHeader;
  }

  @NotNull
  private static Map<Long, DataRecord> loadDataRecords(@NotNull final QuickShop plugin, @NotNull final List<ShopHistory.ShopHistoryRecord> queryResult) {

    final Map<Long, DataRecord> dataRecords = new ConcurrentHashMap<>();
    final List<CompletableFuture<Void>> futures = new ArrayList<>();
    for(final ShopHistory.ShopHistoryRecord record : queryResult) {
      final long id = record.dataId();
      futures.add(plugin.getDatabaseHelper()
                      .getDataRecord(id)
                      .thenAccept(data->{
                        if(data != null) {
                          dataRecords.put(id, data);
                        }
                      })
                      .exceptionally(ex->{
                        plugin.logger().warn("Failed to load DataRecord for id {}", id, ex);
                        return null;
                      }));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return dataRecords;
  }
}
