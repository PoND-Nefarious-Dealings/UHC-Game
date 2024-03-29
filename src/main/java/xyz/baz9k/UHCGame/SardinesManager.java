package xyz.baz9k.UHCGame;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.TextColor;
import xyz.baz9k.UHCGame.util.stack.StaticItemProperties;
import xyz.baz9k.UHCGame.util.stack.TransItemStack;
import xyz.baz9k.UHCGame.util.tag.UUIDTagType;

import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

/**
 * Manages the handling of sardines in the sardine game mode.
 * <p>All unassigned players have a sardine in their inventory. 
 * 
 * <p>Passing it onto another unassigned player causes them to join a team.
 * Passing it onto an assigned player causes them to join the assigned team.
 * 
 * <p>When a team is formed, sardines are cleared.
 */
public class SardinesManager implements Listener {
    private final UHCGamePlugin plugin;
    private final NamespacedKey sardineKey;

    public SardinesManager(UHCGamePlugin plugin) {
        this.plugin = plugin;
        this.sardineKey = new NamespacedKey(plugin, "sardine_owner");
    }

    /**
     * @return true if sardines mode is enabled and working
     */
    private boolean enabled() {
        return plugin.getGameManager().hasUHCStarted()
            && plugin.configValues().sardines();
    }

    /**
     * Creates a sardine for a player
     * @param p the player
     * @return the created sardine
     */
    private ItemStack createSardine(OfflinePlayer p) {
        TransItemStack sardine = new TransItemStack("item.sardines", 
            new StaticItemProperties(
                Material.COD, 
                noDeco(TextColor.color(0xF9C9A9)),
                null, 
                new Object[]{p.getName()},
                true,
                m -> {
                    var container = m.getPersistentDataContainer();
                    container.set(sardineKey, new UUIDTagType(), p.getUniqueId());
                }
            )
        );
        return sardine;
    }

    /**
     * Gives a player their sardine
     * @param p the player to give sardine to
     */
    public void giveSardine(Player p) {
        ItemStack sardine = createSardine(p);
        var excess = p.getInventory().addItem(sardine);
        excess.values()
            .forEach(s -> {
                // item is gonna drop either way, so we kinda just have to kill it when it spawns if it's not supposed to??
                Item it = p.getWorld().dropItem(p.getLocation(), s);
                if (!new PlayerDropItemEvent(p, it).callEvent()) {
                    it.setWillAge(true);
                    it.setCanPlayerPickup(false);
                    it.setCanMobPickup(false);
                    it.setInvulnerable(false);
                    it.setTicksLived(5 * 60 * 20);
                }
            });
    }

    /**
     * Check if item entity is a sardine item
     * @param it item entity
     * @return true/false
     */
    public boolean isSardine(Item it) {
        return isSardine(it.getItemStack());
    }
    
    /**
     * Check if item stack is sardine item
     * @param s item stack
     * @return true/false
     */
    public boolean isSardine(ItemStack s) {
        if (s == null) return false;
        var m = s.getItemMeta();
        var container = m.getPersistentDataContainer();
        return container.has(sardineKey, new UUIDTagType());
    }

