package dev.vaniley.vanillapoints.api.event;

import dev.vaniley.vanillapoints.api.PointInfo;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class HomeDeletedEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final String actorName;
    private final UUID playerId;
    private final PointInfo deletedPoint;
    private boolean cancelled;

    public HomeDeletedEvent(UUID actorId, String actorName, UUID playerId, PointInfo deletedPoint) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.playerId = playerId;
        this.deletedPoint = deletedPoint;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorName() {
        return actorName;
    }

    public UUID playerId() {
        return playerId;
    }

    public PointInfo deletedPoint() {
        return deletedPoint;
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
