package xyz.baz9k.UHCGame.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static xyz.baz9k.UHCGame.util.Utils.*;

import java.util.Objects;

/**
 * {@link Node} that contains an {@link Inventory}.
 * <p>
 * Each slot contains items that represent other Nodes.
 */
public class BranchNode extends Node {
    private final int slotCount;
    private final Node[] children;
    private final @NotNull Inventory inventory;

    /**
     * Create a root {@link BranchNode}. This node does not have a parent.
     * @param nodeName Node name, which is used to determine the ID
     * @param guiHeight Number of rows in this node's inventory
     */
    // remove tmp when impl complete
    public BranchNode(@NotNull String nodeName, int guiHeight, boolean tmp) {
        this(null, 0, null, nodeName, guiHeight);
    }

    /**
     * Create a root {@link BranchNode}. This node does not have a parent.
     * @param guiName Name of this node's inventory
     * @param guiHeight Number of rows in this node's inventory
     */
    @Deprecated
    public BranchNode(@NotNull String guiName, int guiHeight) {
        this(null, 0, null, guiName, guiHeight);
    }

    /**
     * @param parent Parent node
     * @param slot lot of this node in parent's inventory
     * @param itemStack Item stack of this node in parent's inventory
     * @param guiName Name of this node's inventory
     * @param guiHeight Number of rows in this node's inventory
     */
    @Deprecated
    public BranchNode(@Nullable BranchNode parent, int slot, @Nullable NodeItemStack itemStack, @NotNull String guiName, int guiHeight) {
        this(parent, slot, itemStack, Component.text(guiName), guiHeight);
    }

    /**
     * @param parent Parent node
     * @param slot lot of this node in parent's inventory
     * @param itemStack Item stack of this node in parent's inventory
     * @param guiTitle Title of this node's inventory (with formatting)
     * @param guiHeight Number of rows in this node's inventory
     */
    @Deprecated
    public BranchNode(@Nullable BranchNode parent, int slot, @Nullable NodeItemStack itemStack, @NotNull Component guiTitle, int guiHeight) {
        super(parent, slot, itemStack);
        slotCount = 9 * guiHeight;

        int arrLen = parent == null ? slotCount : slotCount - 1;
        children = new Node[arrLen];

        inventory = Bukkit.createInventory(null, slotCount, guiTitle);
        initInventory();
    }

    /**
     * @param parent Parent node
     * @param slot lot of this node in parent's inventory
     * @param nodeName Node name, which is used to determine the ID
     * @param mat Material for the item stack
     * @param guiHeight Number of rows in this node's inventory
     */
    public BranchNode(@Nullable BranchNode parent, int slot, String nodeName, @Nullable Material mat, int guiHeight) {
        super(parent, slot, nodeName, mat);
        slotCount = 9 * guiHeight;

        int arrLen = parent == null ? slotCount : slotCount - 1;
        children = new Node[arrLen];

        inventory = Bukkit.createInventory(null, slotCount, NodeItemStack.nameOf(id()));
        initInventory();
    }

    private void initInventory() {
        // add glass to all slots
        ItemStack emptyGlass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta m = emptyGlass.getItemMeta();
        m.displayName(Component.space());
        emptyGlass.setItemMeta(m);
        
        for (int i = 0; i < slotCount; i++) {
            inventory.setItem(i, emptyGlass);
        }

        // If we aren't root, add a slot for the "Go Back" button
        if (parent != null) {
            ItemStack goBack = new ItemStack(Material.ARROW);

            m = goBack.getItemMeta();
            m.displayName(trans("xyz.baz9k.uhc.config.inv.go_back").style(noDeco(NamedTextColor.RED)));
            goBack.setItemMeta(m);

            inventory.setItem(slotCount - 1, goBack);
        }
    }

    /**
     * Set child to a slot. If child is null, the child at specified slot is removed from the slot.
     * @param slot
     * @param child
     */
    public void setChild(int slot, @Nullable Node child) {
        Objects.checkIndex(slot, children.length);

        if (child == null) {
            children[slot] = null;
            inventory.setItem(slot, null);
            return;
        }
        children[slot] = child;
        inventory.setItem(slot, child.itemStack);
    }

    /**
     * Handles what happens when a player clicks the item in the slot in this node's inventory.
     * @param p
     * @param slot
     */
    public void onClick(@NotNull Player p, int slot) {
        Objects.checkIndex(0, slotCount);

        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1, 2);
        
        // if not root, add go back trigger
        if (parent != null && slot == slotCount - 1) {
            p.openInventory(parent.inventory);
            return;
        }

        Node node = children[slot];
        if (node != null) node.click(p);
    }

    public void click(Player p) {
        p.openInventory(inventory);
    }

    /**
     * Updates the {@link ItemStack} of the specified child of the inventory.
     * @param slot the slot
     */
    public void updateSlot(int slot) {
        inventory.setItem(slot, children[slot].itemStack);
    }

    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public Node[] getChildren() {
        return children;
    }

    @Override
    public String id() {
        return super.id() + ".root";
    }
}