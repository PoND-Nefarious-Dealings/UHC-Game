package xyz.baz9k.UHCGame.config;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

import static xyz.baz9k.UHCGame.util.Utils.*;

/**
 * {@link ItemStack} modified to be more simple for {@link Node} use.
 * 
 * The material, display name, description, and extra lore are modifiable through this class directly.
 * The lore consists of a formatted description with the extra lore.
 */
public class NodeItemStack extends ItemStack {
    /**
     * ID used for name & desc
     */
    private final String id;

    /**
     * Object used to format desc
     */
    private Object formatObject;

    /**
     * Additional lore below the description, which can be translated with a key.
     */
    private TExtraLore extraLoreInfo;
    /**
     * Additional lore below the description, which is a component and does not use a key.
     */
    private List<Component> extraLore = List.of();

    /**
     * Record that provides additional properties about the ItemStack, particularly:
     * <p> - fn to get material
     * <p> - name style
     * <p> - object mapping in format string
     */
    private final Info info;

    // TEXT STYLES
    /**
     * This text style (color & formatting) will be used in the description by default
     */
    public static final Style DEFAULT_NAME_STYLE = noDeco(null);
    /**
     * This text style (color & formatting) will be used in the description by default
     */
    public static final Style DEFAULT_DESC_STYLE = noDeco(NamedTextColor.GRAY);
    //
    
    private static final String NAME_ID_FORMAT = "xyz.baz9k.uhc.config.inv.%s.name";
    private static final String DESC_ID_FORMAT = "xyz.baz9k.uhc.config.inv.%s.desc";

    /**
     * Provides info that must be provided and isn't generated by the {@link Node} classes
     */
    public static record Info(Function<Object, Material> matGet, Style nameStyle, UnaryOperator<Object> mapper) {
        public Info(Function<Object, Material> mat) { this(mat, DEFAULT_NAME_STYLE, UnaryOperator.identity()); }
        public Info(Function<Object, Material> mat, TextColor clr) { this(mat, noDeco(clr), UnaryOperator.identity()); }
        public Info(Function<Object, Material> mat, Style nameStyle) { this(mat, nameStyle, UnaryOperator.identity()); }
        public Info(Function<Object, Material> mat, UnaryOperator<Object> mapper) { this(mat, DEFAULT_NAME_STYLE, mapper); }

        public Info(Material mat) { this(v -> mat, DEFAULT_NAME_STYLE, UnaryOperator.identity()); }
        public Info(Material mat, TextColor clr) { this(v -> mat, noDeco(clr), UnaryOperator.identity()); }
        public Info(Material mat, Style nameStyle) { this(v -> mat, nameStyle, UnaryOperator.identity()); }
        public Info(Material mat, UnaryOperator<Object> mapper) { this(v -> mat, DEFAULT_NAME_STYLE, mapper); }
    
        public Info withMatGet(Function<Object, Material> matGet) {
            return new Info(matGet, nameStyle(), mapper());
        }
        public Material mat(String id) {
            return matGet.apply(Node.cfg.get(id));
        }
    }

    /**
     * Extra lore based off key + arguments
     */
    private static record TExtraLore(String id, Object... args) {
        public List<Component> component() {
            Object[] args = Arrays.stream(this.args)
            .map(o -> {
                if (o instanceof Component c) return render(c);
                return o;
            })
            .toArray();

            return splitLines(render(trans(id, args).style(DEFAULT_DESC_STYLE)));
        }
    }

    public NodeItemStack(String id, Info info) {
        super(info.mat(id));

        this.id = id;
        this.info = info;

        editMeta(m -> { m.addItemFlags(ItemFlag.HIDE_ENCHANTS); });
        updateAll();

    }

    /**
     * Gets the formatted description of the item.
     * @return the description
     */
    public List<Component> desc() {
        return descOf(id).stream()
        .map(c -> {
            if (c instanceof TextComponent tc) {
                String content = tc.content();
                return tc.content(MessageFormat.format(content, formatObject));
            } else return c;
        })
        .toList();
    }
    /**
     * Sets the object for the formatted description of the item. This changes the lore.
     * @param o Object to use for formatting the description
     */
    public void desc(Object o) {
        formatObject = info.mapper.apply(o);
        updateAll();
    }

