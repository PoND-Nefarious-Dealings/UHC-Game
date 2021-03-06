package xyz.baz9k.UHCGame.config;

import java.util.ArrayList;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import static xyz.baz9k.UHCGame.util.Utils.*;

public class OptionValuedNode extends ValuedNode {

    private OptionData[] optData;

    /**
     * @param parent Parent node
     * @param slot lot of this node in parent's inventory
     * @param nodeName Node name, which is used to determine the ID
     * @param optData An array of options that this node supports 
     */
    public OptionValuedNode(BranchNode parent, int slot, String nodeName, NodeItemStack.Info info, OptionData... optData) {
        super(parent, slot, nodeName, info.withMatGet(v -> optData[(int) v].material()), ValuedNode.Type.OPTION);
        this.optData = optData;
        updateItemStack();
    }

    @Override
    public void click(Player p) {
        int currIndex = cfg.getInt(id());
        this.set((currIndex + 1) % optData.length);
    }

    public void updateItemStack() {
        if (optData == null) return;
        int ind = cfg.getInt(id()) % optData.length;
        OptionData dat = optData[ind];
        itemStack.desc(dat.name());

        itemStack.setType(dat.material());
        var extraLore = new ArrayList<Component>();
        for (int i = 0; i < optData.length; i++) {
            TextColor clr = i == ind ? NamedTextColor.GREEN : NamedTextColor.RED;
            extraLore.add(Component.text(optData[i].name(), noDeco(clr)));

        }
        itemStack.extraLore(extraLore);

        // since updating the item does not update it in the inventory, parent has to
        parent.updateSlot(parentSlot);
    }
    
}
