package org.nypl.simplified.accounts.source.api

import org.nypl.simplified.accounts.api.AccountProviderDescriptionType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.observable.ObservableReadableType
import java.net.URI
import javax.annotation.concurrent.ThreadSafe

/**
 * The interface exposing a set of account providers.
 *
 * Implementations are required to be safe to access from multiple threads.
 */

@ThreadSafe
interface AccountProviderRegistryType {

  /**
   * A source of registry events.
   */

  val events: ObservableReadableType<AccountProviderRegistryEvent>

  /**
   * The default, guaranteed-to-exist account provider.
   */

  val defaultProvider: AccountProviderType

  /**
   * A read-only view of the currently resolved providers.
   */

  val resolvedProviders: Map<URI, AccountProviderType>

  /**
   * Refresh the available account providers from all sources.
   */

  fun refresh()

  /**
   * Return an immutable read-only of the account provider descriptions.
   *
   * Implementations are required to implicitly call [refresh] if the method has not previously
   * been called.
   */

  fun accountProviderDescriptions(): Map<URI, AccountProviderDescriptionType>

  /**
   * Find the account provider with the given `id`.
   *
   * Implementations are required to implicitly call [refresh] if the method has not previously
   * been called.
   */

  fun findAccountProviderDescription(id: URI): AccountProviderDescriptionType? =
    this.accountProviderDescriptions()[id]

  /**
   * Introduce the given account provider to the registry. If an existing, newer version of the
   * given account provider already exists in the registry, the newer version is returned.
   */

  fun updateProvider(accountProvider: AccountProviderType): AccountProviderType

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescription(description: AccountProviderDescriptionType): AccountProviderDescriptionType
}
