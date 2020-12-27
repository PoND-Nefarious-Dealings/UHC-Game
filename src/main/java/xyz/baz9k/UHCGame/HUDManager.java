package xyz.baz9k.UHCGame;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import xyz.baz9k.UHCGame.util.ColorGradient;
import xyz.baz9k.UHCGame.util.ColoredStringBuilder;
import xyz.baz9k.UHCGame.util.TeamColors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import xyz.baz9k.UHCGame.util.Utils;

import java.awt.*;
import java.util.List;
import java.util.Collections;

public class HUDManager implements Listener {
    private UHCGame plugin;
    private GameManager gameManager;
    private TeamManager teamManager;
    public HUDManager(UHCGame plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.teamManager = plugin.getTeamManager();
    }

    private static String createEmptyName(char c){
        return ChatColor.translateAlternateColorCodes('&', "&"+c);
    }

    private String formatState(@NotNull Player p) {

        PlayerState state = teamManager.getPlayerState(p);
        int team = teamManager.getTeam(p);

        if (state == PlayerState.SPECTATOR) return ChatColor.AQUA.toString() + ChatColor.ITALIC + "Spectator";
        if (state == PlayerState.COMBATANT_UNASSIGNED) return ChatColor.ITALIC + "Unassigned";
        return TeamColors.getTeamChatColor(team) + ChatColor.BOLD.toString() + "Team " + team;
    }

    private String formatTeammate(@NotNull Player you, @NotNull Player teammate) {
        ColoredStringBuilder s = new ColoredStringBuilder();

        double teammateHP = teammate.getHealth() + teammate.getAbsorptionAmount();
        double teammateMaxHP = teammate.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        Color FULL_HP = new Color(87, 232, 107);
        Color HALF_HP = new Color(254, 254, 105);
        Color NO_HP = new Color(232, 85, 85);
        Color OVER_HEAL = new Color(171, 85, 232);
        Color gradient;
        if (teammateHP > teammateMaxHP) {
            gradient = OVER_HEAL;
        } else {
            gradient = ColorGradient.multiColorGradient(teammateHP/teammateMaxHP, NO_HP, HALF_HP, FULL_HP);
        }

        // prefix if spectator
        if (teamManager.isSpectator(you)) {
            int team = teamManager.getTeam(teammate);
            s.append(TeamColors.getTeamPrefixWithSpace(team));
        }

        // name and health
        if (teamManager.getPlayerState(teammate) == PlayerState.COMBATANT_DEAD) {
            s.append(teammate.getName(), ChatColor.GRAY, ChatColor.STRIKETHROUGH);
            s.append(" 0♥",ChatColor.GRAY, ChatColor.STRIKETHROUGH);
            return s.toString();
        } else {
            s.append(teammate.getName(), gradient);
            s.append(" " + (int) Math.ceil(teammateHP) + "♥ ", gradient);
        }
        // direction
        Location youLoc = you.getLocation();
        Location teammateLoc = teammate.getLocation();
        double dx = youLoc.getX() - teammateLoc.getX();
        double dz = youLoc.getZ() - teammateLoc.getZ();

        double angle = Math.toDegrees(Math.atan2(dz, dx)); // angle btwn you & teammate
        double yeYaw = youLoc.getYaw();

        double relAngle = Utils.mod(yeYaw - angle + 90, 360) - 180;
        String arrow;
        if (112.5 < relAngle && relAngle < 157.5) arrow = "↙";
        else if (67.5 < relAngle && relAngle < 112.5) arrow = "←";
        else if (22.5 < relAngle && relAngle < 67.5) arrow = "↖";
        else if (-22.5 < relAngle && relAngle < 22.5) arrow = "↑";
        else if (-67.5 < relAngle && relAngle < -22.5) arrow = "↗";
        else if (-112.5 < relAngle && relAngle < -67.5) arrow = "→";
        else if (-157.5 < relAngle && relAngle < -112.5) arrow = "↘";
        else arrow = "↓";
        s.append(arrow, ChatColor.GOLD);

        return s.toString();
    }

