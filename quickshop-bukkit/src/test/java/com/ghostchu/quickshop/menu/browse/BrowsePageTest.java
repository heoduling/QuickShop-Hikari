package com.ghostchu.quickshop.menu.browse;

import net.tnemc.menu.core.Menu;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.compatibility.MenuPlayer;
import net.tnemc.menu.core.compatibility.PlayerInventory;
import net.tnemc.menu.core.handlers.MenuClickHandler;
import net.tnemc.menu.core.icon.Icon;
import net.tnemc.menu.core.icon.action.ActionType;
import net.tnemc.menu.core.manager.MenuManager;
import net.tnemc.menu.core.utils.SlotPos;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BrowsePageTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void keepsEachPlayersIconsAndClickActionsSeparate(final int pageNumber) {

    final TestPlayer first = new TestPlayer();
    final TestPlayer second = new TestPlayer();
    final List<UUID> clicked = new ArrayList<>();
    final BrowsePage page = new BrowsePage(pageNumber, "browse-test", callback->{
      final UUID owner = callback.getPlayer().identifier();
      final Icon icon = icon(9);
      icon.setClick(click->clicked.add(owner));
      callback.getPage().addIcon(icon);
    });
    final Menu menu = new Menu();
    menu.setRows(6);
    menu.addPage(page);

    open(page, first);
    final Icon firstIcon = page.getIcons(first.identifier()).get(9);
    open(page, second);

    assertSame(firstIcon, page.getIcons(first.identifier()).get(9));
    assertNotSame(firstIcon, page.getIcons(second.identifier()).get(9));
    assertTrue(menu.onClick(click(menu, pageNumber, first, 9)));
    assertTrue(menu.onClick(click(menu, pageNumber, second, 9)));
    assertEquals(List.of(first.identifier(), second.identifier()), clicked);
    assertTrue(menu.onClick(click(menu, pageNumber, first, 10)), "Empty slots stay locked");
    assertTrue(page.getIcons().isEmpty(), "The shared icon map must not hold player content");
  }

  @Test
  void rebuildingDoesNotModifyThePageBeingRead() {

    final TestPlayer player = new TestPlayer();
    final AtomicInteger draws = new AtomicInteger();
    final BrowsePage page = new BrowsePage(1, "browse-test", callback->{
      final int start = draws.getAndIncrement() * 10;
      callback.getPage().addIcon(icon(start));
      callback.getPage().addIcon(icon(start + 1));
    });
    open(page, player);
    final Map<Integer, Icon> original = page.getIcons(player.identifier());
    final var iterator = original.entrySet().iterator();
    iterator.next();

    open(page, player);

    assertDoesNotThrow(()->iterator.forEachRemaining(entry->assertTrue(entry.getKey() < 10)));
    assertEquals(2, original.size());
    assertEquals(java.util.Set.of(10, 11), page.getIcons(player.identifier()).keySet());
    assertNotSame(original, page.getIcons(player.identifier()));
  }

  @Test
  void publishesOnlyAfterDrawingFinishes() throws Exception {

    final TestPlayer player = new TestPlayer();
    final CountDownLatch drawing = new CountDownLatch(1);
    final CountDownLatch finish = new CountDownLatch(1);
    final AtomicInteger draws = new AtomicInteger();
    final BrowsePage page = new BrowsePage(1, "browse-test", callback->{
      callback.getPage().addIcon(icon(0));
      if(draws.getAndIncrement() > 0) {
        drawing.countDown();
        await(finish);
      }
      callback.getPage().addIcon(icon(1));
    });
    open(page, player);
    final Map<Integer, Icon> original = page.getIcons(player.identifier());

    try(final var executor = Executors.newSingleThreadExecutor()) {
      final var redraw = executor.submit(()->open(page, player));
      try {
        assertTrue(drawing.await(5, TimeUnit.SECONDS));
        assertSame(original, page.getIcons(player.identifier()));
        assertEquals(2, page.getIcons(player.identifier()).size());
      } finally {
        finish.countDown();
      }
      redraw.get(5, TimeUnit.SECONDS);
    }
    assertNotSame(original, page.getIcons(player.identifier()));
    assertEquals(2, page.getIcons(player.identifier()).size());
  }

  @Test
  void playersCanRebuildWhileAnotherInventoryIterates() throws Exception {

    final List<TestPlayer> players = List.of(new TestPlayer(), new TestPlayer(), new TestPlayer());
    final BrowsePage page = new BrowsePage(1, "browse-test", callback->{
      for(int slot = 0; slot < 54; slot++) {
        final Icon icon = icon(slot);
        icon.setClick(click->assertEquals(callback.getPlayer().identifier(), click.player().identifier()));
        callback.getPage().addIcon(icon);
      }
    });
    final Menu menu = new Menu();
    menu.setRows(6);
    menu.addPage(page);
    final CountDownLatch start = new CountDownLatch(1);
    try(final var executor = Executors.newFixedThreadPool(players.size())) {
      final var runs = players.stream().map(player->executor.submit(()->{
        await(start);
        for(int repeat = 0; repeat < 200; repeat++) {
          open(page, player);
          int count = 0;
          for(final var entry : page.getIcons(player.identifier()).entrySet()) {
            assertTrue(menu.onClick(click(menu, 1, player, entry.getKey())));
            count++;
          }
          assertEquals(54, count);
        }
      })).toList();
      start.countDown();
      for(final var run : runs) {
        run.get(10, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void viewerCleanupRemovesBothPagesWithoutAffectingOtherPlayers() {

    final String menuName = "browse-test-" + UUID.randomUUID();
    final Menu menu = new Menu();
    menu.setName(menuName);
    final BrowsePage grouped = new BrowsePage(1, menuName, callback->callback.getPage().addIcon(icon(9)));
    final BrowsePage shops = new BrowsePage(2, menuName, callback->callback.getPage().addIcon(icon(9)));
    menu.addPage(grouped);
    menu.addPage(shops);
    MenuManager.instance().addMenu(menu);
    final TestPlayer first = new TestPlayer();
    final TestPlayer second = new TestPlayer();
    open(grouped, first);
    open(grouped, first);
    open(shops, first);
    open(grouped, second);
    open(shops, second);

    first.viewer().orElseThrow().removeInstances();

    assertFalse(grouped.hasInstance(first.identifier()));
    assertFalse(shops.hasInstance(first.identifier()));
    assertTrue(grouped.getIcons(first.identifier()).isEmpty());
    assertTrue(shops.getIcons(first.identifier()).isEmpty());
    assertTrue(grouped.hasInstance(second.identifier()));
    assertTrue(shops.hasInstance(second.identifier()));
    assertTrue(menu.onClick(click(menu, 1, first, 9)), "A removed page cannot execute stale actions");
    second.viewer().orElseThrow().removeInstances();
  }

  private static Icon icon(final int slot) {

    final Icon icon = new Icon(null, null);
    icon.setSlot(slot);
    return icon;
  }

  private static void open(final BrowsePage page, final TestPlayer player) {

    page.getOpen().accept(new PageOpenCallback(page, player));
  }

  private static MenuClickHandler click(final Menu menu, final int page, final MenuPlayer player, final int slot) {

    return new MenuClickHandler(new SlotPos(slot), player, menu, page, ActionType.LEFT_CLICK);
  }

  private static void await(final CountDownLatch latch) {

    try {
      if(!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for the other thread");
      }
    } catch(final InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private static final class TestPlayer implements MenuPlayer {

    private final MenuViewer viewer = new MenuViewer(UUID.randomUUID());

    @Override
    public UUID identifier() {

      return viewer.uuid();
    }

    @Override
    public Optional<MenuViewer> viewer() {

      return Optional.of(viewer);
    }

    @Override
    public PlayerInventory<?> inventory() {

      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasPermission(final String permission) {

      return true;
    }

    @Override
    public void message(final String message) {

      throw new UnsupportedOperationException(message);
    }
  }
}
