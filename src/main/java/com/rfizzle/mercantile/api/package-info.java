/**
 * Mercantile's public API — the only package other mods should reference.
 *
 * <p>Everything in this package is <strong>stable</strong> per the
 * <a href="https://github.com/rfizzle/concord/blob/master/API-STANDARD.md">Concord
 * API Standard v1</a>: signatures here survive minor and patch releases, and a
 * breaking change requires a major version bump plus a changelog entry naming
 * the broken signature. Everything <em>outside</em> this package — attachments,
 * managers, mixins — is internal and may change without notice in any release.
 *
 * <p>Stability note: the standard calls for {@code @ApiStatus.Stable}, but no
 * such annotation exists in {@code org.jetbrains.annotations} (stability is its
 * implicit default; {@code ApiStatus} only marks deviations such as
 * {@code Internal} or {@code Experimental}). Stability is therefore declared
 * here and on each class's javadoc instead.
 *
 * <p>Consume as a soft dependency only: compile against the mod with
 * {@code modCompileOnly} and guard every call site with
 * {@code FabricLoader.getInstance().isModLoaded("mercantile")}. See the
 * "For Mod Developers" section of the README for worked examples.
 */
package com.rfizzle.mercantile.api;
