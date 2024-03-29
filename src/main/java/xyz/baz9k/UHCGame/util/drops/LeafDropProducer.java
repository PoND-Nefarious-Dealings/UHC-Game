package xyz.baz9k.UHCGame.util.drops;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;

import xyz.baz9k.UHCGame.ConfigValues;
import xyz.baz9k.UHCGame.util.Ench;

/**
 * If the event involves breaking leaves, this class can add apples to the drops.
 */
public class LeafDropProducer {
    private final Material leafType;
    private final List<Item> drops;
    private final Optional<Player> player;
    private final Location spawnLoc;
    private final ConfigValues cfg;
    private final Random r;

    /**
     * @param e Event to handle
     * @param cfg Current config values
     */
    public LeafDropProducer(BlockEvent e, ConfigValues cfg) {
        if (e instanceof BlockDropItemEvent drop) {
            this.drops = drop.getItems();
            this.player = Optional.of(drop.getPlayer());
            this.leafType = drop.getBlockState().getType();
        } else if (e instanceof LeavesDecayEvent decay) {
            this.drops = new ArrayList<>();
            this.player = Optional.empty();
            this.leafType = decay.getBlock().getType();
        } else {
            throw new IllegalArgumentException("BlockEvent e is not BlockDropItemEvent or LeavesDecayEvent");
        }

        this.spawnLoc = e.getBlock().getLocation();
        this.cfg = cfg;
        this.r = new Random();
    }

    private int appleMultiplier() {
        return cfg.appleDropRate();
    }
    private boolean shearApple() {
        return cfg.shearApple();
    }
    private boolean allLeaves() {
        return cfg.allLeaves();
    }

    private Optional<ItemStack> tool() {
        return player.map(p -> p.getInventory().getItemInMainHand())
            .filter(s -> s.getType() != Material.AIR);
    }

    private double appleDropRate() {
        int fortune = tool()
            .map(t -> t.getEnchantmentLevel(Ench.FORTUNE))
            .orElse(0);
        
        int baseDivisor = switch (fortune) {
            case 0  -> 200;
            case 1  -> 180;
            case 2  -> 160;
            case 3  -> 120;
            default ->  40;
        };
        return (double) appleMultiplier() / baseDivisor;
    }

    /**
     * Adds apple drops if applicable.
     */
    public void addDrops() {
        if (!shearApple() 
            && tool().isPresent() 
            && tool().get().getType() == Material.SHEARS) {
                return;
        }

        if (!Tag.LEAVES.isTagged(leafType)) return;
        if (!allLeaves() && leafType != Material.OAK_LEAVES) return;

        // clear out apple drops so we can register our own
        drops.removeIf(it -> it.getItemStack().getType() == Material.APPLE);

        // if drop rate matches, spawn an apple
        if (r.nextDouble() < appleDropRate()) {
            ItemStack apple = new ItemStack(Material.APPLE);
            Item appleDrop = spawnLoc.getWorld().dropItem(spawnLoc, apple);

            drops.add(appleDrop);
        }
    }
}
