package xyz.baz9k.UHCGame;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import xyz.baz9k.UHCGame.config.*;

public class ConfigManager implements Listener {
    private final ConfigTree configTree;

    public ConfigManager(UHCGame plugin) {
        this.configTree = new ConfigTree(plugin);
    }

    public void openMenu(@NotNull Player p) {
        configTree.getRoot().click(p);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        BranchNode b = configTree.getNodeFromInventory(e.getInventory());

        if (b == null) return; // interacted view does not have a node inventory
        if (e.getInventory() == e.getClickedInventory()) { // handle clicks IF the clicked inv is the top of the view
            try {
                if (e.getCurrentItem() != null) b.onClick((Player) e.getWhoClicked(), e.getSlot());
            } finally {
                e.setCancelled(true);
            }
        }

    }
}
