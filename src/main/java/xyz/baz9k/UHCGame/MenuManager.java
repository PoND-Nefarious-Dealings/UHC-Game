package xyz.baz9k.UHCGame;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import xyz.baz9k.UHCGame.menu.*;
import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class MenuManager implements Listener {
    private final MenuTree menuTree;
    private final Set<Inventory> readOnlyInvs = new HashSet<>();

    public MenuManager(UHCGamePlugin plugin) {
        this.menuTree = new MenuTree(plugin);
    }

    public void openMenu(@NotNull Player p) {
        menuTree.root().click(p);
    }

    public void openSubMenu(String name, @NotNull Player p) {
        menuTree.root().findDescendant(name).ifPresent(n -> n.click(p));
    }

    public void invSee(Player recipient, Player target) {
        int N_SLOTS = 54;
        Inventory inv = Bukkit.createInventory(null, N_SLOTS, new Key("cmd.invsee.title", target.getName()).trans());
        PlayerInventory targetInv = target.getInventory();
        ItemStack[] contents = new ItemStack[N_SLOTS],
                    targetContents = targetInv.getStorageContents();

        ItemStack emptyItem = new ItemStack(Material.GRAY_STAINED_GLASS);
        emptyItem.editMeta(m -> m.displayName(Component.space()));
        
        for (int i = 0; i < N_SLOTS / 2; i++) {
            contents[i] = emptyItem;
        }
        contents[9 + 1] = targetInv.getHelmet();
        contents[9 + 2] = targetInv.getChestplate();
        contents[9 + 3] = targetInv.getLeggings();
        contents[9 + 4] = targetInv.getBoots();
        contents[9 + 6] = targetInv.getItemInMainHand();
        contents[9 + 7] = targetInv.getItemInOffHand();

        System.arraycopy(targetContents, 0, contents, N_SLOTS / 2, targetContents.length);
        inv.setContents(contents);

        readOnlyInvs.add(inv);
        recipient.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        BranchNode b = menuTree.getNodeFromInventory(e.getInventory());

        if (b != null) {
            // interacted inventory is a node inventory
            if (e.getInventory() == e.getClickedInventory()) { // handle clicks IF the clicked inv is the top of the view
                try {
                    if (e.getCurrentItem() != null) b.onClick((Player) e.getWhoClicked(), e.getSlot());
                } finally {
                    e.setCancelled(true);
                }
            }
        } else {
            if (readOnlyInvs.contains(e.getInventory())) {
                e.setCancelled(true);
            }
        }

    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (inv.getViewers().size() == 0 && readOnlyInvs.contains(inv)) {
            readOnlyInvs.remove(e.getInventory());
        }
    }
}
