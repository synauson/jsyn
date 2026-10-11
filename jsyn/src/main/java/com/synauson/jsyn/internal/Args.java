package com.synauson.jsyn.internal;

import com.synauson.jsyn.exception.InvalidArgumentException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Argument checks for the public API. Every rejected argument surfaces as an
 * {@link InvalidArgumentException}, the same type the native layer throws for invalid
 * arguments, so callers never see a bare {@link NullPointerException} from jsyn.
 *
 * @since 1.2.0
 */
public final class Args {
    private Args() {}

    /**
     * Return {@code value}, or throw if it is null.
     *
     * @param value the argument to check
     * @param name  the argument's name, used in the message
     * @param <T>   the argument's type
     * @return {@code value}, never null
     * @throws InvalidArgumentException with message {@code "<name> must not be null"} if
     *         {@code value} is null
     */
    public static <T> T notNull(@Nullable T value, String name) {
        if (value == null) {
            throw new InvalidArgumentException(name + " must not be null");
        }
        return value;
    }

    /**
     * Start collecting the required fields of a builder, so that {@link Required#validate()}
     * can report every missing one at once.
     *
     * @param typeName the simple name of the type being built, used in the message
     * @return a fresh collector
     */
    public static Required required(String typeName) {
        return new Required(typeName);
    }

    /**
     * Collects missing required fields for one {@code build()} call.
     *
     * @since 1.2.0
     */
    public static final class Required {
        private final String typeName;
        private final List<String> missing = new ArrayList<>();

        private Required(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Record {@code name} as missing if {@code value} is null.
         *
         * @param name  the field's name
         * @param value the field's current value
         * @return this collector
         */
        public Required field(String name, @Nullable Object value) {
            if (value == null) {
                missing.add(name);
            }
            return this;
        }

        /**
         * Throw if any recorded field was missing.
         *
         * @throws InvalidArgumentException with message {@code "<Type> requires a, b"} naming
         *         every missing field in the order they were recorded
         */
        public void validate() {
            if (!missing.isEmpty()) {
                throw new InvalidArgumentException(
                    typeName + " requires " + String.join(", ", missing));
            }
        }
    }
}
