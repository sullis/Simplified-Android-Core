package org.nypl.simplified.books.controller

import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import com.io7m.junreachable.UnreachableCodeException
import org.nypl.drm.core.AdobeVendorID
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.createAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData.AccountLoginConnectionFailure
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData.AccountLoginCredentialsIncorrect
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData.AccountLoginNotRequired
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData.AccountLoginServerError
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginErrorData.AccountLoginServerParseError
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginStringResourcesType
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.http.core.HTTPResultError
import org.nypl.simplified.http.core.HTTPResultException
import org.nypl.simplified.http.core.HTTPResultOKType
import org.nypl.simplified.http.core.HTTPType
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.parser.api.ParseWarning
import org.nypl.simplified.patron.api.PatronDRM
import org.nypl.simplified.patron.api.PatronDRMAdobe
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.controller.api.AccountLoginTaskResult
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable

/**
 * A task that performs a login for the given account in the given profile.
 */

class ProfileAccountLoginTask(
  private val http: HTTPType,
  private val profile: ProfileReadableType,
  private val account: AccountType,
  private val loginStrings: AccountLoginStringResourcesType,
  private val patronParsers: PatronUserProfileParsersType,
  initialCredentials: AccountAuthenticationCredentials) : Callable<AccountLoginTaskResult> {

  @Volatile
  private var credentials: AccountAuthenticationCredentials =
    initialCredentials

  private var adobeDRM: PatronDRMAdobe? =
    null

  private val steps: TaskRecorderType<AccountLoginErrorData> =
    TaskRecorder.create()

  private val logger =
    LoggerFactory.getLogger(ProfileAccountLoginTask::class.java)

  override fun call() =
    this.run()

  private fun debug(message: String, vararg arguments: Any?) =
    this.logger.debug("[{}][{}] ${message}", this.profile.id().uuid, this.account.id(), *arguments)

  private fun checkAuthenticationRequired(): AccountProviderAuthenticationDescription? {
    val authentication = this.account.provider().authentication
    return if (authentication == null) {
      this.debug("account does not require authentication")
      this.steps.currentStepFailed(this.loginStrings.loginAuthNotRequired, AccountLoginNotRequired)
      this.account.setLoginState(AccountLoginFailed(this.steps.finish()))
      null
    } else authentication
  }

  private fun run(): AccountLoginTaskResult {
    try {
      val step = this.steps.beginNewStep(this.loginStrings.loginCheckAuthRequired)
      this.account.setLoginState(AccountLoggingIn(step.description))

      return when (this.checkAuthenticationRequired()) {
        is AccountProviderAuthenticationDescription -> {
          this.runPatronProfileRequest()
          this.runDeviceActivation()
          this.account.setLoginState(AccountLoggedIn(this.credentials))
          AccountLoginTaskResult(this.steps.finish())
        }
        else -> {
          this.steps.currentStepSucceeded(this.loginStrings.loginAuthNotRequired)
          AccountLoginTaskResult(this.steps.finish())
        }
      }
    } catch (e: Exception) {
      val step = this.steps.currentStep()!!
      if (step.exception == null) {
        this.steps.currentStepFailed(
          message = pickUsableMessage(step.resolution, e),
          errorValue = step.errorValue,
          exception = e)
      }

      val resultingSteps = this.steps.finish()
      this.account.setLoginState(AccountLoginFailed(resultingSteps))
      return AccountLoginTaskResult(this.steps.finish())
    }
  }

  private fun runDeviceActivation() {
    this.logger.debug("running device activation")

    val step =
      this.steps.beginNewStep(this.loginStrings.loginDeviceActivation)

    val adobeDRMValues = this.adobeDRM
    if (adobeDRMValues != null) {
      this.logger.debug("constructing new adobe credentials")

      val newCredentials =
        this.credentials.toBuilder()
          .setAdobeCredentials(AccountAuthenticationAdobePreActivationCredentials(
            AdobeVendorID(adobeDRMValues.vendor),
            AccountAuthenticationAdobeClientToken.create(adobeDRMValues.clientToken),
            adobeDRMValues.deviceManagerURI,
            null))
          .build()

      this.credentials = newCredentials
    }

    this.steps.currentStepSucceeded(this.loginStrings.loginDeviceActivated)
  }

  private fun pickUsableMessage(message: String, e: Exception): String {
    val exMessage = e.message
    return if (message.isEmpty()) {
      if (exMessage != null) {
        exMessage
      } else {
        e.javaClass.simpleName
      }
    } else {
      message
    }
  }

  private fun <T> someOrNull(option: OptionType<T>): T? {
    return if (option is Some<T>) {
      option.get()
    } else {
      null
    }
  }

  /**
   * Execute a patron profile document request. This fetches patron settings from the remote
   * server and attempts to extract useful information such as DRM-related credentials.
   */

  private fun runPatronProfileRequest() {
    this.logger.debug("running patron profile request")

    val step = this.steps.beginNewStep(this.loginStrings.loginPatronSettingsRequest)
    this.account.setLoginState(AccountLoggingIn(step.description))

    val patronSettingsURI = this.account.provider().patronSettingsURI
    if (patronSettingsURI == null) {
      this.steps.currentStepFailed(this.loginStrings.loginPatronSettingsRequestNoURI)
      throw Exception()
    }

    val httpAuthentication =
      createAuthenticatedHTTP(this.credentials)
    val result =
      this.http.get(Option.some(httpAuthentication), patronSettingsURI, 0L)

    return when (result) {
      is HTTPResultOKType<InputStream> ->
        this.onPatronProfileRequestOK(patronSettingsURI, result)
      is HTTPResultError<InputStream> ->
        this.onPatronProfileRequestHTTPError(patronSettingsURI, result)
      is HTTPResultException<InputStream> ->
        this.onPatronProfileRequestHTTPException(patronSettingsURI, result)
      else ->
        throw UnreachableCodeException()
    }
  }

  /**
   * A patron settings document was received. Parse it and try to extract any required
   * DRM information.
   */

  private fun onPatronProfileRequestOK(
    patronSettingsURI: URI,
    result: HTTPResultOKType<InputStream>) {
    this.logger.debug("requested patron profile successfully")
    return this.patronParsers.createParser(patronSettingsURI, result.value).use { parser ->
      when (val parseResult = parser.parse()) {
        is ParseResult.Success -> {
          this.logger.debug("parsed patron profile successfully")
          parseResult.warnings.forEach(this::logParseWarning)
          parseResult.result.drm.forEach(this::onPatronProfileRequestHandleDRM)
          this.steps.currentStepSucceeded(this.loginStrings.loginPatronSettingsRequestOK)
        }
        is ParseResult.Failure -> {
          this.logger.error("failed to parse patron profile")
          this.steps.currentStepFailed(
            message = this.loginStrings.loginPatronSettingsRequestParseFailed(
              parseResult.errors.map(this::showParseError)),
            errorValue = AccountLoginServerParseError(parseResult.warnings, parseResult.errors))
          throw Exception()
        }
      }
    }
  }

  /**
   * Log and convert a parse error to a humanly-readable string.
   */

  private fun showParseError(error: ParseError): String {
    this.logger.error(
      "{}:{}:{}: {}: ",
      error.source,
      error.line,
      error.column,
      error.message,
      error.exception)

    return buildString {
      this.append(error.line)
      this.append(':')
      this.append(error.column)
      this.append(": ")
      this.append(error.message)
      val ex = error.exception
      if (ex != null) {
        this.append(ex.message)
        this.append(" (")
        this.append(ex.javaClass.simpleName)
        this.append(")")
      }
    }
  }

  /**
   * Process a DRM item.
   */

  private fun onPatronProfileRequestHandleDRM(drm: PatronDRM) {
    return when (drm) {
      is PatronDRMAdobe -> this.onPatronProfileRequestHandleDRMAdobe(drm)
      else -> {

      }
    }
  }

  private fun onPatronProfileRequestHandleDRMAdobe(drm: PatronDRMAdobe) {
    this.logger.debug("received Adobe DRM client token")
    this.adobeDRM = drm
  }

  private fun onPatronProfileRequestHTTPException(
    patronSettingsURI: URI,
    result: HTTPResultException<InputStream>) {
    this.steps.currentStepFailed(
      message = this.loginStrings.loginPatronSettingsConnectionFailed,
      errorValue = AccountLoginConnectionFailure,
      exception = result.error)
    throw result.error
  }

  private fun onPatronProfileRequestHTTPError(
    patronSettingsURI: URI,
    result: HTTPResultError<InputStream>) {
    this.debug("received http error: {}: {}: {}", patronSettingsURI, result.message, result.status)

    when (result.status) {
      HttpURLConnection.HTTP_UNAUTHORIZED -> {
        this.steps.currentStepFailed(
          message = this.loginStrings.loginPatronSettingsInvalidCredentials,
          errorValue = AccountLoginCredentialsIncorrect)
        throw Exception()
      }
      else -> {
        this.steps.currentStepFailed(
          message = this.loginStrings.loginServerError(result.status, result.message),
          errorValue = AccountLoginServerError(
            uri = patronSettingsURI,
            statusCode = result.status,
            errorMessage = result.message,
            errorReport = this.someOrNull(result.problemReport)))
        throw Exception()
      }
    }
  }

  private fun logParseWarning(warning: ParseWarning) {
    this.logger.warn(
      "{}:{}:{}: {}: ",
      warning.source,
      warning.line,
      warning.column,
      warning.message,
      warning.exception)
  }
}
