package xyz.baz9k.UHCGame.menu;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import xyz.baz9k.UHCGame.util.stack.DynItemProperties;
import xyz.baz9k.UHCGame.util.stack.DynItemProperties.ExtraLore;

import org.jetbrains.annotations.NotNull;

import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

public final class OptionValuedNode extends ValuedNode {

    private static final Key OPT_DESC_ID_FORMAT = new Key("%s.options");

    /**
     * @param parent Parent node
     * @param slot Slot of this node in parent's inventory
     * @param nodeName Name of the node
     * @param optMaterials Materials for the options supported
     */
    public OptionValuedNode(BranchNode parent, int slot, String nodeName, DynItemProperties<Object> props, Material... optMaterials) {
        super(parent, slot, nodeName, props.mat(v -> optMaterials[(int) v]), ValuedNode.Type.OPTION, i -> (int) i % optMaterials.length);
        props.formatArg(v -> this.optDesc((int) v))
            .extraLore(v -> {
                int current = (int) v;

                var extraLore = new ArrayList<Component>();
                for (int i = 0; i < optMaterials.length; i++) {
                    var clr = i == current ? NamedTextColor.GREEN : NamedTextColor.RED;
                    extraLore.add(Component.text(optDesc(i), noDeco(clr)));

                }
                return new ExtraLore(extraLore);
            });
    }

    @Override
    public boolean click(@NotNull Player p) {
        int currIndex = cfg.getInt(cfgKey());
        this.set(currIndex + 1);
        return true;
    }

    /**
     * @param i
     * @return the description for option i
     */
    public String optDesc(int i) {
        var langYaml = plugin.getLangManager().langYaml();
        Key k = OPT_DESC_ID_FORMAT.sub(langKey());
        var optDescs = langYaml.getStringList(k.key());
        if (optDescs.size() == 0) {
            return String.format(k.key() + "[%s]", i);
        } else {
            return optDescs.get(i);
        }
    }
}
