package rxl.commands;

import rxl.Jukebox;
import rxl.utils.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JukeboxCommand implements CommandExecutor, TabCompleter {
    
    private final Jukebox plugin;
    
    public JukeboxCommand(Jukebox plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("jukebox")) {
            handleJukeboxCommand(sender, args);
            return true;
        }
        return false;
    }
    
    private void handleJukeboxCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED));
            return;
        }
        
        if (!player.hasPermission("jukebox.give")) {
            return;
        }
        
        if (args.length == 0) {
            giveJukebox(player);
            return;
        }

        if (args[0].equalsIgnoreCase("help")) {
            showHelp(player);
        } else {
            player.sendMessage(Component.text("Unknown subcommand. Use /jukebox help for help.", NamedTextColor.RED));
        }
    }
    
    private void giveJukebox(Player player) {
        String jukeboxId = plugin.getJukeboxManager().createJukeboxId();
        ItemStack jukebox = ItemUtil.createJukebox(jukeboxId);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(jukebox);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), jukebox);
        }
    }
    
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Jukebox Help ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/jukebox - Get a Jukebox item", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/jukebox help - Show this help", NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• Shift+Right-click: Open Jukebox GUI", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Right-click: Play/Stop music", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Place music discs in the queue slots", NamedTextColor.GRAY));
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("jukebox.give")) {
                completions.add("help");
            }
        }
        return completions.stream().filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
    }
} 