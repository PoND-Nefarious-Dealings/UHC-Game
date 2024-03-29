package xyz.baz9k.UHCGame.menu;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import xyz.baz9k.UHCGame.util.Path;
import xyz.baz9k.UHCGame.util.stack.DynItemProperties;
import xyz.baz9k.UHCGame.util.stack.DynItemProperties.ExtraLore;

import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

import java.util.Objects;
import java.util.function.UnaryOperator;

public class ValuedNode extends Node implements ValueHolder {
    protected final Type type;
    protected UnaryOperator<Number> restrict = UnaryOperator.identity();
    private Object prevValue;
    static BranchNode cfgRoot;

    /**
     * Enum of the supported types for a {@link ValuedNode}.
     */
    public enum Type {
        INTEGER (true), 
        DOUBLE  (true), 
        STRING  (false), 
        BOOLEAN (false), 
        OPTION  (true);

        private final boolean isNumeric;
        Type(boolean isNumeric) {
            this.isNumeric = isNumeric;
        }

        private Type requireNumeric() {
            if (isNumeric) return this;
            throw new Key("err.menu.not_numeric_type").transErr(IllegalArgumentException.class, this);
        }
    }

    /**
     * @param parent Parent node
     * @param slot Slot of this node in parent's inventory
     * @param nodeName Name of the node
     * @param props {@link ItemProperties}
     * <p>
     * If format strings are included in the item's description (%s, %.1f, etc.), 
     * those will be substituted with the config value.
     * @param type Type of data this value stores
     * <p>
     * WITH A RESTRICTING FUNCTION, THE TYPE MUST BE NUMERIC.
     * @param restrict This function maps invalid numeric values to the correct values.
     */
    public ValuedNode(BranchNode parent, int slot, String nodeName, DynItemProperties<Object> props, Type type, UnaryOperator<Number> restrict) {
        this(parent, slot, nodeName, props, type.requireNumeric());
        
        this.restrict = restrict;
    }
    /**
     * @param parent Parent node
     * @param slot Slot of this node in parent's inventory
     * @param nodeName Name of the node
     * @param props {@link ItemProperties}
     * <p>
     * If format strings are included in the item's description (%s, %.1f, etc.), 
     * those will be substituted with the config value.
     * @param type Type of data this value stores
     */
    public ValuedNode(BranchNode parent, int slot, String nodeName, DynItemProperties<Object> props, Type type) {
        super(parent, slot, nodeName, props);
        this.type = type;
        
        props.useObject(this::get);
        if (type == Type.BOOLEAN) {
            props.enchGlint(o -> (boolean) o)
                .extraLore(ExtraLore.fromBool());
        }
    }

    @Override
    public String cfgKey() {
        Objects.requireNonNull(cfgRoot, "Config root not yet declared, cannot initialize valued nodes");
        return pathRelativeTo(cfgRoot)
            .map(Path::toString)
            .get();
    }

    @Override
    public boolean click(@NotNull Player p) {
        switch (type) {
            case INTEGER, DOUBLE, STRING -> new ValueRequest(plugin, p, this);
            case BOOLEAN -> this.set(!cfg.getBoolean(cfgKey()));
            // case OPTION -> see OptionValuedNode#click
            default -> throw new Key("err.menu.needs_impl").transErr(IllegalArgumentException.class, type);
        }
        return true;
    }

    /**
     * Undoes a value change if the value change brings an invalid config state.
     */
    public void undo() {
        set(prevValue);
    }

    /**
     * Sets the current object for the config key corresponding to this node.
     * @param value value to set key to
     */
    @Override
    public void set(Object value) {
        prevValue = cfg.get(cfgKey());
        if (type.isNumeric) value = restrict.apply((Number) value);
        ValueHolder.super.set(value);
    }
}
