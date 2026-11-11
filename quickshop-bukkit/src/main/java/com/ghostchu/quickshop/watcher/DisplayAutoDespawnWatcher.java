package com.ghostchu.quickshop.watcher;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.shop.ContainerShop;
import com.ghostchu.quickshop.shop.display.AbstractDisplayItem;
import com.ghostchu.quickshop.shop.display.virtual.VirtualDisplayItem;
import com.ghostchu.quickshop.util.paste.item.SubPasteItem;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DisplayAutoDespawnWatcher implements Runnable, Reloadable, SubPasteItem {

  private final QuickShop plugin;
  private volatile int range;
  private WrappedTask task;
  private int taskPeriod;
  private volatile boolean active;
  private final AtomicBoolean scanning = new AtomicBoolean();
  private final AtomicLong generation = new AtomicLong();
  private final ConcurrentMap<Shop, DisplayRequest> pendingDisplays = new ConcurrentHashMap<>();

  private record DisplayRequest(long generation, boolean visible) {}

  public DisplayAutoDespawnWatcher(@NotNull final QuickShop plugin) {

    this.plugin = plugin;
    plugin.getReloadManager().register(this);
    plugin.getPasteManager().register(plugin.getJavaPlugin(), this);
    init();
  }

  public DisplayAutoDespawnWatcher(final QuickShop plugin, final int range) {

    this.plugin = plugin;
    this.range = range;
  }

  private void init() {

    this.range = plugin.getConfig().getInt("shop.display-despawn-range");
  }

  @Override
  public ReloadResult reloadModule() {

    init();
    return ReloadResult.builder().status(ReloadStatus.SUCCESS).build();
  }

  public void start(final int delay, final int period) {

    taskPeriod = period;
    stop();
    active = true;
    task = QuickShop.folia().getScheduler().runTimer(this, delay, period);
  }

  @Override
  public void run() {

    if(!active || !scanning.compareAndSet(false, true)) {
      return;
    }
    final long currentGeneration = generation.get();
    final List<CompletableFuture<Location>> snapshots = new ArrayList<>();
    for(final Player player : Bukkit.getOnlinePlayers()) {
      snapshots.add(captureLocation(player, currentGeneration));
    }
    CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new)).whenComplete((unused, error)->{
      if(!isCurrent(currentGeneration)) {
        releaseScan(currentGeneration);
        return;
      }
      if(error != null) {
        releaseScan(currentGeneration);
        plugin.logger().warn("Unable to capture player locations for display checks", error);
        return;
      }
      try {
        Bukkit.getAsyncScheduler().runNow(plugin.getJavaPlugin(), task->{
          try {
            scanDisplays(snapshots, currentGeneration);
          } finally {
            releaseScan(currentGeneration);
          }
        });
      } catch(final RuntimeException failure) {
        releaseScan(currentGeneration);
        if(isCurrent(currentGeneration)) {
          plugin.logger().warn("Unable to schedule display checks", failure);
        }
      }
    });
  }

  private CompletableFuture<Location> captureLocation(final Player player, final long currentGeneration) {

    final CompletableFuture<Location> snapshot = new CompletableFuture<>();
    final Runnable capture = ()->{
      try {
        snapshot.complete(isCurrent(currentGeneration) && player.isOnline()? player.getLocation() : null);
      } catch(final RuntimeException failure) {
        snapshot.completeExceptionally(failure);
      }
    };
    try {
      if(Bukkit.isOwnedByCurrentRegion(player)) {
        capture.run();
      } else if(player.getScheduler().run(plugin.getJavaPlugin(), task->capture.run(), ()->snapshot.complete(null)) == null) {
        snapshot.complete(null);
      }
    } catch(final RuntimeException failure) {
      snapshot.completeExceptionally(failure);
    }
    return snapshot;
  }

  private void scanDisplays(final List<CompletableFuture<Location>> snapshots, final long currentGeneration) {

    if(!isCurrent(currentGeneration)) {
      return;
    }
    final List<Location> positions = snapshots.stream().map(snapshot->snapshot.getNow(null)).filter(java.util.Objects::nonNull).toList();
    final Set<Shop> loadedShops = plugin.getShopManager().getLoadedShops();
    pendingDisplays.keySet().retainAll(loadedShops);
    final int currentRange = range;
    for(final Shop shop : loadedShops) {
      if(!isCurrent(currentGeneration)) {
        return;
      }
      final Location location = shop.bukkitLocation();
      boolean visible = false;
      for(final Location position : positions) {
        if(position.getWorld() == location.getWorld() && position.distance(location) <= currentRange) {
          visible = true;
          break;
        }
      }
      final AbstractDisplayItem displayItem = ((ContainerShop)shop).getDisplayItem();
      if(displayItem == null || (displayItem instanceof final VirtualDisplayItem<?> virtual
                                && virtual.isSpawned() == visible && !pendingDisplays.containsKey(shop))) {
        continue;
      }
      queueDisplay(shop, location, new DisplayRequest(currentGeneration, visible));
    }
  }

  private void queueDisplay(final Shop shop, final Location location, final DisplayRequest request) {

    DisplayRequest previous;
    // Keep at most one queued owner task per shop, carrying the latest decision.
    do {
      previous = pendingDisplays.get(shop);
      if(!isCurrent(request.generation()) || (previous != null && previous.generation() > request.generation())) {
        return;
      }
    } while(previous == null? pendingDisplays.putIfAbsent(shop, request) != null : !pendingDisplays.replace(shop, previous, request));
    if(previous != null && previous.generation() == request.generation()) {
      return;
    }
    try {
      Bukkit.getRegionScheduler().run(plugin.getJavaPlugin(), location, task->applyDisplay(shop, request.generation()));
    } catch(final RuntimeException failure) {
      pendingDisplays.remove(shop, request);
      if(isCurrent(request.generation())) {
        plugin.logger().warn("Unable to schedule display check for shop {}", shop.getShopId(), failure);
      }
    }
  }

  private void applyDisplay(final Shop shop, final long currentGeneration) {

    DisplayRequest request;
    do {
      request = pendingDisplays.get(shop);
      if(request == null || request.generation() != currentGeneration) {
        return;
      }
    } while(!pendingDisplays.remove(shop, request));
    if(!isCurrent(currentGeneration) || !shop.isLoaded() || shop.isDisableDisplay()
       || !Bukkit.isOwnedByCurrentRegion(shop.bukkitLocation())) {
      return;
    }
    final AbstractDisplayItem displayItem = ((ContainerShop)shop).getDisplayItem();
    if(displayItem != null) {
      if(request.visible()) {
        if(!displayItem.isSpawned()) {
          displayItem.spawn();
        }
      } else if(displayItem.isSpawned()) {
        displayItem.remove(false);
      }
    }
  }

  private boolean isCurrent(final long currentGeneration) {

    return active && generation.get() == currentGeneration && plugin.getJavaPlugin().isEnabled();
  }

  private void releaseScan(final long currentGeneration) {

    if(generation.get() == currentGeneration) {
      scanning.set(false);
    }
  }

  public void stop() {

    active = false;
    generation.incrementAndGet();
    scanning.set(false);
    pendingDisplays.clear();
    try {
      if(task != null && !task.isCancelled()) {
        task.cancel();
      }
    } catch(final IllegalStateException ignore) {
    }
  }

  public void unregister() {

    stop();
    plugin.getReloadManager().unregister(this);
    plugin.getPasteManager().unregister(plugin.getJavaPlugin(), this);
  }

  @Override
  @NotNull
  public String genBody() {

    final StringJoiner joiner = new StringJoiner("<br/>");
    joiner.add("<b>Warning: DisplayAutoDespawnWatcher has been enabled, this may cause lag. This feature is not recommended</b>");
    joiner.add("<p>Range: " + range + "</p>");
    return joiner.toString();
  }

  @Override
  @NotNull
  public String getTitle() {

    return "Display Auto Despawn Watcher";
  }

  public int getTaskPeriod() {

    return this.taskPeriod;
  }
}
