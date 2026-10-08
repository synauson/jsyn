package com.synauson.jsyn;

import com.google.gson.Gson;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What this runtime may use right now, and how much of it is in use: the license,
 * its usage limits and current usage, which AI capabilities it includes, and the state
 * of each model.
 *
 * <p>Deserialized from the JSON returned by {@code NativeBridge.capabilities}; field
 * names match the Rust {@code CapabilitiesReport} (camelCase).
 *
 * @since 1.4.0
 */
public final class Capabilities {
    /** The license and its state. */
    public final LicenseInfo license;

    /**
     * What the limits count over: {@code "this instance"} until usage is aggregated
     * across every instance of a license.
     */
    public final String limitsScope;

    /** Fraction above each limit still admitted, reported as overage (0.25 = 125%). */
    public final double overdraft;

    /** Concurrent conferences. */
    public final Usage conferences;

    /** Concurrent conferences with at least one AI capability in use. */
    public final Usage aiConferences;

    /** Each AI capability, whether the license includes it, and its stream usage. */
    public final List<CapabilityInfo> capabilities;

    /** Each model this runtime knows, and whether it is ready to use. */
    public final List<ModelInfo> models;

    private Capabilities() {
        this.license = null;
        this.limitsScope = null;
        this.overdraft = 0;
        this.conferences = null;
        this.aiConferences = null;
        this.capabilities = null;
        this.models = null;
    }

    /**
     * Deserialize from the JSON string returned by the native bridge.
     *
     * @param json JSON previously returned by {@code NativeBridge.capabilities}
     * @return the deserialized report
     */
    public static Capabilities fromJson(String json) {
        return new Gson().fromJson(json, Capabilities.class);
    }

    /**
     * The license this runtime runs under.
     *
     * @since 1.4.0
     */
    public static final class LicenseInfo {
        /**
         * {@code "licensed"}, {@code "free-tier-floor"} (no current license file, for
         * example while the licensing server is unreachable), or {@code "rejected"}.
         */
        public final String state;
        /** One line describing the license and its state. */
        public final String description;
        /** Licensing server id of the license, when licensed. */
        public final @Nullable String licenseId;
        /** Name of the license, when licensed. */
        public final @Nullable String name;
        /** Where the license file in use came from: {@code "server"}, {@code "cache"} or {@code "provided"}. */
        public final @Nullable String source;
        /** RFC 3339; when the license file in use stops being valid offline. */
        public final @Nullable String fileExpiry;
        /** RFC 3339; the license's own expiry, if it has one. */
        public final @Nullable String licenseExpiry;

        private LicenseInfo() {
            this.state = null;
            this.description = null;
            this.licenseId = null;
            this.name = null;
            this.source = null;
            this.fileExpiry = null;
            this.licenseExpiry = null;
        }
    }

    /**
     * A limit and how much of it is in use.
     *
     * @since 1.4.0
     */
    public static final class Usage {
        /** The limit; {@code null} is unlimited. */
        public final @Nullable Integer limit;
        /** Units currently in use. */
        public final int inUse;

        private Usage() {
            this.limit = null;
            this.inUse = 0;
        }
    }

    /**
     * One AI capability.
     *
     * @since 1.4.0
     */
    public static final class CapabilityInfo {
        /** Entitlement code, e.g. {@code FEATURE_TURN_DETECTION}. */
        public final String code;
        /** Whether the license includes it. */
        public final boolean entitled;
        /** Concurrent streams using it (one per participant it runs on). */
        public final Usage streams;

        private CapabilityInfo() {
            this.code = null;
            this.entitled = false;
            this.streams = null;
        }
    }

    /**
     * One model.
     *
     * @since 1.4.0
     */
    public static final class ModelInfo {
        /** Model id, e.g. {@code sentito-1}. */
        public final String id;
        /** Directory name of this version in the model store. */
        public final String version;
        /** Published release version. */
        public final String release;
        /** {@code "ready"}, {@code "missing"}, {@code "invalid"} or {@code "not-entitled"}. */
        public final String state;
        /** Why, when not ready. */
        public final @Nullable String detail;

        private ModelInfo() {
            this.id = null;
            this.version = null;
            this.release = null;
            this.state = null;
            this.detail = null;
        }
    }
}
