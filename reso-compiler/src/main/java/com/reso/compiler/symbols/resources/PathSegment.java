package com.reso.compiler.symbols.resources;

import com.reso.compiler.types.ResoType;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a segment in a resource path.
 * Can be either a simple identifier or an indexer.
 */
public class PathSegment {
    private final String name;
    private final ResoType indexerType;
    private final boolean isIndexer;

    /**
     * Creates a path segment for a simple identifier.
     *
     * @param name The identifier name
     */
    public PathSegment(@Nonnull String name) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.indexerType = null;
        this.isIndexer = false;
    }

    /**
     * Creates a path segment for an indexer.
     *
     * @param name        The indexer parameter name
     * @param indexerType The indexer parameter type
     */
    public PathSegment(@Nonnull String name, @Nonnull ResoType indexerType) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.indexerType = Objects.requireNonNull(indexerType, "Indexer type cannot be null");
        this.isIndexer = true;
    }

    /**
     * Gets the name of this path segment.
     *
     * @return The name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Gets the display name of this path segment.
     *
     * @return The display name
     */
    @Nonnull
    public String getDisplayName() {
        if (isIndexer) {
            return "{Indexer}";
        } else {
            return name;
        }
    }

    /**
     * Gets the indexer type if this is an indexer segment.
     *
     * @return The indexer type, or null if this is not an indexer
     */
    @Nullable
    public ResoType getIndexerType() {
        return indexerType;
    }

    /**
     * Checks if this is an indexer segment.
     *
     * @return true if this is an indexer segment
     */
    public boolean isIndexer() {
        return isIndexer;
    }

    /**
     * Returns a string representation of this path segment.
     *
     * @return String representation
     */
    @Override
    public String toString() {
        if (isIndexer) {
            return "{" + indexerType.getName() + "}";
        } else {
            return name;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        PathSegment that = (PathSegment) obj;
        return isIndexer == that.isIndexer
            && name.equals(that.name)
            && Objects.equals(indexerType, that.indexerType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, indexerType, isIndexer);
    }
}