    private Stream<ItemStack> sardinesInInventory(Player p) {
        return Arrays.stream(p.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(this::isSardine);
    }
    /**
     * Check if player has their own sardine
     * @param p player
     * @return true/false
     */
    public boolean hasSardine(Player p) {
        return sardinesInInventory(p)
            .map(this::uuidOfSardine)           // convert inv to list of UUIDs (or empty if not sardine)
            .flatMap(Optional::stream)          // conv Optional<UUID> to UUID
            .anyMatch(p.getUniqueId()::equals); // check if any UUID matches our UUID
    }
    /**
     * Check if player needs a sardine
     * @param p player to check
     * @return true/false
     */
    public boolean needsSardine(Player p) {
        return plugin.getTeamManager().isWildcard(p) 
            && !hasSardine(p);
    }

    /**
     * Give player the sardine if they need it
     * @param p player to maybe give sardine to
     */
    public void giveSardineIfNeedy(Player p) {
        if (needsSardine(p)) giveSardine(p);
    }

    /**
     * @param s the item stack to evaluate
     * @return the UUID of the owner of the item stack if it's a sardine, otherwise empty
     */
    public Optional<UUID> uuidOfSardine(ItemStack s) {
        if (s == null) return Optional.empty();
        var m = s.getItemMeta();
        var container = m.getPersistentDataContainer();
        UUID uuid = container.get(sardineKey, new UUIDTagType());
        return Optional.ofNullable(uuid);
    }

    /**
     * @param s the item stack to evaluate
     * @return the owner of the item stack if it's a sardine and if the owner is online
     */
    public Optional<Player> whoseSardine(ItemStack s) {
        return uuidOfSardine(s).map(Bukkit::getPlayer);
    }
    
    /**
     * Remove any sardines from the player's inventory that isn't theirs
     * @param p player to remove sardines from
     */
    public void eatSardines(Player p, boolean preserveOwn) {
        var sardines = sardinesInInventory(p);

        if (preserveOwn) sardines = sardines.filter(s -> p != whoseSardine(s).get());

        sardines.forEach(s -> s.setType(Material.AIR));
    }

    private void initAssign(Player giver, Player recipient) {
        if (giver == recipient) return; // not successful, do not assign
        var tm = plugin.getTeamManager();
        
        if (tm.isWildcard(recipient)) { // two wildcard interaction, new team
            int t = tm.getNumTeams() + 1;
            tm.assignPlayerToTeam(giver, t, false);
            tm.assignPlayerToTeam(recipient, t, false);
        } else { // wildcard gives to team, join team
            int t = tm.getTeam(recipient);
            tm.assignPlayerToTeam(giver, t);
        }

        eatSardines(giver, false);
        eatSardines(recipient, false);
    }

    @EventHandler
    public void onCollectSardine(PlayerAttemptPickupItemEvent e) {
        if (!enabled()) return;
        // check if sardine collect is valid
        // only accept givers who are wildcards, only accept assigned combatant recipients
        Item maybeSardineItem = e.getItem();
        if (isSardine(maybeSardineItem)) {
            var tm = plugin.getTeamManager();
            
            Optional<Player> oGiver = whoseSardine(maybeSardineItem.getItemStack())
                .filter(tm::isWildcard);
            Optional<Player> oRecipient = Optional.of(e.getPlayer())
                .filter(tm::isAssignedCombatant);

            if (oGiver.isPresent() && oRecipient.isPresent()) {
                Player giver = oGiver.get(),
                       recipient = oRecipient.get();
                
                initAssign(giver, recipient);

            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (oGiver.isPresent()) giveSardineIfNeedy(oGiver.get());
                if (oRecipient.isPresent()) eatSardines(oRecipient.get(), true);
            }, 1);
        }
    }

    @EventHandler
    public void onDropSardine(PlayerDropItemEvent e) {
        if (!enabled()) return;
        // set up sardine properties
        Item maybeSardine = e.getItemDrop();

        if (isSardine(maybeSardine)) {
            maybeSardine.setInvulnerable(true); // check invul allows sardine to despawn 
            maybeSardine.setTicksLived((4 * 60 + 30) * 20);
        }
    }

    @EventHandler
    public void onInventoryDraggingSardine(InventoryClickEvent e) {
        if (!enabled()) return;
        // make sure sardine cannot be dragged out of inventory while in an inventory view
        InventoryView iview = e.getView();
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return; // this is a player inventory do whatever

        // if there's a click on the player inventory, cancel if it's a sardine
        if (e.getClickedInventory() == iview.getBottomInventory()) {
            ItemStack maybeSardine = e.getView().getItem(e.getRawSlot());
            if (isSardine(maybeSardine)) {
                e.setCancelled(true);
            }
        }

    }

    @EventHandler
    public void onSardineDespawn(ItemDespawnEvent e) {
        if (!enabled()) return;
        // if sardine despawns, pop it back into the player's inventory
        Item maybeSardineItem = e.getEntity();
        ItemStack ms = maybeSardineItem.getItemStack();
        
        whoseSardine(ms)
            .ifPresent(this::giveSardineIfNeedy);
    }

    @EventHandler
    public void onSardineConsume(PlayerItemConsumeEvent e) {
        if (!enabled()) return;

        if (isSardine(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled()) return;

        Player p = e.getPlayer();
        giveSardineIfNeedy(p);
    }
}
