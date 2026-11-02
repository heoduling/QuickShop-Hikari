package com.ghostchu.quickshop.command.subcommand.silent;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.command.CommandParser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubCommand_SilentBaseTest {
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
  @CsvSource({"true,true", "false,true", "true,false"})
  void checksTheShopInItsRegionThenReturnsToThePlayer(boolean valid, boolean online) {
    QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
    FoliaLib folia = mock(FoliaLib.class);
    PlatformScheduler scheduler = mock(PlatformScheduler.class);
    when(folia.getScheduler()).thenReturn(scheduler);
    Player player = mock(Player.class);
    when(player.isOnline()).thenReturn(online);
    UUID uuid = UUID.randomUUID();
    Shop shop = mock(Shop.class);
    Location location = new Location(null, 20045, 89, -38774);
    when(shop.bukkitLocation()).thenReturn(location);
    when(shop.isValid()).thenReturn(valid);
    when(plugin.getShopManager().getShopFromRuntimeRandomUniqueId(uuid, true)).thenReturn(shop);
    TestCommand command = new TestCommand(plugin);
    CommandParser parser = new CommandParser(uuid.toString(), true);
    try(var quickShop = mockStatic(QuickShop.class)) {
      quickShop.when(QuickShop::folia).thenReturn(folia);
      command.onCommand(player, "qs", parser);
      verify(plugin.getShopManager()).getShopFromRuntimeRandomUniqueId(uuid, true);
      verify(plugin.getShopManager(), never()).getShopFromRuntimeRandomUniqueId(uuid);
      verify(shop, never()).isValid();
      assertNull(command.executed);
      ArgumentCaptor<Runnable> regionTask = ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler).runAtLocationLater(eq(location), regionTask.capture(), eq(1L));
      regionTask.getValue().run();
      verify(shop).isValid();
      assertNull(command.executed);
      ArgumentCaptor<Runnable> playerTask = ArgumentCaptor.forClass(Runnable.class);
      verify(scheduler).runAtEntityLater(eq(player), playerTask.capture(), eq(1L));
      playerTask.getValue().run();
      if(valid && online) {
        assertSame(shop, command.executed);
      } else {
        assertNull(command.executed);
      }
      if(!valid && online) {
        verify(plugin.text()).of(player, "not-looking-at-shop");
      } else {
        verify(plugin.text(), never()).of(player, "not-looking-at-shop");
      }
    }
  }

  private static final class TestCommand extends SubCommand_SilentBase {
    private Shop executed;

    private TestCommand(QuickShop plugin) {
      super(plugin);
    }

    @Override
    protected void doSilentCommand(Player sender, Shop shop, CommandParser parser) {
      executed = shop;
    }
  }
}
