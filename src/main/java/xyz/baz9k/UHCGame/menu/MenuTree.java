package xyz.baz9k.UHCGame.menu;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextColor;
import xyz.baz9k.UHCGame.UHCGamePlugin;
import xyz.baz9k.UHCGame.util.Debug;

import static xyz.baz9k.UHCGame.menu.NodeItemStack.*;
import static xyz.baz9k.UHCGame.util.Utils.*;
import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

import java.util.*;

/**
 * Setup for the config GUI tree
 */
public class MenuTree {
    private BranchNode root;
    private UHCGamePlugin plugin;

    public MenuTree(UHCGamePlugin plugin) {
        this.plugin = plugin;
        Node.setPlugin(plugin);

        root = createCtrlPanelBranch();
        createConfigBranch(root);
    }

    public BranchNode root() {
        return root;
    }

    private static int slotAt(int row, int col) {
        return row * 9 + col;
    }

    
    /**
     * Returns the node in the tree that has the specified inventory
     * @param inventory The inventory
     * @return The node (or null if absent)
     */
    public BranchNode getNodeFromInventory(Inventory inventory) {
        return root.getNodeFromInventory(inventory);
    }
    
    @SuppressWarnings("unchecked")
    private BranchNode createCtrlPanelBranch() {
        BranchNode ctrlRoot = new BranchNode(6);
        
        new ActionNode(ctrlRoot, slotAt(1, 2), "start_game", new ItemProperties(o -> {
            boolean succ = ((List<?>) o).size() == 0;
            return succ ? Material.IRON_SWORD : Material.NETHERITE_SWORD;
        })
            .useObject(plugin.getGameManager()::checkStartPanel)
            .extraLore(o -> {
                var lines = new ArrayList<Component>();
                lines.add(render(trans("xyz.baz9k.err.menu.panel.check_start_failed")));
               
                for (var l : (List<String>) o) {
                    lines.add(Component.text(l));
                }
                return new ExtraLore(lines);
            }),
            p -> {
            p.closeInventory();
            plugin.getGameManager().startUHC(false);
        });
        new ActionNode(ctrlRoot, slotAt(1, 6), "end_game", new ItemProperties(o -> {
            boolean succ = ((List<?>) o).size() == 0;
            return succ ? Material.IRON_SHOVEL : Material.NETHERITE_SHOVEL;
        })
            .useObject(plugin.getGameManager()::checkEndPanel)
            .extraLore(o -> {
                var lines = new ArrayList<Component>();
                lines.add(render(trans("xyz.baz9k.err.menu.panel.check_end_failed")));
               
                for (var l : (List<String>) o) {
                    lines.add(Component.text(l));
                }
                return new ExtraLore(lines);
            }),
            p -> {
            p.closeInventory();
            plugin.getGameManager().endUHC(false);
        });

        new ActionNode(ctrlRoot, slotAt(3, 1), "reseed_worlds", new ItemProperties(Material.APPLE), p -> {
            p.closeInventory();
            plugin.getWorldManager().reseedWorlds();
        });
        new ActionNode(ctrlRoot, slotAt(3, 2), "debug_toggle", new ItemProperties(o -> {
            return (boolean) o ? Material.GLOWSTONE : Material.BLACKSTONE;
        })
            .useObject(Debug::isDebugging)
            .extraLore(ExtraLore.fromBool()), 
            p -> {
            Debug.setDebug(!Debug.isDebugging());
        });
        new ActionNode(ctrlRoot, slotAt(3, 3), "stage_next", new ItemProperties(Material.SUNFLOWER), p -> {
            plugin.getGameManager().incrementStage();
        });

        new ActionNode(ctrlRoot, slotAt(4, 7), "clear_teams", new ItemProperties(Material.BLACK_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.resetAllPlayers();
        });
        new ActionNode(ctrlRoot, slotAt(4, 1), "assign_teams_x", new ItemProperties(Material.DIAMOND), p -> {
            var tm = plugin.getTeamManager();
            new ValueRequest(plugin, p, ValueRequest.Type.NUMBER_REQUEST, "team_count", t -> {
                tm.setNumTeams((int) t);
                tm.assignTeams();
                tm.announceTeams();
            }, false);
        });
        new ActionNode(ctrlRoot, slotAt(4, 2), "assign_teams_1", new ItemProperties(Material.RED_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.setTeamSize("solos");
            tm.assignTeams();
            tm.announceTeams();
            
        });
        new ActionNode(ctrlRoot, slotAt(4, 3), "assign_teams_2", new ItemProperties(Material.ORANGE_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.setTeamSize("duos");
            tm.assignTeams();
            tm.announceTeams();
            
        });
        new ActionNode(ctrlRoot, slotAt(4, 4), "assign_teams_3", new ItemProperties(Material.YELLOW_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.setTeamSize("trios");
            tm.assignTeams();
            tm.announceTeams();
            
        });
        new ActionNode(ctrlRoot, slotAt(4, 5), "assign_teams_4", new ItemProperties(Material.GREEN_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.setTeamSize("quartets");
            tm.assignTeams();
            tm.announceTeams();
            
        });
        new ActionNode(ctrlRoot, slotAt(4, 6), "assign_teams_5", new ItemProperties(Material.BLUE_DYE), p -> {
            p.closeInventory();
            var tm = plugin.getTeamManager();
            tm.setTeamSize("quintets");
            tm.assignTeams();
            tm.announceTeams();
            
        });

        return ctrlRoot;
    }

