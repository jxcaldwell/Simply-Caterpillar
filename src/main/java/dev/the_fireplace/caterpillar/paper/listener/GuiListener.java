package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.paper.DecorationGui;
import dev.the_fireplace.caterpillar.paper.HeadGui;
import dev.the_fireplace.caterpillar.paper.ReinforcementGui;
import dev.the_fireplace.caterpillar.paper.IncineratorGui;
import dev.the_fireplace.caterpillar.paper.StorageGui;
import dev.the_fireplace.caterpillar.paper.TransporterGui;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import java.util.ArrayList;
import java.util.List;
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
        if (top.getHolder() instanceof ReinforcementGui || top.getHolder() instanceof DecorationGui
                || top.getHolder() instanceof IncineratorGui) {
            ghostClick(event, top);
        } else if (top.getHolder() instanceof StorageGui storage) {
            clickStorage(event, storage);
        } else if (top.getHolder() instanceof HeadGui gui) {
            clickHead(event, gui);
        } else if (top.getHolder() instanceof TransporterGui) {
            plugin.manager().markDirty();
        }
    }

    private void clickStorage(InventoryClickEvent event, StorageGui gui) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw < StorageGui.SIZE) {
            if (!StorageGui.isStorageSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                event.setCurrentItem(gui.insert(current));
            }
        }
        plugin.manager().markDirty();
    }

    /**
     * The settings GUIs (reinforcement, decoration, incinerator filter) hold "ghost" items: clicking a slot with an
     * item on the cursor copies its type, nothing is moved in or out. The player's own inventory below works as
     * usual so they can pick items up; shift-clicking an item there adds its type to the first free slot.
     *
     * @return true if the click was handled as a ghost-slot click (and cancelled)
     */
    private boolean ghostClick(InventoryClickEvent event, Inventory top) {
        int raw = event.getRawSlot();
        InventoryAction action = event.getAction();
        if (raw >= top.getSize() || raw < 0) {
            // Bottom inventory (or outside the window): normal, except moves that would reach into the top.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (current != null && !current.getType().isAir() && addType(top, current.getType())) {
                    plugin.manager().markDirty();
                }
            } else if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return false;
        }
        event.setCancelled(true);
        // Number keys and the off-hand key "swap" an item into the slot: use that item as if it were on the cursor.
        ItemStack cursor = event.getCursor();
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            cursor = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            cursor = event.getWhoClicked().getInventory().getItemInOffHand();
        }
        boolean changed = setSlot(top, raw, cursor, event.getClick().isRightClick());
        if (changed) {
            plugin.manager().markDirty();
        }
        return true;
    }

    private static boolean setSlot(Inventory top, int raw, ItemStack cursor, boolean rightClick) {
        Object holder = top.getHolder();
        if (holder instanceof ReinforcementGui reinforcement) {
            return reinforcement.click(raw, cursor);
        }
        if (holder instanceof DecorationGui decoration) {
            return decoration.click(raw, cursor, rightClick);
        }
        if (holder instanceof IncineratorGui filter) {
            if (cursor != null && !cursor.getType().isAir()) {
                filter.set(raw, cursor.getType());
            } else {
                filter.getInventory().setItem(raw, null);
            }
            return true;
        }
        return false;
    }

    private static boolean addType(Inventory top, org.bukkit.Material material) {
        Object holder = top.getHolder();
        if (holder instanceof ReinforcementGui reinforcement) {
            return reinforcement.addType(material);
        }
        if (holder instanceof DecorationGui decoration) {
            return decoration.addType(material);
        }
        if (holder instanceof IncineratorGui filter) {
            return filter.addType(material);
        }
        return false;
    }

    private void clickHead(InventoryClickEvent event, HeadGui gui) {
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
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof IncineratorGui || holder instanceof ReinforcementGui || holder instanceof DecorationGui) {
            Inventory top = event.getView().getTopInventory();
            List<Integer> inTop = new ArrayList<>();
            for (int raw : event.getRawSlots()) {
                if (raw < top.getSize()) {
                    inTop.add(raw);
                }
            }
            if (inTop.isEmpty()) {
                return; // a drag within the player's own inventory
            }
            event.setCancelled(true);
            // A click with a slight mouse movement arrives as a one-slot drag: treat it as the click it was.
            if (inTop.size() == 1 && setSlot(top, inTop.get(0), event.getOldCursor(), false)) {
                plugin.manager().markDirty();
            }
            return;
        }
        if (holder instanceof TransporterGui) {
            plugin.manager().markDirty();
            return;
        }
        if (holder instanceof StorageGui) {
            for (int raw : event.getRawSlots()) {
                if (raw < StorageGui.SIZE && !StorageGui.isStorageSlot(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            plugin.manager().markDirty();
            return;
        }
        if (!(holder instanceof HeadGui)) {
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
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof HeadGui || holder instanceof StorageGui || holder instanceof IncineratorGui
                || holder instanceof TransporterGui) {
            plugin.manager().markDirty();
        }
    }
}
