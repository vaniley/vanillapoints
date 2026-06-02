package dev.vaniley.vanillapoints.api.event;

import dev.vaniley.vanillapoints.api.PointInfo;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class SpawnSetEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final String actorName;
    private final PointInfo oldPoint;
    private final PointInfo newPoint;
    private boolean cancelled;

    public SpawnSetEvent(UUID actorId, String actorName, PointInfo oldPoint, PointInfo newPoint) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.oldPoint = oldPoint;
        this.newPoint = newPoint;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorName() {
        return actorName;
    }

    public PointInfo oldPoint() {
        return oldPoint;
    }

    public PointInfo newPoint() {
        return newPoint;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
