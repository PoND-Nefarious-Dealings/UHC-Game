package xyz.baz9k.UHCGame.util;

import net.md_5.bungee.api.ChatColor;

import java.awt.*;

public class TeamColors {
    private static final int numTeamColors = 64;
    private static final int[] teamColorCodes = {
        0xc04040, 0x4040c0, 0x40c040, 0xc0c040, 0xc06b40, 0x6b40c0, 0x40c0c0, 0x6bc040,
        0xc09640, 0x9640c0, 0x4096c0, 0x40c096, 0x96c040, 0xc040c0, 0x406bc0, 0x40c06b,
        0xc04096, 0xc06bc0, 0x6bc06b, 0xc0966b, 0xc0406b, 0xc06b96, 0x6b96c0, 0x96c06b,
        0xc06b6b, 0x964040, 0x6b6bc0, 0x6bc0c0, 0xc0c06b, 0x966b40, 0x966bc0, 0x6bc096,
        0x969640, 0x406b40, 0x404096, 0x40966b, 0x6b9640, 0x406b6b, 0x96406b, 0x406b96,
        0x409640, 0x40406b, 0x6b4040, 0x964096, 0x409696, 0x6b406b, 0x6b6b40, 0x6b4096,
        0xc09696, 0x404040, 0x966b6b, 0x96c0c0, 0xc0c096, 0x6b6b6b, 0x6b9696, 0xc096c0,
        0x96c096, 0x969696, 0x6b6b96, 0x6b966b, 0x9696c0, 0xc0c0c0, 0x966b96, 0x96966b
    };

    private static Color[] teamColors = new Color[numTeamColors];

    static {
        for (int i = 0; i < numTeamColors; i++) {
            teamColors[i] = new Color(teamColorCodes[i]);
        }
    }

    public static Color getTeamColor(int teamIndex) {
        if (teamIndex < 0) throw new IllegalArgumentException("Team index must be positive.");
        if (teamIndex > numTeamColors) throw new IllegalArgumentException("Team index must be less than number of predefined team colors (" + numTeamColors + ")");
        if (teamIndex == 0) {
            return new Color(85, 255, 255);
        }
        return teamColors[teamIndex - 1];
    }

    public static ChatColor getTeamChatColor(int teamIndex) {
        return ChatColor.of(getTeamColor(teamIndex));
    }

    public static int getNumTeamColors() {
        return numTeamColors;
    }

    public static String getTeamPrefix(int teamIndex) {
        ColoredStringBuilder cs = new ColoredStringBuilder();
        if (teamIndex == 0) {
            cs.append("[S]",ChatColor.AQUA,ChatColor.ITALIC);
        } else {
            cs.append("[" + teamIndex + "]",getTeamChatColor(teamIndex),ChatColor.BOLD);
        }
        return cs.toString();

    }

    public static String getTeamPrefixWithSpace(int teamIndex) {
        return getTeamPrefix(teamIndex) + " " + ChatColor.RESET;
    }
}