    private BranchNode createConfigBranch(BranchNode root) {
        BranchNode cfgRoot = new BranchNode(root, slotAt(3, 7), "config", new ItemProperties(Material.GOLDEN_PICKAXE), 3);
        ValuedNode.cfgRoot = cfgRoot;

        BranchNode intervals = new BranchNode(cfgRoot, slotAt(1, 3), "intervals",  new ItemProperties(Material.CLOCK),                   3);
        BranchNode wbSize    = new BranchNode(cfgRoot, slotAt(1, 4), "wb_size",    new ItemProperties(Material.BLUE_STAINED_GLASS_PANE), 3);
        BranchNode esoterics = new BranchNode(cfgRoot, slotAt(1, 5), "esoteric",   new ItemProperties(Material.NETHER_STAR),             6);

        /* INTERVALS (in secs) */
        new ValuedNode(intervals, slotAt(1, 2), "start",     new ItemProperties(v -> (int) v == 0 ? Material.BLACK_CONCRETE : Material.RED_CONCRETE)   .formatter(i -> getTimeString((int) i)), ValuedNode.Type.INTEGER, i -> clamp(0, i.intValue(), 7200));
        new ValuedNode(intervals, slotAt(1, 3), "movement1", new ItemProperties(v -> (int) v == 0 ? Material.BLACK_CONCRETE : Material.ORANGE_CONCRETE).formatter(i -> getTimeString((int) i)), ValuedNode.Type.INTEGER, i -> clamp(0, i.intValue(), 7200));
        new ValuedNode(intervals, slotAt(1, 4), "stop",      new ItemProperties(v -> (int) v == 0 ? Material.BLACK_CONCRETE : Material.YELLOW_CONCRETE).formatter(i -> getTimeString((int) i)), ValuedNode.Type.INTEGER, i -> clamp(0, i.intValue(), 7200));
        new ValuedNode(intervals, slotAt(1, 5), "movement2", new ItemProperties(v -> (int) v == 0 ? Material.BLACK_CONCRETE : Material.GREEN_CONCRETE) .formatter(i -> getTimeString((int) i)), ValuedNode.Type.INTEGER, i -> clamp(0, i.intValue(), 7200));
        new ValuedNode(intervals, slotAt(1, 6), "dmwait",    new ItemProperties(v -> (int) v == 0 ? Material.BLACK_CONCRETE : Material.BLUE_CONCRETE)  .formatter(i -> getTimeString((int) i)), ValuedNode.Type.INTEGER, i -> clamp(0, i.intValue(), 7200));

        /* WB SIZE (diameter) */
        new ValuedNode(wbSize, slotAt(1, 2), "initial",    new ItemProperties(Material.RED_STAINED_GLASS),    ValuedNode.Type.DOUBLE, d -> clamp(0, d.doubleValue(), 60000000));
        new ValuedNode(wbSize, slotAt(1, 3), "border1",    new ItemProperties(Material.ORANGE_STAINED_GLASS), ValuedNode.Type.DOUBLE, d -> clamp(0, d.doubleValue(), 60000000));
        new ValuedNode(wbSize, slotAt(1, 5), "border2",    new ItemProperties(Material.GREEN_STAINED_GLASS),  ValuedNode.Type.DOUBLE, d -> clamp(0, d.doubleValue(), 60000000));
        new ValuedNode(wbSize, slotAt(1, 6), "deathmatch", new ItemProperties(Material.PURPLE_STAINED_GLASS), ValuedNode.Type.DOUBLE, d -> clamp(0, d.doubleValue(), 60000000));

        /* ESOTERICS */
        int i = 0;
        new ValuedNode(esoterics, i++, "gone_fishing",  new ItemProperties(Material.FISHING_ROD)          .style(TextColor.color(0x3730FF)), ValuedNode.Type.BOOLEAN);
        new ValuedNode(esoterics, i++, "boss_team",     new ItemProperties(Material.DRAGON_HEAD)          .style(TextColor.color(0xA100FF)), ValuedNode.Type.BOOLEAN);
        new ValuedNode(esoterics, i++, "always_elytra", new ItemProperties(Material.ELYTRA)               .style(TextColor.color(0xB5B8FF)), ValuedNode.Type.BOOLEAN);
        new ValuedNode(esoterics, i++, "sardines",      new ItemProperties(Material.TROPICAL_FISH)        .style(TextColor.color(0xFFBC70)), ValuedNode.Type.BOOLEAN);
        new ValuedNode(esoterics, i++, "wither_bonus",  new ItemProperties(Material.WITHER_SKELETON_SKULL).style(TextColor.color(0x503754)), ValuedNode.Type.BOOLEAN);
        new OptionValuedNode(esoterics, i++, "dn_cycle", new ItemProperties().style(TextColor.color(0xFFEB85)),
            Material.CLOCK,
            Material.COMPASS,
            Material.SNOWBALL,
            Material.GLOWSTONE,
            Material.LIGHT_GRAY_CONCRETE
        );
        new ValuedNode(esoterics, i++, "nether_spawn",  new ItemProperties(Material.NETHERRACK).style(TextColor.color(0x9C4040)), ValuedNode.Type.BOOLEAN);
        new ValuedNode(esoterics, i++, "bomberman",     new ItemProperties(Material.GUNPOWDER) .style(TextColor.color(0x800000)), ValuedNode.Type.BOOLEAN);
        new OptionValuedNode(esoterics, i++, "max_health", new ItemProperties().style(TextColor.color(0xFF2121)),
            Material.SPIDER_EYE,
            Material.APPLE,
            Material.GOLDEN_APPLE,
            Material.ENCHANTED_GOLDEN_APPLE
        );
        new OptionValuedNode(esoterics, i++, "mv_speed", new ItemProperties().style(TextColor.color(0x61A877)),
            Material.SOUL_SAND,
            Material.GRASS_BLOCK,
            Material.ICE,
            Material.EMERALD_BLOCK
        );
        new ActionNode(esoterics, 52, "reset_to_defaults", new ItemProperties(Material.CREEPER_HEAD).style(TextColor.color(0x3BEBD3)), p -> { 
            var defaults = plugin.getConfig().getConfigurationSection("esoteric").getDefaultSection();
            for (Node n : esoterics.getChildren()) {
                if (n instanceof ValuedNode vn) {
                    vn.set(defaults.get(vn.cfgKey()));
                }
            }
            esoterics.updateAllSlots();
         });
    
         return cfgRoot;
    }
}