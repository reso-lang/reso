package com.reso.compiler.symbols;

import static com.reso.compiler.symbols.Visibility.FILEPRIVATE;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents an access context in the visibility hierarchy.
 * Tracks the current context for visibility checking.
 */
public class AccessContext {
    private final Visibility level;
    private final AccessContext parent;
    private final String fileIdentifier;

    /**
     * Creates a new access context.
     *
     * @param level          The visibility level of this context
     * @param fileIdentifier The source file name for this context
     * @param parent         The parent context (null for global)
     */
    public AccessContext(@Nonnull Visibility level, @Nullable String fileIdentifier,
                         @Nullable AccessContext parent) {
        this.level = Objects.requireNonNull(level, "Level cannot be null");
        this.fileIdentifier = fileIdentifier;
        this.parent = parent;
    }

    /**
     * Creates the global context.
     */
    public static AccessContext createGlobal() {
        return new AccessContext(Visibility.GLOBAL, null, null);
    }

    /**
     * Creates a resource context.
     */
    public static AccessContext createFilePrivate(@Nonnull String fileIdentifier,
                                                  @Nonnull AccessContext parent) {
        return new AccessContext(FILEPRIVATE, fileIdentifier, parent);
    }

    @Nonnull
    public Visibility getLevel() {
        return level;
    }

    @Nullable
    public String getFileIdentifier() {
        return fileIdentifier;
    }

    @Nullable
    public AccessContext getParent() {
        return parent;
    }

    /**
     * Checks if this context can access a symbol with the given visibility.
     *
     * @param targetVisibility The visibility of the target symbol
     * @param fileIdentifier   The source file name of the target symbol
     * @return true if access is allowed
     */
    public boolean canAccess(@Nonnull Visibility targetVisibility,
                             @Nullable String fileIdentifier) {
        Objects.requireNonNull(targetVisibility, "Target visibility cannot be null");

        return switch (targetVisibility) {
            case GLOBAL -> true; // Global symbols are accessible from anywhere

            case FILEPRIVATE -> isInSameFile(fileIdentifier);
        };
    }

    private boolean isInSameFile(@Nullable String fileIdentifier) {
        return this.level == FILEPRIVATE && Objects.equals(this.fileIdentifier, fileIdentifier);
    }

    @Override
    public String toString() {
        return "AccessContext{"
            + "level=" + level
            + ", parent=" + parent
            + ", fileIdentifier='" + fileIdentifier + '\''
            + '}';
    }
}