    private void addPlayerToScoreboardTeam(@NotNull Scoreboard s, @NotNull Player p, int team){
        Team t = s.getTeam(String.valueOf(team));
        if(t == null){
            t = s.registerNewTeam(String.valueOf(team));
            t.setPrefix(TeamColors.getTeamPrefixWithSpace(team));
        }
        t.addEntry(p.getName());
    }

    private void setTeams(@NotNull Player player){
        Scoreboard s = player.getScoreboard();
        for(Player p : plugin.getServer().getOnlinePlayers()){
            int team = teamManager.getTeam(p);
            addPlayerToScoreboardTeam(s, p, team);
        }
    }

    public void addPlayerToTeams(@NotNull Player player){
        int team = teamManager.getTeam(player);
        for(Player p : plugin.getServer().getOnlinePlayers()){
            Scoreboard s = p.getScoreboard();
            addPlayerToScoreboardTeam(s, player, team);
        }
    }

    public void createHUDScoreboard(@NotNull Player p){
        // give player scoreboard & objective
        Scoreboard newBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        p.setScoreboard(newBoard);

        Objective hud = newBoard.registerNewObjective("hud", "dummy", p.getName());
        hud.setDisplaySlot(DisplaySlot.SIDEBAR);

        setTeams(p);

    }

    private void addHUDLine(@NotNull Player p, @NotNull String name, int position){
        Scoreboard b = p.getScoreboard();
        Team team = b.getTeam(name);
        if (team == null) team = b.registerNewTeam(name);
        if (position < 1 || position > 15) throw new IllegalArgumentException("Position needs to be between 1 and 15.");
        String pname = createEmptyName(Integer.toString(position, 16).charAt(0));
        team.addEntry(pname);

        Objective hud = b.getObjective("hud");
        if(hud == null) return;
        hud.getScore(pname).setScore(position);
    }

    private void setHUDLine(@NotNull Player p, @NotNull String field, @NotNull String text){
        Scoreboard b = p.getScoreboard();
        Team team = b.getTeam(field);
        if(team == null) return;
        team.setPrefix(text);
    }

    public void initializePlayerHUD(@NotNull Player p) {
        createHUDScoreboard(p);

        addHUDLine(p, "state",      15);
        // 14 - 10 are tmate
        addHUDLine(p, "newline",     9);
        addHUDLine(p, "posrot",      8);
        addHUDLine(p, "wbpos",       7);
        addHUDLine(p, "newline",     6);
        addHUDLine(p, "combsalive",  5);
        addHUDLine(p, "teamsalive",  4);
        addHUDLine(p, "kills",       3);
        addHUDLine(p, "newline",     2);
        addHUDLine(p, "elapsedTime", 1);

        setHUDLine(p, "state", formatState(p));
        updateTeammateHUD(p);
        updateMovementHUD(p);
        // update WB POS hud //
        updateCombatantsAliveHUD(p);
        updateTeamsAliveHUD(p);
        updateKillsHUD(p);
        updateElapsedTimeHUD(p);
    }

    public void cleanup(){
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for(Player p : plugin.getServer().getOnlinePlayers()){
            p.setScoreboard(main);
        }
    }

