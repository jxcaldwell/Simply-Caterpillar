package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import io.papermc.paper.entity.TeleportFlag;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/**
 * The seats of drill-seat segments. A player rides an invisible, non-persistent armour stand that is moved along
 * with the segment, so no client mod is needed. The stands are removed as soon as nobody rides them.
 */
public final class Seats {

    private final SimplyCaterpillarPlugin plugin;
    /** Armour stand by the id of the seat segment. */
    private final Map<UUID, ArmorStand> stands = new HashMap<>();

    public Seats(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    private static float yaw(Facing facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
        };
    }

    private Location locationOf(World world, Facing facing, Pos pos) {
        return new Location(world, pos.x() + 0.5, pos.y() + plugin.settings().seatYOffset, pos.z() + 0.5,
                yaw(facing), 0f);
    }

    /** True if somebody is already sitting on this seat. */
    public boolean occupied(UUID segmentId) {
        ArmorStand stand = stands.get(segmentId);
        return stand != null && stand.isValid() && !stand.getPassengers().isEmpty();
    }

    /** Seats the player. Returns false when the seat is taken. */
    public boolean sit(Player player, Machine machine, Machine.Segment segment) {
        if (occupied(segment.id())) {
            return false;
        }
        World world = player.getWorld();
        remove(segment.id());
        Location at = locationOf(world, machine.facing(), segment.pos());
        ArmorStand stand = world.spawn(at, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setMarker(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setBasePlate(false);
        });
        if (!stand.addPassenger(player)) {
            stand.remove();
            return false;
        }
        player.setRotation(yaw(machine.facing()), 0f);
        stands.put(segment.id(), stand);
        return true;
    }

    /** Carries the seat (and its rider) along when the segment has stepped to a new position. */
    public void follow(Machine machine, Machine.Segment segment) {
        ArmorStand stand = stands.get(segment.id());
        if (stand == null || !stand.isValid()) {
            return;
        }
        World world = stand.getWorld();
        stand.teleport(locationOf(world, machine.facing(), segment.pos()), TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    public void remove(UUID segmentId) {
        ArmorStand stand = stands.remove(segmentId);
        if (stand != null) {
            stand.eject();
            stand.remove();
        }
    }

    public void removeAll() {
        for (ArmorStand stand : stands.values()) {
            stand.eject();
            stand.remove();
        }
        stands.clear();
    }

    /** Removes stands nobody sits on any more (the rider dismounted, died or left). */
    public void cleanup() {
        Iterator<ArmorStand> it = stands.values().iterator();
        while (it.hasNext()) {
            ArmorStand stand = it.next();
            if (!stand.isValid() || stand.getPassengers().isEmpty()) {
                stand.remove();
                it.remove();
            }
        }
    }
}
