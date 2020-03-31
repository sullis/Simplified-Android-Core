package org.nypl.simplified.accounts.registry

import android.content.Context
import com.google.common.base.Preconditions
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountProviderDescriptionType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.SourceFailed
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.StatusChanged
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.Updated
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Idle
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Refreshing
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * The default registry of account provider descriptions.
 */

class AccountProviderRegistry private constructor(
  private val context: Context,
  private val sources: List<AccountProviderSourceType>,
  override val defaultProvider: AccountProviderType
) : AccountProviderRegistryType {

  @Volatile
  private var initialized = false

  @Volatile
  private var statusRef: AccountProviderRegistryStatus = Idle

  private val descriptions = ConcurrentHashMap<URI, AccountProviderDescriptionType>()
  private val descriptionsReadOnly = Collections.unmodifiableMap(this.descriptions)
  private val resolved = ConcurrentHashMap<URI, AccountProviderType>()
  private val resolvedReadOnly = Collections.unmodifiableMap(this.resolved)

  private val logger =
    LoggerFactory.getLogger(AccountProviderRegistry::class.java)

  private val eventsActual: PublishSubject<AccountProviderRegistryEvent> =
    PublishSubject.create()

  override val events: Observable<AccountProviderRegistryEvent> =
    this.eventsActual

  override val status: AccountProviderRegistryStatus
    get() = this.statusRef

  override fun accountProviderDescriptions(): Map<URI, AccountProviderDescriptionType> {
    if (!this.initialized) {
      this.refresh(false)
    }
    return this.descriptionsReadOnly
  }

  override val resolvedProviders: Map<URI, AccountProviderType>
    get() = this.resolvedReadOnly

  override fun refresh(includeTestingLibraries: Boolean) {
    this.logger.debug("refreshing account provider descriptions")

    this.statusRef = Refreshing
    this.eventsActual.onNext(StatusChanged)

    try {
      for (source in this.sources) {
        try {
          when (val result = source.load(this.context, includeTestingLibraries)) {
            is AccountProviderSourceType.SourceResult.SourceSucceeded -> {
              val newDescriptions = result.results
              for (key in newDescriptions.keys) {
                this.updateDescription(newDescriptions[key]!!)
              }
            }
            is AccountProviderSourceType.SourceResult.SourceFailed -> {
              this.eventsActual.onNext(SourceFailed(source.javaClass, result.exception))
            }
          }
        } catch (e: Exception) {
          this.eventsActual.onNext(SourceFailed(source.javaClass, e))
        }
      }
    } finally {
      this.initialized = true
      this.statusRef = Idle
      this.eventsActual.onNext(StatusChanged)
    }
  }

  override fun updateProvider(accountProvider: AccountProviderType): AccountProviderType {
    val id = accountProvider.id
    val existing = this.resolved[id]
    if (existing != null) {
      Preconditions.checkState(
        id == existing.id,
        "ID $id must match existing id ${existing.id}")
      if (existing.updated.isAfter(accountProvider.updated)) {
        return existing
      }
    }

    this.logger.debug("received updated version of resolved provider {}", id)
    this.resolved[id] = accountProvider
    this.eventsActual.onNext(Updated(id))

    this.updateDescription(accountProvider.toDescription())
    return accountProvider
  }

  override fun updateDescription(
    description: AccountProviderDescriptionType
  ): AccountProviderDescriptionType {

    val id = description.metadata.id
    val existing = this.descriptions[id]
    if (existing != null) {
      Preconditions.checkState(
        id == existing.metadata.id,
        "ID $id must match existing id ${existing.metadata.id}")
      if (existing.metadata.updated.isAfter(description.metadata.updated)) {
        return existing
      }
    }

    this.logger.debug("received updated version of description {}", id)
    this.descriptions[id] = description
    this.eventsActual.onNext(Updated(id))
    return description
  }

  companion object {

    /**
     * Create a new description registry based on sources discovered by [ServiceLoader]
     */

    fun createFromServiceLoader(
      context: Context,
      defaultProvider: AccountProviderType
    ): AccountProviderRegistryType {
      val loader =
        ServiceLoader.load(AccountProviderSourceType::class.java)
      val sources =
        loader.toList()
      return createFrom(context, sources, defaultProvider)
    }

    /**
     * Create a new description registry based on the given list of sources.
     */

    fun createFrom(
      context: Context,
      sources: List<AccountProviderSourceType>,
      defaultProvider: AccountProviderType
    ): AccountProviderRegistry = AccountProviderRegistry(context, sources, defaultProvider)
  }
}
