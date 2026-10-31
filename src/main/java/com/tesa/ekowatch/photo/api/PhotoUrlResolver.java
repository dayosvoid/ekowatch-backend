package com.tesa.ekowatch.photo.api;
/** Esther's trusted storage adapter resolves a sanitised reference; null means unavailable.
 * This is an internal integration hook, never a client-facing storage-key response.
 */
@FunctionalInterface
public interface PhotoUrlResolver {
    String resolve(String storageKey);
}
