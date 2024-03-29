package xyz.baz9k.UHCGame.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Utility class for dealing with chained strings.
 * Example: "a.b.c.d.e.f.g.h"
 * <p> Nulls are ignored and filtered out of arguments.
 */
public final class Path implements Iterable<String> {

    private static class Builder {
        private List<String> path = new ArrayList<>();
        
        private Builder() {}
        private Builder(String[] p) {
            path.addAll(Arrays.asList(p));
        }

        Builder append(String[] p, boolean check) {
            for (String n : p) {
                if (!check || (n != null && !n.isBlank())) {
                    path.add(n);
                }
            }
            return this;
        }
        
        Builder append(Path p) { 
            if (p == null) return this;
            return append(p.path, false); 
        }
        Builder append(String p) {
            if (p == null) return this;
            return append(p.split("\\."), true); 
        }

        Builder appendAll(Path... p) { 
            if (p == null) return this;
            for (Path n : p) append(n);
            return this;
        }
        Builder appendAll(String... p) {
            if (p == null) return this;
            for (String n : p) append(n);
            return this;
        }

        Path build() { return new Path(path.toArray(String[]::new)); }
    }
    private final String[] path;

    /**
     * Should only be used if assured that every String passed is a valid node.
     * <p> Node: Non-null, non-blank, does not contain separators
     * @param path Array of nodes
     */
    private Path(String... path) {
        Objects.requireNonNull(path);
        this.path = path;
    }

    public String toString() {
        return String.join(".", path);
    }

    private Builder builder() {
        return new Builder(path);
    }

    /**
     * Construct a path from an array of strings
     * @param path path
     * @return new Path
     */
    public static Path of(String... path) {
        Objects.requireNonNull(path);

        return new Builder()
            .appendAll(path)
            .build();
    }

    /**
     * Join a set of paths
     * @param paths paths to join
     * @return the joined path
     */
    public static Path join(Path... paths) {
        Objects.requireNonNull(paths);

        return new Builder()
            .appendAll(paths)
            .build();
    }

    /**
     * Join strings together as if they were paths
     * @param paths Strings to join
     * @return the joined path returned as a string
     */
    public static String join(String... paths) {
        Objects.requireNonNull(paths);
        return Path.of(paths).toString();
    }

    /**
     * Create a new path with the nodes appended
     * @param nodes new nodes
     * @return new path
     */
    public Path append(String... nodes) {
        Objects.requireNonNull(nodes);
        
        return builder()
            .appendAll(nodes)
            .build();
    }

    /**
     * Create a new path with the paths appended
     * @param paths new paths
     * @return new path
     */
    public Path append(Path... paths) {
        Objects.requireNonNull(paths);

        return builder()
            .appendAll(paths)
            .build();
    }

    /**
     * Check if this path is empty
     * @return true/false
     */
    public boolean isRoot() {
        return path.length == 0;
    }

    /**
     * Traverses a tree with named nodes.
     * @param <N> Class of every node
     * @param <B> Class of every branch node
     * @param root The top node to start traversing from
     * @param getChild Method to get from a branch node to a child
     * @return the descendant at the path, if it exists
     */
    @SuppressWarnings("unchecked")
    public <N, B extends N> Optional<N> traverse(B root, BiFunction<B, String, Optional<N>> getChild) {
        if (isRoot()) return Optional.of(root);

        Class<B> branchClass = (Class<B>) root.getClass();
        B node = root;
        
        // grab child via getChild method.
        // if it's not a branch type, then it can't have another child, so return empty
        for (int i = 0; i < path.length - 1; i++) {
            String childName = path[i];
            Optional<N> child = getChild.apply(node, childName);
            
            if (child.isPresent()) {
                N c = child.get();

                if (branchClass.isInstance(c)) {
                    node = branchClass.cast(c);
                    continue;
                }
            }

            return Optional.empty();
        }

        // we found the last parent, so get the last parent's child
        String childName = path[path.length - 1];
        return getChild.apply(node, childName);
    }

    /**
     * Traverses a nested map.
     * <p><code> 
     *     new Path("a.b.c.d").traverse(map);
     * </code>
     * is equivalent to
     * <code>
     *     ((Map&lt;?,?&gt;) ((Map&lt;?,?&gt;) ((Map&lt;?,?&gt;) map.get("a")).get("b")).get("c")).get("d")
     * </code>
     * @param root The top map to start traversing from
     * @return the descendant at the path, if it exists
     */
    public Optional<Object> traverse(Map<?, ?> root) {
        return traverse(root, (m, s) -> Optional.ofNullable(m.get(s)));
    }

    /**
     * Find the path relative to a given parent.
     * @param parent the parent
     * @return the path relative to a given parent, or empty if the path isn't relative to this parent/
     */
    public Optional<Path> relativeTo(Path parent) {
        Objects.requireNonNull(parent);

        int mismatch = Arrays.mismatch(parent.path, path);
        if (mismatch == -1) return Optional.of(new Path());
        if (mismatch == parent.path.length) {
            String[] p = Arrays.copyOfRange(path, mismatch, path.length);
            return Optional.of(new Path(p));
        }
        return Optional.empty();
    }

    // pregenerated
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(path);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Path other = (Path) obj;
        if (!Arrays.equals(path, other.path))
            return false;
        return true;
    }
    //
    
    public Stream<String> stream() {
        return Arrays.stream(path);
    }

    @Override
    public Iterator<String> iterator() {
        return Arrays.asList(path).iterator();
    }
}
