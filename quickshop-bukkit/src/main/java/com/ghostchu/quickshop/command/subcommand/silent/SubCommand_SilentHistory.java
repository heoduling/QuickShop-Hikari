package com.ghostchu.quickshop.command.subcommand.silent;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.command.CommandParser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermission;
import com.ghostchu.quickshop.shop.history.ShopHistoryGui;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SubCommand_SilentHistory extends SubCommand_SilentBase {

  public SubCommand_SilentHistory(final QuickShop plugin) {

    super(plugin);
  }

  @Override
  protected void doSilentCommand(final Player sender, @NotNull final Shop shop, @NotNull final CommandParser parser) {

    if(!shop.playerAuthorize(sender.getUniqueId(), BuiltInShopPermission.VIEW_PURCHASE_LOGS)
       && !plugin.perm().hasPermission(sender, "quickshop.other.history")) {
      plugin.text().of(sender, "not-managed-shop").send();
      return;
    }

    ShopHistoryGui.open(sender, List.of(shop));
  }
}
