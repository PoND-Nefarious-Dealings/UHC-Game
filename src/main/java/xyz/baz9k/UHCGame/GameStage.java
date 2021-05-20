package xyz.baz9k.UHCGame;

import java.time.Duration;
import java.util.Arrays;

import static xyz.baz9k.UHCGame.util.Utils.*;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
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
    NOT_IN_GAME,
    WB_STILL   (BossBar.Color.RED,    Duration.ofMinutes(60), 1200, true,  Component.translatable("xyz.baz9k.uhc.bossbar.wb_still",   NamedTextColor.RED),         Component.text("xyz.baz9k.uhc.chat.stage_base.wb_still", NamedTextColor.GREEN, BOLD)),
    WB_1       (BossBar.Color.BLUE,   Duration.ofMinutes(15), 25,   false, Component.translatable("xyz.baz9k.uhc.bossbar.wb_1",       NamedTextColor.BLUE),        Component.text("xyz.baz9k.uhc.chat.stage_base.wb_1", NamedTextColor.RED, BOLD)),
    WB_STOP    (BossBar.Color.RED,    Duration.ofMinutes(5),  25,   true,  Component.translatable("xyz.baz9k.uhc.bossbar.wb_stop",    NamedTextColor.RED),         Component.text("xyz.baz9k.uhc.chat.stage_base.wb_stop", NamedTextColor.AQUA)),
    WB_2       (BossBar.Color.BLUE,   Duration.ofMinutes(10), 3,    false, Component.translatable("xyz.baz9k.uhc.bossbar.wb_2",       NamedTextColor.BLUE),        Component.text("xyz.baz9k.uhc.chat.stage_base.wb_2", NamedTextColor.RED)),
    DM_WAIT    (BossBar.Color.WHITE,  Duration.ofMinutes(5),  3,    true,  Component.translatable("xyz.baz9k.uhc.bossbar.dm_wait",    NamedTextColor.WHITE),       Component.text("xyz.baz9k.uhc.chat.stage_base.dm_wait", NamedTextColor.DARK_AQUA)),
    DEATHMATCH (BossBar.Color.PURPLE, FOREVER.getDuration(),  20,   true,  Component.translatable("xyz.baz9k.uhc.bossbar.deathmatch", NamedTextColor.DARK_PURPLE), Component.text("xyz.baz9k.uhc.chat.stage_base.deathmatch", NamedTextColor.BLUE, BOLD));
    
    private final BossBar.Color bbClr;
    private final Duration dur;
    private final double wbSize;
    private final Component bbTitle;
    private final Component baseChatMsg;
    private final Style bodyStyle;

    private final boolean isWBInstant;
    /**
     * NOT_IN_GAME
     */
    private GameStage() { 
        this(BossBar.Color.WHITE, Duration.ZERO, -1, false, Component.empty(), Component.empty());
    }
    

    /**
     * @param bbClr Color of the boss bar
     * @param dur Duration of the stage
     * @param wbDiameter Diameter of the world border that this stage progresses to
     * @param isWBInstant True if WB instantly jumps to this border at the start, false if progresses to WB by the end
     * @param bbTitle Title of the boss bar as a component (so, with colors and formatting)
     * @param baseChatMsg The base chat message, before color and additional warnings are added
     */
    private GameStage(@NotNull BossBar.Color bbClr, @NotNull Duration dur, int wbDiameter, boolean isWBInstant, @NotNull Component bbTitle, @NotNull Component baseChatMsg) {
        // bossbar
        this.bbClr = bbClr;
        this.bbTitle = bbTitle;

        // stage specific
        this.dur = dur;
        this.wbSize = wbDiameter;
        this.isWBInstant = isWBInstant;

        // message body
        this.baseChatMsg = baseChatMsg;
        this.bodyStyle = baseChatMsg.style();
    }

    @Nullable
    private static GameStage fromOrdinal(int i) {
        try {
            return values()[i];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * 0 = First in game stage
     * @param i
     * @return GameStage connected to index. Null if not valid
     */
    @Nullable
    public static GameStage fromIndex(int i) {
        if (i < 0) return null;
        return fromOrdinal(i + 1);
    }
    /**
     * @return the next stage in the GameStage sequence. If NOT_IN_GAME or DEATHMATCH, it will return null.
     */
    @Nullable
    public GameStage next() {
        if (this == NOT_IN_GAME) return null;
        
        return fromOrdinal(ordinal() + 1);
    }

    /* PROPERTIES */
    public BossBar.Color getBBColor() {
        return bbClr;
    }

    public Component getBBTitle() {
        return bbTitle;
    }

    public Duration getDuration() {
        return dur;
    }
    
    public boolean isInstant() {
        return dur.isZero();
    }

    public double getWBDiameter() {
        return wbSize;
    }

    public double getWBRadius() {
        return getWBDiameter() / 2;
    }
    
    /**
     * Updates worlds to align with the stage's world border size.
     * @param worlds
     */
    public void applyWBSize(World... worlds) {
        if (this == NOT_IN_GAME) return;
        for (World w : worlds) {
            if (isWBInstant) {
                w.getWorldBorder().setSize(wbSize);
            } else {
                w.getWorldBorder().setSize(wbSize, dur.toSeconds());
            }
        }
    }

    /**
     * @return the next stage that has a non-zero duration; returns null if executed on {@link #NOT_IN_GAME} or {@link #DEATHMATCH}
     */
    @Nullable
    private GameStage nextGradualStage() {
        if (this == NOT_IN_GAME) return null;

        return Arrays.stream(values())
                     .skip(ordinal() + 1)
                     .filter(gs -> !gs.isInstant())
                     .findFirst()
                     .orElse(null);
    }

    /**
     * Last stage before DM that has a non-zero duration; returns null if every stage has a 0 duration (Should not be possible normally).
     */
    @Nullable
    private static GameStage lastGradualStage() {
        GameStage[] values = values();

        // iter every stage in reverse EXCEPT NOT_IN_GAME and DEATHMATCH
        for (int i = values.length - 2; i >= 1; i--) {
            GameStage gs = values[i];
            if (!gs.isInstant()) {
                return gs;
            };
        }
        return null;
    }


    /**
     * Gives a builder that starts a warning message by the game.
     * [warn prefix] [message]
     * <p>
     * {@literal <!> World border is shrinking in ---}
     */
    private static TextComponent.Builder getMessageBuilder() {
        return Component.text()
                        .append(
                            Component.text("<", TextColor.color(0xCFCFFF), BOLD),
                            Component.translatable("uhc.baz9k.xyz.chat.name", TextColor.color(0xA679FE), BOLD),
                            Component.text("> ", TextColor.color(0xCFCFFF), BOLD)
                        );
    }

    /**
     * Sends the linked message in chat.
     */
    public void sendMessage() {
        if (this == NOT_IN_GAME) return;
        if (this == DEATHMATCH) {
            Bukkit.getServer().sendMessage(getMessageBuilder().append(baseChatMsg));
            return;
        }
        /**
         * 
         * Gradual = duration > 0
         * Instant = duration = 0
         * 
         * 1. If the next gradual stage is a world border moving stage (WB_1, WB_2),
         *   a. If it is instant, warn that WB immediately shrinks next stage
         *   b. If it is not instant, warn that WB begins shrinking next stage 
         * 
         * 2. If the current stage is a world border moving stage,
         *   a. If it is instant, display that WB just shrunk.
         *   b. If it is not instant, display that WB is shrinking.
         * 
         * 3. If this is the last gradual stage, additionally display dmWarn.
         * 
         */

        TranslatableComponent situation = Component.translatable("uhc.baz9k.xyz.chat.warning.no_warn", bodyStyle);
        Component subject = Component.translatable(this == WB_STILL ? "uhc.baz9k.xyz.chat.wb.name" : "uhc.baz9k.xyz.chat.wb.pronoun");

        GameStage nextGrad = nextGradualStage();
        if (!nextGrad.isWBInstant) {
            situation = Component.translatable(nextGrad.isInstant() ? "uhc.baz9k.xyz.chat.warning.wb_will_instant_shrink" : "uhc.baz9k.xyz.chat.warning.wb_will_shrink");
        }
        if (!isWBInstant) {
            situation = Component.translatable(isInstant() ? "uhc.baz9k.xyz.chat.warning.wb_just_instant_shrink" : "uhc.baz9k.xyz.chat.warning.wb_just_shrink");
        }
        
        TextComponent.Builder s = getMessageBuilder();
        
        s.append(situation.args(baseChatMsg, subject, Component.text(wbSize / 2), Component.text(getWordTimeString(dur))));
        if (this == lastGradualStage()) {
            s.append(Component.translatable("uhc.baz9k.xyz.chat.warning.dm_warn", bodyStyle).args(Component.text(getWordTimeString(dur))));
        }
        
        Bukkit.getServer().sendMessage(s);
    }
}
