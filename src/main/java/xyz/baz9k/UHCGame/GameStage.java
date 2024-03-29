package xyz.baz9k.UHCGame;

import java.time.Duration;
import java.util.Arrays;

import static xyz.baz9k.UHCGame.util.Utils.*;
import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import static net.kyori.adventure.text.format.TextDecoration.*;

import static java.time.temporal.ChronoUnit.FOREVER;

/**
 * {@link Enum} with properties for each stage of the game.
 * <p>
 * {@link #NOT_IN_GAME} should always be the first game stage
 * <p>
 * {@link #DEATHMATCH} should always be the last game stage
 */
public enum GameStage {
    NOT_IN_GAME (null, BossBar.Color.WHITE, new ConfigDur(Duration.ZERO), new ConfigWBSize(-1), false, null, null),

    WB_STILL    ("wb_still",   BossBar.Color.RED,    new ConfigDur("start"),               new ConfigWBSize("initial"),    true,  Style.style(NamedTextColor.RED),         Style.style(NamedTextColor.GREEN, BOLD)),
    WB_1        ("wb_1",       BossBar.Color.BLUE,   new ConfigDur("movement1"),           new ConfigWBSize("border1"),    false, Style.style(NamedTextColor.BLUE),        Style.style(NamedTextColor.RED, BOLD)),
    WB_STOP     ("wb_stop",    BossBar.Color.RED,    new ConfigDur("stop"),                new ConfigWBSize("border1"),    true,  Style.style(NamedTextColor.RED),         Style.style(NamedTextColor.AQUA)),
    WB_2        ("wb_2",       BossBar.Color.BLUE,   new ConfigDur("movement2"),           new ConfigWBSize("border2"),    false, Style.style(NamedTextColor.BLUE),        Style.style(NamedTextColor.RED)),
    DM_WAIT     ("dm_wait",    BossBar.Color.WHITE,  new ConfigDur("dmwait"),              new ConfigWBSize("border2"),    true,  Style.style(NamedTextColor.WHITE),       Style.style(NamedTextColor.DARK_AQUA)),
    DEATHMATCH  ("deathmatch", BossBar.Color.PURPLE, new ConfigDur(FOREVER.getDuration()), new ConfigWBSize("deathmatch"), true,  Style.style(NamedTextColor.DARK_PURPLE), Style.style(NamedTextColor.BLUE, BOLD));
    
    private static UHCGamePlugin plugin;
    public static void setPlugin(UHCGamePlugin plugin) { GameStage.plugin = plugin; }

    // union type, String | Duration
    private record ConfigDur(String id, Duration def) {
        public ConfigDur(String id)    { this(id, null);  }
        public ConfigDur(Duration def) { this(null, def); }
        
        public Duration get() {
            var cfg = plugin.configValues();
            return cfg.stageDuration(id).orElse(def);
        }
    }

    // union type, String | double
    private record ConfigWBSize(String id, double def) {
        public ConfigWBSize(String id)    { this(id, -1);  }
        public ConfigWBSize(double def)   { this(null, def); }
        
        public double get() {
            var cfg = plugin.configValues();
            return cfg.wbDiameter(id, def);
        }
    }

    private final BossBar.Color bbClr;
    private final ConfigDur dur;
    private final ConfigWBSize wbSize;
    private final Component bbTitle;
    private final Component baseChatMsg;
    private final Style bodyStyle;

    private final boolean isWBInstant;
    /**
     * 
     * @param langKey      Identifier in the translatable key that differentiates each stage
     * @param bbClr        Color of boss bar
     * @param dur          Duration of the stage (either a config ID or duration)
     * <p> If zero, the stage is skipped in iteration
     * @param wbDiameter   Diameter of world border that this stage progresses to (either a config ID or double)
     * @param isWBInstant  If true, WB instantly jumps to the wbDiameter at the start. If false, WB progresses to the wbDiameter by the end of the stage.
     * @param bbTitleStyle Style (color, text decoration, etc.) of the boss bar title
     * @param bodyStyle    Style of the base chat message
     */
    GameStage(String langKey, BossBar.Color bbClr, ConfigDur dur, ConfigWBSize wbDiameter, boolean isWBInstant, Style bbTitleStyle, Style bodyStyle) {
        // bossbar
        this.bbClr = bbClr;

        // stage specific
        this.dur = dur;
        this.wbSize = wbDiameter;
        this.isWBInstant = isWBInstant;

        // message body
        this.bodyStyle = bodyStyle;

        // translatable components
        if (langKey != null) {
            this.bbTitle     = new Key("bossbar.%s",langKey).trans().style(bbTitleStyle);
            this.baseChatMsg = new Key("chat.stage_base.%s",langKey).trans().style(bodyStyle);
        } else {
            this.bbTitle     = Component.empty();
            this.baseChatMsg = Component.empty();
    
        }
    }

