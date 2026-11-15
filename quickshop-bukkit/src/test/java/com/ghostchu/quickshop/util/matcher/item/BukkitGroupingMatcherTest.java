package com.ghostchu.quickshop.util.matcher.item;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.event.general.ShopItemMatchEvent;
import com.ghostchu.quickshop.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BukkitGroupingMatcherTest {
  @Test void missingIdsAreReadOnceWhileEveryComparisonGetsFreshEventCopies() {
    try(var f = new Fixture()) {
      ItemStack a = f.item("a", 7);
      ItemStack b = f.item("b", 9);
      var matches = f.matcher.createGroupingMatcher();
      for(int i = 0; i < 3; i++) assertFalse(matches.test(a, b));
      verify(f.platform, times(1)).getItemShopId(any());
      verify(f.manager, times(3)).callEvent(any(ShopItemMatchEvent.class));
      verify(a, times(4)).clone();
      verify(b, times(4)).clone();
      assertEquals(6, f.eventCopies.size());
      assertEquals(6, f.eventCopies.stream().distinct().count());
    }
  }

  @Test void sharedIdsMergeDifferentItemsAndReadEachNormalizedSnapshotOnce() {
    try(var f = new Fixture()) {
      f.ids.put("a", "shared");
      f.ids.put("b", "shared");
      ItemStack a = f.item("a", 7);
      ItemStack b = f.item("b", 9);
      var matches = f.matcher.createGroupingMatcher();
      assertTrue(matches.test(a, b));
      assertTrue(matches.test(a, b));
      verify(f.platform, times(2)).getItemShopId(any());
      assertEquals(7, a.getAmount());
      assertEquals(9, b.getAmount());
    }
  }

  @Test void listenerMutationCannotReachSourcesOrCachedNormalizedItems() {
    try(var f = new Fixture()) {
      f.mutateEvents = true;
      ItemStack a = f.item("a", 7);
      ItemStack b = f.item("b", 9);
      var matches = f.matcher.createGroupingMatcher();
      assertFalse(matches.test(a, b));
      assertFalse(matches.test(a, b));
      assertEquals(7, a.getAmount());
      assertEquals(9, b.getAmount());
      assertTrue(f.eventCopies.stream().allMatch(item->item.getAmount() == 99));
      assertTrue(f.idCopies.stream().allMatch(item->item.getAmount() == 1));
      assertTrue(f.idCopies.stream().noneMatch(f.eventCopies::contains));
    }
  }

  @Test void eventAcceptanceSkipsNormalizationAndIdLookups() {
    try(var f = new Fixture()) {
      f.acceptEvents = true;
      ItemStack a = f.item("a", 7);
      ItemStack b = f.item("b", 9);
      var matches = f.matcher.createGroupingMatcher();
      assertTrue(matches.test(a, b));
      assertTrue(matches.test(a, b));
      verifyNoInteractions(f.platform);
      verify(a, times(2)).clone();
      verify(b, times(2)).clone();
    }
  }

  @Test void directSimilaritySkipsEventsAndPreparation() {
    try(var f = new Fixture()) {
      ItemStack a = f.item("same", 7);
      ItemStack b = f.item("same", 9);
      assertTrue(f.matcher.createGroupingMatcher().test(a, b));
      verifyNoInteractions(f.platform, f.manager);
      verify(a, never()).clone();
      verify(b, never()).clone();
    }
  }

  @Test void nextGroupingContextReadsNewIds() {
    try(var f = new Fixture()) {
      ItemStack a = f.item("a", 7);
      ItemStack b = f.item("b", 9);
      assertFalse(f.matcher.createGroupingMatcher().test(a, b));
      f.ids.put("a", "new");
      f.ids.put("b", "new");
      assertTrue(f.matcher.createGroupingMatcher().test(a, b));
      verify(f.platform, times(3)).getItemShopId(any());
    }
  }

  @Test void nullInputsRetainTheirOriginalOutcomes() {
    try(var f = new Fixture()) {
      ItemStack a = f.item("a", 7);
      var matches = f.matcher.createGroupingMatcher();
      assertTrue(matches.test(null, null));
      assertFalse(matches.test(null, a));
      assertFalse(matches.test(a, null));
      verifyNoInteractions(f.platform, f.manager);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final QuickShop plugin = mock(QuickShop.class);
    final Platform platform = mock(Platform.class);
    final PluginManager manager = mock(PluginManager.class);
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final BukkitItemMatcherImpl matcher = new BukkitItemMatcherImpl(plugin);
    final Map<ItemStack, String> keys = new IdentityHashMap<>();
    final Map<String, String> ids = new HashMap<>();
    final List<ItemStack> eventCopies = new ArrayList<>();
    final List<ItemStack> idCopies = new ArrayList<>();
    boolean acceptEvents;
    boolean mutateEvents;

    Fixture() {
      when(plugin.platform()).thenReturn(platform);
      bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
      when(platform.getItemShopId(any())).thenAnswer(call->{
        ItemStack item = call.getArgument(0);
        assertEquals(1, item.getAmount());
        idCopies.add(item);
        return ids.get(keys.get(item));
      });
      doAnswer(call->{
        ShopItemMatchEvent event = call.getArgument(0);
        eventCopies.add(event.original());
        eventCopies.add(event.comparison());
        if(mutateEvents) {
          event.original().setAmount(99);
          event.comparison().setAmount(99);
        }
        event.matches(acceptEvents);
        return null;
      }).when(manager).callEvent(any(ShopItemMatchEvent.class));
    }

    ItemStack item(String key, int initialAmount) {
      ItemStack item = mock(ItemStack.class);
      AtomicInteger amount = new AtomicInteger(initialAmount);
      keys.put(item, key);
      when(item.getType()).thenReturn(Material.STONE);
      when(item.getAmount()).thenAnswer(call->amount.get());
      doAnswer(call->{ amount.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
      when(item.isSimilar(any())).thenAnswer(call->key.equals(keys.get(call.getArgument(0))));
      when(item.clone()).thenAnswer(call->item(key, amount.get()));
      return item;
    }

    @Override public void close() { bukkit.close(); }
  }
}
