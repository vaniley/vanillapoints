/**
 * Synchronous, cancellable notifications fired before point storage changes.
 *
 * <p>On Paper these run on the main tick thread. On Folia they run on the calling
 * region or global tick thread, not an asynchronous worker; {@code isAsynchronous()}
 * remains {@code false}. Point snapshots contain immutable coordinates/metadata,
 * not live entities or blocks. The caller's thread does not necessarily own the
 * region containing those coordinates.</p>
 *
 * <p>VanillaPoints serializes its mutation notifications and storage changes.
 * Listeners may read the API and cancel the event, but must not block waiting for
 * another region or task, and must not perform nested mutations (these are rejected).
 * Use the appropriate scheduler for later region/entity work. Reload requests are
 * queued and do not synchronously replace the service during a notification.</p>
 */
package dev.vaniley.vanillapoints.api.event;