    /**
     * Gets the extra lore of the item.
     * @return the extra lore
     */
    public List<Component> extraLore() {
        if (extraLoreInfo != null) return extraLoreInfo.component();
        return Collections.unmodifiableList(extraLore);
    }

    /**
     * Sets the extra lore of the item
     * @param loreKey the extra lore
     * @param args arguments
     */
    public void extraLore(String key, Object... args) {
        extraLoreInfo = new TExtraLore(key, args);
        updateAll();
    }
    /**
     * Sets the extra lore of the item
     * @param lore the extra lore
     */
    public void extraLore(Component lore) {
        extraLore = List.of(lore);
        updateAll();
    }
    /**
     * Sets the extra lore of the item
     * @param lore the extra lore
     */
    public void extraLore(List<Component> lore) {
        extraLore = List.copyOf(lore);
        updateAll();
    }

    private void updateLore() {
        List<Component> lore = new ArrayList<>(desc());
        
        extraLore = extraLore();
        if (extraLore == null) extraLore = List.of();
        if (extraLore.size() > 0) {
            lore.add(Component.empty());
            lore.addAll(extraLore);
        }

        lore(lore);
    }

    /**
     * Updates all description data to current locale.
     */
    public NodeItemStack updateAll() {
        setType(info.mat(id));
        editMeta(m -> { m.displayName(nameOf(id).style(info.nameStyle)); });
        updateLore();

        return this;
    }

    /**
     * Takes a function that modifies the meta and updates the item with the modified meta.
     * @param mf
     */
    public void editMeta(Consumer<ItemMeta> mf) {
        ItemMeta m = getItemMeta();
        mf.accept(m);
        setItemMeta(m);
    }

    /**
     * Splits a component into a list consisting of lines
     * @param comp Component to split into
     * <p> Non-text components cannot be split, and will be returned in a list.
     * @return list
     */
    private static List<Component> splitLines(Component comp) {
        if (!(comp instanceof TextComponent text)) return List.of(comp); // "fuck it i dunno"
        List<TextComponent> components = new ArrayList<>();
        components.add(text);
        for (Component child : text.children()) {
            if (child instanceof TextComponent tc) components.add(tc);
            else components.add(Component.text(child.toString(), child.style())); // "fuck it i dunno"
        }

        List<Component> lines = new ArrayList<>();

        for (TextComponent c : components) {
            var content = new ArrayList<>(Arrays.asList(c.content().split("\n")));

            if (lines.size() > 0) {
                int i = lines.size() - 1;
                Component segment = Component.text(content.remove(0), c.style());
                lines.set(i, lines.get(i).append(segment));
            }

            for (String cline : content) {
                lines.add(Component.text(cline, c.style()));
            }
        }

        return lines;
    }

    /**
     * Gets a rendered name by translation key
     * <p> Rendered components are text components that are already translated.
     * @param id translation key
     * @return rendered Component
     */
    public static Component nameOf(String id) {
        return nameOf(id, DEFAULT_NAME_STYLE);
    }
    /**
     * Gets a rendered name by translation key
     * <p> Rendered components are text components that are already translated.
     * @param id translation key
     * @return rendered Component
     */
    public static Component nameOf(String id, Style s) {
        String key = String.format(NAME_ID_FORMAT, id);
        return render(trans(key));
    }

    /**
     * Gets a rendered description by translation key
     * <p> Rendered components are text components that are already translated.
     * @param id translation key
     * @return lines of rendered Component
     */
    public static List<Component> descOf(String id) {
        return descOf(id, DEFAULT_DESC_STYLE);
    }
    /**
     * Gets a rendered description by translation key
     * <p> Rendered components are text components that are already translated.
     * @param id translation key
     * @return lines of rendered Component
     */
    public static List<Component> descOf(String id, Style s) {
        String key = String.format(DESC_ID_FORMAT, id);

        Component rendered = render(trans(key).style(s));
        if (componentString(rendered).equals("")) return List.of();

        return splitLines(rendered);
    }
}
