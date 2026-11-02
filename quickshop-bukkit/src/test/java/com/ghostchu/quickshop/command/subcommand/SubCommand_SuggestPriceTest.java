package com.ghostchu.quickshop.command.subcommand;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.command.CommandParser;
import com.ghostchu.quickshop.api.localization.text.Text;
import com.ghostchu.quickshop.api.localization.text.TextManager;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubCommand_SuggestPriceTest {
  @BeforeAll
  static void initializeShop() throws ClassNotFoundException {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("QuickShop");
    try(var api = mockStatic(QuickShopAPI.class)) {
      api.when(QuickShopAPI::getPluginInstance).thenReturn(plugin);
// Shop creates its sign key when the interface is first initialized.
      Class.forName(Shop.class.getName());
    }
  }

  @ParameterizedTest
  @CsvSource({"0,3,1", "3,0,1", "1,3,1", "3,1,1", "3,3,2", "2,2,0", "0,0,0"})
  void suggestsOnlyForTypesWithEnoughShops(int buying, int selling, int suggestions) {
    QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    Player player = mock(Player.class, RETURNS_DEEP_STUBS);
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(Material.DIAMOND);
    when(player.getInventory().getItemInMainHand()).thenReturn(item);
    when(plugin.getItemMatcher().matches(item, item)).thenReturn(true);
    List<Shop> shops = new ArrayList<>();
    for(int i = 0; i < buying + selling; i++) {
      Shop shop = mock(Shop.class);
      when(shop.getItem()).thenReturn(item);
      when(shop.isBuying()).thenReturn(i < buying);
      when(shop.isSelling()).thenReturn(i >= buying);
      when(shop.getPrice()).thenReturn((i + 1) * 10.0);
      shops.add(shop);
    }
    when(plugin.getShopManager().getAllShops()).thenReturn(shops);
    TextManager messages = mock(TextManager.class);
    Text text = mock(Text.class);
    when(text.forLocale()).thenReturn(Component.text("suggestion"));
    when(messages.of(eq(player), anyString(), any(Object[].class))).thenReturn(text);
    when(plugin.text()).thenReturn(messages);
    SubCommand_SuggestPrice command = new HeldItemPriceCommand(plugin);
    try(var util = mockStatic(Util.class)) {
      util.when(()->Util.asyncThreadRun(any(Runnable.class))).thenAnswer(call->{
        call.<Runnable>getArgument(0).run();
        return null;
      });
      assertDoesNotThrow(()->command.onCommand(player, "qs", new CommandParser("", true)));
    }
    verify(messages, times(suggestions)).of(eq(player), eq("price-suggest"), any(Object[].class));
    verify(messages, times(suggestions)).of(eq(player), eq("price-suggest-multi"), any(Object[].class));
    if(suggestions == 0) {
      verify(messages).of(player, "cannot-suggest-price", buying + selling);
    }
  }

  private static final class HeldItemPriceCommand extends SubCommand_SuggestPrice {
    private HeldItemPriceCommand(QuickShop plugin) {
      super(plugin);
    }

    @Override
    public Shop getLookingShop(Player sender) {
      return null;
    }
  }
}