    /**
     * Returns the nth active stage, where 0 = first active in-game stage (typically WB_STILL)
     * @param n
     * @return the nth active stage. null if not valid
     */
    public @Nullable static GameStage nth(int n) {
        return Arrays.stream(values())
            .filter(GameStage::isActive)
            .skip(n)
            .findFirst()
            .orElse(null);
    }

    /**
     * @return the first previous stage that has a non-zero duration
     * <p> returns null if called on {@link #NOT_IN_GAME} or there is no previous active stage
     */
    private @Nullable GameStage prev() {
        GameStage[] values = values();

        // iter every stage before this stage in reverse except NOT_IN_GAME
        for (int i = ordinal() - 1; i >= 1; i--) {
            GameStage gs = values[i];
            if (gs.isActive()) return gs;
        }
        return null;
    }
    /**
     * @return the next stage that has a non-zero duration
     * <p> returns null if called on {@link #NOT_IN_GAME} or there is no further active stage
     */
    public @Nullable GameStage next() {
        if (this == NOT_IN_GAME) return null;
        
        return Arrays.stream(values())
            .skip(ordinal() + 1)
            .filter(GameStage::isActive)
            .findFirst()
            .orElse(null);
    }

    /**
     * @return true if the wb changes after this stage
     */
    private boolean wbChangesAfter() {
        var next = next();
        if (next == null) return false;
        return wbDiameter() != next.wbDiameter();
    }


    /* PROPERTIES */
    public BossBar.Color getBBColor() {
        return bbClr;
    }

    public Component getBBTitle() {
        return bbTitle;
    }

    public Duration duration() {
        return dur.get();
    }
    
    public boolean isActive() {
        return !duration().isZero();
    }

    public double wbDiameter() {
        return wbSize.get();
    }

    public double wbRadius() {
        return wbDiameter() / 2;
    }
    
    public boolean isWBInstant() {
        return isWBInstant;
    }

    /**
     * Updates worlds to align with the stage's world border size.
     * @param worlds Worlds to apply world border to
     */
    public void applyWBSize(World... worlds) {
        if (this == NOT_IN_GAME) return;
        for (World w : worlds) {
            if (isWBInstant) {
                w.getWorldBorder().setSize(wbDiameter());
            } else {
                w.getWorldBorder().setSize(wbDiameter(), duration().toSeconds());
            }
        }
    }

    private static final Key WB_NAME = new Key("chat.wb.name");
    private static final Key WB_PRONOUN = new Key("chat.wb.pronoun");

    private static final Key WILL_SHRINK = new Key("chat.warning.wb_will_shrink");
    private static final Key WILL_SHRINK_INSTANT = new Key("chat.warning.wb_will_instant_shrink");
    private static final Key JUST_SHRINK = new Key("chat.warning.wb_will_shrink");
    private static final Key JUST_SHRINK_INSTANT = new Key("chat.warning.wb_will_instant_shrink");
    
    private static final Key DM_WARN = new Key("chat.warning.dm_warn");

    /**
     * Sends the linked message in chat.
     */
    public void sendMessage() {
        if (this == NOT_IN_GAME) return;

        TextComponent.Builder msg = Component.text().append(baseChatMsg);
        if (this != DEATHMATCH) {
            Key situationKey = null;
            Component subject = (this == WB_STILL ? WB_NAME : WB_PRONOUN).trans();

            GameStage next = next();

            // at the beginning of each wb change, add a msg
            // at the end of each wb change, add a msg
            if (prev() != null && prev().wbChangesAfter()) {
                situationKey = isWBInstant ? JUST_SHRINK_INSTANT : JUST_SHRINK;
            } else if (wbChangesAfter()) {
                situationKey = next.isWBInstant ? WILL_SHRINK_INSTANT : WILL_SHRINK;
            }
            
            msg.append(Component.space());

            if (situationKey != null) {
                Component situation = situationKey.trans(subject, wbRadius(), getWordTime(duration()))
                    .style(bodyStyle);
                msg.append(situation);
            }

            if (this == DEATHMATCH.prev()) {
                Component dmwarn = DM_WARN.trans(getWordTime(duration())).style(bodyStyle);
                
                msg.append(Component.space())
                .append(dmwarn);
            }
        }
        
        sendMessageAsBoxless(Bukkit.getServer(), msg);
    }

    public static void sendMessageAsBoxless(Audience a, ComponentLike c) {
        Style nameStyle = Style.style(TextColor.color(0xA679FE), BOLD),
              chatStyle = Style.style(TextColor.color(0xCFCFFF), BOLD);

        Component msg = Component.translatable("chat.type.text",
            new Key("chat.name").trans().style(nameStyle), c
        ).style(chatStyle);

        a.sendMessage(msg);
    }
}
