package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /caterpillar give|reload|list} for administrators. */
public final class CaterpillarCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("give", "reload", "list");

    private final SimplyCaterpillarPlugin plugin;

    public CaterpillarCommand(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simplycaterpillar.admin")) {
            sender.sendMessage(plugin.lang().get("msg.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.lang().get("msg.command.usage"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.lang().get("msg.command.reloaded"));
            }
            case "list" -> list(sender);
            default -> sender.sendMessage(plugin.lang().get("msg.command.usage"));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.lang().get("msg.command.usage"));
            return;
        }
        PartType type = PartType.fromId(args[1].toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(plugin.lang().get("msg.command.unknown-part",
                    Placeholder.unparsed("part", args[1]),
                    Placeholder.unparsed("parts", String.join(", ", partIds()))));
            return;
        }

        Player target = null;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.lang().get("msg.command.unknown-player",
                        Placeholder.unparsed("player", args[2])));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        }
        if (target == null) {
            sender.sendMessage(plugin.lang().get("msg.command.usage"));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.lang().get("msg.command.usage"));
                return;
            }
        }

        ItemStack stack = plugin.items().create(type, amount);
        for (ItemStack left : target.getInventory().addItem(stack).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        sender.sendMessage(plugin.lang().get("msg.command.given",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("part", type.id),
                Placeholder.unparsed("player", target.getName())));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(plugin.lang().get("msg.command.list-header",
                Placeholder.unparsed("count", String.valueOf(plugin.manager().all().size()))));
        int shown = 0;
        for (Machine machine : plugin.manager().all()) {
            if (shown++ >= 25) {
                break;
            }
            World world = Bukkit.getWorld(machine.world());
            OfflinePlayer owner = Bukkit.getOfflinePlayer(machine.owner());
            Pos base = machine.base();
            sender.sendMessage(plugin.lang().get("msg.command.list-entry",
                    Placeholder.unparsed("world", world == null ? "?" : world.getName()),
                    Placeholder.unparsed("x", String.valueOf(base.x())),
                    Placeholder.unparsed("y", String.valueOf(base.y())),
                    Placeholder.unparsed("z", String.valueOf(base.z())),
                    Placeholder.unparsed("dir", machine.facing().name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("segments", String.valueOf(machine.segments().size())),
                    Placeholder.unparsed("owner", owner.getName() == null ? machine.owner().toString() : owner.getName())));
        }
    }

    private static List<String> partIds() {
        List<String> ids = new ArrayList<>();
        for (PartType type : PartType.values()) {
            ids.add(type.id);
        }
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simplycaterpillar.admin")) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.addAll(partIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("1", "16", "64"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
