package dev.vaniley.vanillapoints;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncSaveSchedulingTest {
    @Test
    void usesPaperAsyncSchedulerAndNotifiesOnlyOnThePlayersEntityScheduler() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PointStorage storage = mock(PointStorage.class);
        MessageService messages = mock(MessageService.class);
        Player player = mock(Player.class);
        AsyncScheduler asyncScheduler = mock(AsyncScheduler.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.isEnabled()).thenReturn(true);
        when(storage.snapshot()).thenReturn(PointStorageSnapshot.empty());
        doThrow(new StorageException("save failed")).when(storage).save(any());
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(player.isOnline()).thenReturn(true);
        Component failure = Component.text("save failed");
        when(messages.component(player, "data-save-error")).thenReturn(failure);
        AtomicReference<Consumer<ScheduledTask>> asyncTask = new AtomicReference<>();
        AtomicReference<Consumer<ScheduledTask>> entityTask = new AtomicReference<>();
        doAnswer(invocation -> {
            asyncTask.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        }).when(asyncScheduler).runNow(eq(plugin), any());
        doAnswer(invocation -> {
            entityTask.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        }).when(entityScheduler).run(eq(plugin), any(), isNull());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(asyncScheduler);
            AsyncSaveService service = new AsyncSaveService(plugin, storage, messages);
            service.requestSave(player);
            verify(asyncScheduler).runNow(eq(plugin), any());
            verify(storage, never()).snapshot();

            asyncTask.get().accept(mock(ScheduledTask.class));
            verify(entityScheduler).run(eq(plugin), any(), isNull());
            verify(messages, never()).component(player, "data-save-error");
            verify(player, never()).isOnline();

            entityTask.get().accept(mock(ScheduledTask.class));
            verify(player).sendMessage(failure);
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }
}
