package com.slicelink.urls;

/** Lifecycle status of a shortened URL record. */
public enum UrlStatus {
    /** Active — the short code resolves to the original URL. */
    ACTIVE,
    /** Disabled by the owner or an administrator. */
    DISABLED
}
