package xyz.baz9k.UHCGame.config;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import xyz.baz9k.UHCGame.UHCGame;

import static xyz.baz9k.UHCGame.util.Utils.*;

/**
 * Setup for the config GUI tree
 */
public class ConfigTree {
    private BranchNode root;
    private static final int ROOT_GUI_HEIGHT = 6;

    public ConfigTree(UHCGame plugin) {
        Node.setPlugin(plugin);
        root = generateTree();
    }

    private static int getSlotCoordinate(int x, int y) {
        return y * 9 + x;
    }

    private static ItemStack itemStack(Material type, String name, String... lore) {

        Component[] loreComps = Arrays.stream(lore)
            .map(Node::withDefaultDescStyle)
            .toArray(Component[]::new);

        return itemStack(type, Component.text(name, noDecoStyle(null)), loreComps);
    }
    private static ItemStack itemStack(Material type, Component name, Component... lore) {
        ItemStack stack = new ItemStack(type);
        ItemMeta m = stack.getItemMeta();

        m.displayName(name);
        m.lore(Arrays.asList(lore));
        
        stack.setItemMeta(m);
        return stack;
    }

    public BranchNode getRoot() {
        return root;
    }

    /**
     * Returns the node in the tree that has the specified inventory
     * @param inventory The inventory
     * @return The node (or null if absent)
     */
    public BranchNode getNodeFromInventory(Inventory inventory) {
        return scanAllChildrenForInventory(inventory, root);
    }

    /**
     * Traverses the tree of a node to find the node that has a specified inventory
     * @param inventory The inventory
     * @param node The node tree to traverse
     * @return The node (or null if absent)
     */
    private static BranchNode scanAllChildrenForInventory(Inventory inventory, BranchNode node) {
        if (node.getInventory() == inventory) {
            return node;
        }

        for (Node child : node.getChildren()) {
            if (child instanceof BranchNode bChild) {
                BranchNode check = scanAllChildrenForInventory(inventory, bChild);
                if (check != null) {
                    return check;
                }
            }
        }
        return null;
    }

    /**
     * @return the root of the tree, once built
     */
    private BranchNode generateTree() {
        BranchNode root = new BranchNode("Config", ROOT_GUI_HEIGHT);

        new ValuedNode(root, getSlotCoordinate(3, 3), itemStack(Material.DIAMOND, "Dice", "number %s"), ValuedNodeType.INTEGER, "team_count");

        new ActionNode(root, getSlotCoordinate(5, 3), itemStack(Material.EMERALD, "Shiny Button", "Click me I dare you"), player -> {
            Bukkit.getServer().sendMessage(Component.text("Clicky Click."));
        });

        new ValuedNode(root, getSlotCoordinate(0, 0), itemStack(Material.QUARTZ, "Boolean", "Should toggle maybe???"), ValuedNodeType.BOOLEAN, "esoteric.gone_fishing");
        new OptionValuedNode(root, getSlotCoordinate(0, 1), itemStack(Material.IRON_INGOT, "Cyclic Thing", "Should flipperoo maybe??"), "esoteric.max_health",
            new OptionData("0", Material.IRON_INGOT),
            new OptionData("1", Material.GOLD_INGOT),
            new OptionData("2", Material.EMERALD),
            new OptionData("3", Material.DIAMOND)
        );
        BranchNode subLevel = new BranchNode(root, getSlotCoordinate(4, 4), itemStack(Material.REDSTONE, "schrodinger's box", "except the cat is dead"), "Fuck", 1);

        new ActionNode(subLevel, getSlotCoordinate(5,0), itemStack(Material.DIAMOND, "Dice 2"), player -> {
            Bukkit.getServer().sendMessage(Component.text("Fuck."));
        });

        return root;
    }
}
