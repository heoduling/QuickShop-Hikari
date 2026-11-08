package com.ghostchu.quickshop.menu.browse;

import net.tnemc.menu.core.Page;
import net.tnemc.menu.core.PlayerInstancePage;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.handlers.MenuClickHandler;
import net.tnemc.menu.core.icon.Icon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Builds a separate page for each viewer before making its icons available to TNML.
 * Rendering and clicks use the same player's page; closing the menu removes it through
 * TNML's {@link PlayerInstancePage#removeInstance(UUID)} callback.
 */
public final class BrowsePage extends PlayerInstancePage {

  private final Map<UUID, Page> playerPages = new ConcurrentHashMap<>();

  public BrowsePage(final int pageNumber, final String menuName, final Consumer<PageOpenCallback> draw) {

    super(pageNumber);
    setLockEmptySlots(true);
    setOpen(callback->callback.getPlayer().viewer().ifPresent(viewer->{
      final Page page = new Page(pageNumber);
      page.setLockEmptySlots(true);
      draw.accept(new PageOpenCallback(page, callback.getPlayer()));

      // Never clear a published page: an inventory build may still be reading it.
      if(playerPages.put(viewer.uuid(), page) == null) {
        viewer.addInstance(menuName);
      }
    }));
  }

  @Override
  public Map<Integer, Icon> getIcons(final UUID player) {

    final Page page = playerPages.get(player);
    return page == null? Map.of() : page.getIcons();
  }

  @Override
  public boolean hasInstance(final UUID player) {

    return playerPages.containsKey(player);
  }

  @Override
  public void removeInstance(final UUID player) {

    playerPages.remove(player);
  }

  @Override
  public boolean onClick(final MenuClickHandler click) {

    return onClick(click.player().identifier(), click);
  }

  @Override
  public boolean onClick(final UUID player, final MenuClickHandler click) {

    final Page page = playerPages.get(player);
    return page == null || page.onClick(click);
  }
}
