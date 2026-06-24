package io.github.tetratheta.mol.plugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/// Groups services and Bukkit resources that live for one enabled plugin runtime.
///
/// Extend this class when a plugin has runtime-owned listeners or scheduled tasks. Register those resources through `registerListener(Listener)` and
/// `registerTask(BukkitTask)` so `terminate()` can clean them up during plugin disable or runtime reload.
///
/// Example:
/// ```java
/// public final class ExampleRuntime extends PluginRuntime {
///   public ExampleRuntime(ExamplePlugin plugin) {
///     super(plugin);
///     registerListener(new ExampleListener(this::runTask));
///   }
///
///   @Override
///   public void terminate() {
///     // Release domain resources first, then Bukkit resources.
///     super.terminate();
///   }
/// }
/// ```
@SuppressWarnings("unused")
public class PluginRuntime {
  private final Set<Listener> listeners;
  private final JavaPlugin plugin;
  private final Set<BukkitTask> tasks;

  /// Creates a runtime bound to one plugin instance.
  ///
  /// @param plugin plugin that owns this runtime
  public PluginRuntime(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    listeners = new HashSet<>();
    tasks = new HashSet<>();
  }

  /// Returns the plugin that owns this runtime.
  ///
  /// Use this in subclasses when a domain service needs the plugin instance itself, for example to build a `NamespacedKey` or schedule a Bukkit task.
  /// Prefer passing narrower services to domain objects when possible.
  ///
  /// @return owning plugin
  protected JavaPlugin getPlugin() {
    return plugin;
  }

  /// Registers a listener and releases it when this runtime terminates.
  ///
  /// Call this from the runtime constructor after all services required by the listener have been created. The listener is registered immediately
  /// with Bukkit's plugin manager and later unregistered by `terminate()`.
  ///
  /// @param listener listener to register
  protected void registerListener(Listener listener) {
    plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    listeners.add(listener);
  }

  /// Tracks a scheduled task and cancels it when this runtime terminates.
  ///
  /// Use this for repeating or delayed tasks created directly through Bukkit's scheduler. For one-tick synchronous work, prefer `runTask(Runnable)`
  /// so completed tasks are removed from the tracking set automatically.
  ///
  /// @param task task to track
  protected void registerTask(BukkitTask task) {
    tasks.add(task);
  }

  /// Schedules a synchronous Bukkit task owned by this runtime.
  ///
  /// Completed tasks are removed from the runtime task set. Pending tasks are still cancelled if this runtime terminates before Bukkit runs them,
  /// which prevents stale reload-era callbacks from touching new services.
  ///
  /// @param runnable task to run on the next server tick
  protected void runTask(Runnable runnable) {
    AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
    BukkitTask task = plugin.getServer().getScheduler().runTask(
        plugin, () -> {
          try {
            runnable.run();
          } finally {
            tasks.remove(taskReference.get());
          }
        }
    );
    taskReference.set(task);
    registerTask(task);
  }

  /// Releases all Bukkit resources owned by this runtime.
  ///
  /// Subclasses that own domain resources should override this method, release those resources first, and then call `super.terminate()`. The base
  /// method unregisters every listener registered through this runtime and cancels every tracked task.
  public void terminate() {
    for (Listener listener : listeners) HandlerList.unregisterAll(listener);
    listeners.clear();
    for (BukkitTask task : tasks) task.cancel();
    tasks.clear();
  }
}