    public void updateTeammateHUD(@NotNull Player p) {
        Scoreboard b = p.getScoreboard();

        int team = teamManager.getTeam(p);
        List<Player> teammates;
        if (teamManager.isAssignedCombatant(p)) {
            teammates = teamManager.getAllCombatantsOnTeam(team);
        } else {
            teammates = teamManager.getAllCombatants();
        }
        teammates.remove(p);
        Collections.sort(teammates, (t1, t2) -> (int)Math.ceil(t1.getHealth()) - (int)Math.ceil(t2.getHealth()));

        int len = Math.min(5, teammates.size());
        for (int i = 0; i < len; i++) {
            String rowName = "tmate" + i;
            Team row = b.getTeam(rowName);
            Player teammate = teammates.get(i);

            if (row == null)  { // if row has not been created before
                addHUDLine(p, rowName, 14 - i);
                row = b.getTeam(rowName);
            }
            setHUDLine(p, rowName, formatTeammate(p, teammate));
        }
    }
    public void updateMovementHUD(@NotNull Player p){
        Location loc = p.getLocation();

        ColoredStringBuilder s = new ColoredStringBuilder();
        // position format
        s.append(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(), ChatColor.GREEN);
        
        // rotation format
        s.append(" (", ChatColor.WHITE);
        double yaw = Utils.mod(loc.getYaw(), 360);
        String xf = yaw < 180 ? "+" : "-";
        String zf = yaw < 90 || yaw > 270 ? "+" : "-";
        s.append(ChatColor.RED + xf + "X " + ChatColor.BLUE + zf + "Z");
        s.append(")", ChatColor.WHITE);

        setHUDLine(p, "posrot", s.toString());
    }

    public void updateWBHUD(@NotNull Player p) {
        Location loc = p.getLocation();

        ColoredStringBuilder s = new ColoredStringBuilder();
        
        // world border radius format
        double r = (p.getWorld().getWorldBorder().getSize() / 2);
        s.append("World Border: ±", ChatColor.AQUA);
        s.append(String.valueOf((int)r), ChatColor.AQUA);

        // distance format
        double distance = r - Math.max(Math.abs(loc.getX()), Math.abs(loc.getZ()));
        s.append(" (");
        s.append((int)distance);
        s.append(" away)");

        setHUDLine(p, "wbpos", s.toString());
    }

    public void updateElapsedTimeHUD(@NotNull Player p){
        String elapsed = Utils.getLongTimeString(gameManager.getElapsedTime());
        ColoredStringBuilder s = new ColoredStringBuilder();
        s.append("Game Time: ",ChatColor.RED);
        s.append(elapsed + " ");
        World world = gameManager.getUHCWorld(Environment.NORMAL);
        long time = world.getTime();
        boolean isDay = !(13188 <= time && time <= 22812);
        Color dayCharacterColor = isDay ? new Color(255, 245, 123) : new Color(43, 47, 119);
        String dayCharacterString = isDay ? "☀" : "☽";
        s.append(dayCharacterString, dayCharacterColor);

        setHUDLine(p, "elapsedTime", s.toString());

    }

    public void updateCombatantsAliveHUD(@NotNull Player p) {
        ColoredStringBuilder s = new ColoredStringBuilder();
        s.append("Combatants: ", ChatColor.WHITE);
        s.append(teamManager.countLivingCombatants());
        s.append(" / ");
        s.append(teamManager.countCombatants());

        setHUDLine(p, "combsalive", s.toString());
    }

    public void updateTeamsAliveHUD(@NotNull Player p) {
        ColoredStringBuilder s = new ColoredStringBuilder();
        s.append("Teams: ", ChatColor.WHITE);
        s.append(teamManager.countLivingTeams());
        s.append(" / ");
        s.append(teamManager.getNumTeams());

        setHUDLine(p, "teamsalive", s.toString());
        
    }

    public void updateKillsHUD(@NotNull Player p) {
        if (teamManager.isSpectator(p)) return;
        ColoredStringBuilder s = new ColoredStringBuilder();
        s.append("Kills: ", ChatColor.WHITE);
        s.append(gameManager.getKills(p));
        
        setHUDLine(p, "kills", s.toString());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent movement){
        Player p = movement.getPlayer();
        if(gameManager.isUHCStarted())
            updateMovementHUD(p);
            // when someone moves, everyone who can see it (specs, ppl on team) should be able to see them move
            for (Player spec : teamManager.getAllSpectators()) {
                updateTeammateHUD(spec);
            }
            int team = teamManager.getTeam(p);
            if (team != 0) {
                for (Player tmate : teamManager.getAllCombatantsOnTeam(team)) {
                    updateTeammateHUD(tmate);
                }
            }
    }

}
