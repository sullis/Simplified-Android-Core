package org.nypl.simplified.accounts.source.nyplregistry

import org.nypl.simplified.http.core.HTTPHasProblemReportType
import org.nypl.simplified.http.core.HTTPProblemReport
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseWarning
import org.nypl.simplified.presentableerror.api.Presentables
import java.net.URI

/**
 * An exception raised by the NYPL registry implementation.
 */

sealed class AccountProviderSourceNYPLRegistryException(
  message: String,
  cause: Exception? = null)
  : Exception(message, cause) {

  /**
   * We failed to connect to the server at all.
   */

  class ServerConnectionFailure(
    val uri: URI,
    cause: Exception)
    : AccountProviderSourceNYPLRegistryException(cause.message ?: "", cause)

  /**
   * The server returned an error instead of a set of account providers.
   */

  class ServerReturnedError(
    val uri: URI,
    val errorCode: Int,
    message: String,
    override val problemReport: HTTPProblemReport?)
    : AccountProviderSourceNYPLRegistryException(message), HTTPHasProblemReportType

  /**
   * The server returned data that could not be parsed.
   */

  class ServerReturnedUnparseableData(
    val uri: URI,
    val warnings: List<ParseWarning>,
    val errors: List<ParseError>,
    cause: Exception? = null)
    : AccountProviderSourceNYPLRegistryException(cause?.message ?: "", cause)
}
