package xyz.baz9k.UHCGame.menu;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.*;

import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;

import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

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
    private final String langKey;

    /**
     * Properties to generate the item stack
     */
    private final ItemProperties props;

    // TEXT STYLES
    /**
     * This text style (color & formatting) will be used in the name by default
     */
    public static final Style DEFAULT_NAME_STYLE = noDeco(null);
    /**
     * This text style (color & formatting) will be used in the description by default
     */
    public static final Style DEFAULT_DESC_STYLE = noDeco(NamedTextColor.GRAY);
    //
    
    private static final String NAME_ID_FORMAT = "xyz.baz9k.uhc.menu.inv.%s.name";
    private static final String DESC_ID_FORMAT = "xyz.baz9k.uhc.menu.inv.%s.desc";

    /**
     * Class provides additional properties about the ItemStack.<p>
     * This class stores functions to map "an object" to the specified property.
     * If this ItemStack is for a {@link ValuedNode}, the used object is the config value for the node.
     * Otherwise, there is no object by default (and all the functions can be treated as suppliers).
     * An object can be defined via the {@link ItemProperties#useObject} method.
     * <p>
     * The properties this class defines:
     * <p> - Material
     * <p> - Name style
     * <p> - Mapping of "object" to string that can be substituted into the description
     * <p> - Function to perform miscellaneous ItemMeta edits (ench hide flags, ench glint)
     * <p> - extra lore, which provides information other than the description of the node
     */
    public static class ItemProperties {
        private Supplier<Object> propsObjSupplier = () -> null;
        private Function<Object, Material> matGet = v -> Material.AIR;
        private Style nameStyle = DEFAULT_NAME_STYLE;
        private Function<Object, String> formatter = String::valueOf;
        private BiConsumer<Object, ItemMeta> miscMetaChanges = (v, m) -> {};
        private Function<Object, ExtraLore> elGet = v -> new ExtraLore();

        public ItemProperties() {}
        public ItemProperties(Function<Object, Material> mat) { mat(mat); }
        public ItemProperties(Material mat) { mat(mat); }

        public ItemProperties useObject(Supplier<Object> uo) {
            this.propsObjSupplier = uo;
            return this;
        }
        public ItemProperties mat(Function<Object, Material> mat) {
            this.matGet = mat;
            return this;
        }
        public ItemProperties style(Style s) {
            this.nameStyle = s;
            return this;
        }
        public ItemProperties formatter(Function<Object, String> formatter) {
            this.formatter = formatter;
            return this;
        }
        public ItemProperties metaChanges(BiConsumer<Object, ItemMeta> mc) {
            this.miscMetaChanges = mc;
            return this;
        }
        public ItemProperties extraLore(Function<Object, ExtraLore> el) {
            this.elGet = el;
            return this;
        }
        
        public ItemProperties mat(Material mat) { return mat(v -> mat); }
        public ItemProperties style(TextColor clr) { return style(noDeco(clr)); }
        

        private Material getMat() {
            return matGet.apply(propsObjSupplier.get());
        }
        private Style getStyle() {
            return nameStyle;
        }
        private String getFormattedDescObj() {
            return formatter.apply(propsObjSupplier.get());
        }
        private void editMeta(ItemMeta m) {
            miscMetaChanges.accept(propsObjSupplier.get(), m);
        }
        private ExtraLore getExtraLore() {
            return elGet.apply(propsObjSupplier.get());
        }
    }

    /**
     * Class that encapsulates extra lore. The extra lore can either be a component/list of lines or a translation key.
     */
    public static class ExtraLore { // extra lore can either be a translatable key or a list of components
        private List<Component> lore = null;

        private String tKey = null;
        private Object[] tArgs = null;

        public ExtraLore(Component... lore) { this.lore = List.of(Objects.requireNonNull(lore)); }
        public ExtraLore(List<Component> lore) { this.lore = List.copyOf(Objects.requireNonNull(lore)); }

        public ExtraLore(String key, Object... args) {
            this.tKey = Objects.requireNonNull(key);
            this.tArgs = Objects.requireNonNull(args);
        }

        /**
         * @return the extra lore in component form
         */
        public List<Component> component() {
            if (lore != null) {
                return List.copyOf(lore)
                    .stream()
                    .map(c -> c.hasStyling() ? c : c.style(DEFAULT_DESC_STYLE))
                    .toList();
            }

            Object[] args = Arrays.stream(tArgs)
                .map(o -> {
                    if (o instanceof Component c) return render(c);
                    return o;
                })
                .toArray();

            return splitLines(render(trans(tKey, args).style(DEFAULT_DESC_STYLE)));
        }

        /**
         * @return an extra lore that is either ACTIVE/INACTIVE based on if the provided object is true or false
         */
        public static Function<Object, ExtraLore> fromBool() {
            return o -> {
                var active = (boolean) o;

                // keeps the description untouched, adds Status: ACTIVE/INACTIVE below it
                TranslatableComponent status;
                if (active) {
                    status = trans("xyz.baz9k.uhc.menu.bool_valued.on").style(noDeco(NamedTextColor.GREEN));
                } else {
                    status = trans("xyz.baz9k.uhc.menu.bool_valued.off").style(noDeco(NamedTextColor.RED));
                }
                return new NodeItemStack.ExtraLore("xyz.baz9k.uhc.menu.bool_valued.status", status);
            };
        }
    }

    /**
     * @param langKey the node's lang key
     * @param props the node's item properties
     */
    public NodeItemStack(String langKey, ItemProperties props) {
        super(props.getMat());

        this.langKey = langKey;
        this.props = props;

        editMeta(m -> m.addItemFlags(ItemFlag.HIDE_ENCHANTS));
        updateAll();

    }

    /**
     * Gets the formatted description of the item.
     * @return the description
     */
    public List<Component> desc() {
        return descFromID(langKey).stream()
            .map(c -> {
                if (c instanceof TextComponent tc) {
                    String content = tc.content();
                    String fmtObj = props.getFormattedDescObj();
                    return tc.content(MessageFormat.format(content, fmtObj));
                } else return c;
            })
            .toList();
    }

    /**
     * Gets the extra lore of the item.
     * @return the extra lore
     */
    public List<Component> extraLore() {
        return props.getExtraLore().component();
    }

    private void updateLore() {
        List<Component> lore = new ArrayList<>(desc());
        
        var el = extraLore();
        if (el.size() > 0) {
            lore.add(Component.empty());
            lore.addAll(el);
        }

        lore(lore);
    }

    /**
     * Updates the ItemStack to be up-to-date with all properties.
     */
    public NodeItemStack updateAll() {
        setType(props.getMat());
        editMeta(m -> {
            m.displayName(nameFromID(langKey, props.getStyle()));
            props.editMeta(m);
        });
        updateLore();

        return this;
    }

    /**
     * Splits a component into a list consisting of lines
     * @param comp Component to split into
     * @apiNote Don't put non-text/-translatable components in.
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
     * Gets rendered name by translating the lang key<p>
     * Rendered components are text components that are already translated.
     * @param langKey lang key
     * @return rendered {@link Component}
     */
    public static Component nameFromID(String langKey) {
        return nameFromID(langKey, DEFAULT_NAME_STYLE);
    }

    /**
     * Gets rendered name by translating the lang key<p>
     * Rendered components are text components that are already translated.
     * @param langKey lang key
     * @param s Style of component
     * @return rendered {@link Component}
     */
    public static Component nameFromID(String langKey, Style s) {
        String key = String.format(NAME_ID_FORMAT, langKey);
        return render(trans(key).style(s));
    }

    /**
     * Gets a rendered description by translating the lang key<p>
     * Rendered components are text components that are already translated.
     * @param langKey lang key
     * @return lines of rendered Component
     */
    public static List<Component> descFromID(String langKey) {
        return descFromID(langKey, DEFAULT_DESC_STYLE);
    }
    /**
     * Gets a rendered description by translating the lang key<p>
     * Rendered components are text components that are already translated.
     * @param langKey lang key
     * @param s Style of component
     * @return lines of rendered Component
     */
    public static List<Component> descFromID(String langKey, Style s) {
        String fullLangKey = String.format(DESC_ID_FORMAT, langKey);

        Component rendered = render(trans(fullLangKey).style(s));
        if (renderString(rendered).equals("")) return List.of();

        return splitLines(rendered);
    }
}
