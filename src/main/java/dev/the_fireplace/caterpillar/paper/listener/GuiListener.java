package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.paper.HeadGui;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Click handling for the drill head GUI: the power button toggles the machine, decoration slots are inert,
 * the fuel slot only accepts fuel, and shift-clicking is routed by hand so items never land in the wrong slot.
 */
public final class GuiListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public GuiListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HeadGui gui)) {
            return;
        }
        HumanEntity clicker = event.getWhoClicked();
        Machine machine = plugin.manager().get(gui.machineId());
        if (machine == null) {
            event.setCancelled(true);
            clicker.closeInventory();
            return;
        }

        InventoryAction action = event.getAction();
        // "Double click to gather" would pull items out of the decoration slots.
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }

        if (raw < HeadGui.SIZE) {
            if (raw == HeadGui.BUTTON) {
                event.setCancelled(true);
                if (clicker instanceof Player player) {
                    togglePower(player, machine, gui);
                }
                return;
            }
            if (!HeadGui.isStorageSlot(raw)) {
                event.setCancelled(true);
                return;
            }
            if (raw == HeadGui.FUEL && putsNonFuelInFuelSlot(event, clicker)) {
                event.setCancelled(true);
                return;
            }
        } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                event.setCurrentItem(gui.insert(current, plugin.fuels()));
            }
        }
        plugin.manager().markDirty();
    }

    private boolean putsNonFuelInFuelSlot(InventoryClickEvent event, HumanEntity clicker) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> !plugin.fuels().isFuel(event.getCursor());
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> hotbarItemIsNotFuel(event, clicker);
            default -> false;
        };
    }

    private boolean hotbarItemIsNotFuel(InventoryClickEvent event, HumanEntity clicker) {
        ItemStack other;
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            other = clicker.getInventory().getItemInOffHand();
        } else {
            int button = event.getHotbarButton();
            other = button >= 0 ? clicker.getInventory().getItem(button) : null;
        }
        return other != null && !other.getType().isAir() && !plugin.fuels().isFuel(other);
    }

    private void togglePower(Player player, Machine machine, HeadGui gui) {
        if (machine.powered()) {
            machine.powerOff(plugin.env());
            player.sendActionBar(plugin.lang().get("msg.powered-off"));
        } else if (machine.powerOn(plugin.env())) {
            player.sendActionBar(plugin.lang().get("msg.powered-on"));
        } else {
            player.sendActionBar(plugin.lang().get("msg.no-fuel"));
        }
        gui.refresh(machine);
        plugin.manager().markDirty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HeadGui)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= HeadGui.SIZE) {
                continue;
            }
            if (!HeadGui.isStorageSlot(raw)) {
                event.setCancelled(true);
                return;
            }
            if (raw == HeadGui.FUEL && !plugin.fuels().isFuel(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
        plugin.manager().markDirty();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HeadGui) {
            plugin.manager().markDirty();
        }
    }
}
