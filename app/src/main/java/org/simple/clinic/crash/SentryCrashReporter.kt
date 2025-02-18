package org.simple.clinic.crash

import android.app.Application
import io.reactivex.schedulers.Schedulers.io
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.BreadcrumbBuilder
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.platform.crash.Breadcrumb
import org.simple.clinic.platform.crash.Breadcrumb.Priority.ASSERT
import org.simple.clinic.platform.crash.Breadcrumb.Priority.DEBUG
import org.simple.clinic.platform.crash.Breadcrumb.Priority.ERROR
import org.simple.clinic.platform.crash.Breadcrumb.Priority.INFO
import org.simple.clinic.platform.crash.Breadcrumb.Priority.VERBOSE
import org.simple.clinic.platform.crash.Breadcrumb.Priority.WARN
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.filterAndUnwrapJust
import java.util.Date

typealias SentryBreadcrumbLevel = io.sentry.event.Breadcrumb.Level

class SentryCrashReporter(
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository
) : CrashReporter {

  override fun init(appContext: Application) {
    Sentry.init(AndroidSentryClientFactory(appContext))
    identifyUserAndCurrentFacility()
  }

  @Suppress("CheckResult")
  private fun identifyUserAndCurrentFacility() {
    val loggedInUserStream = userSession.loggedInUser()
        .subscribeOn(io())
        .filterAndUnwrapJust()
        .replay()
        .refCount()

    loggedInUserStream
        .map { it.uuid }
        .subscribe(
            { Sentry.getContext().addTag("userUuid", it.toString()) },
            { report(it) })

    loggedInUserStream
        .flatMap { facilityRepository.currentFacility(it) }
        .map { it.uuid }
        .subscribe(
            { Sentry.getContext().addTag("facilityUuid", it.toString()) },
            { report(it) })
  }

  override fun dropBreadcrumb(breadcrumb: Breadcrumb) {
    val sentryBreadcrumb = BreadcrumbBuilder()
        .setLevel(priorityToLevel(breadcrumb.priority))
        .setCategory(breadcrumb.tag)
        .setMessage(breadcrumb.message)
        .setTimestamp(Date(System.currentTimeMillis()))
        .build()
    Sentry.getContext().recordBreadcrumb(sentryBreadcrumb)
  }

  private fun priorityToLevel(priority: Breadcrumb.Priority): SentryBreadcrumbLevel {
    return when (priority) {
      VERBOSE, DEBUG -> SentryBreadcrumbLevel.DEBUG
      INFO -> SentryBreadcrumbLevel.INFO
      WARN -> SentryBreadcrumbLevel.WARNING
      ERROR, ASSERT -> SentryBreadcrumbLevel.ERROR
    }
  }

  override fun report(e: Throwable) {
    Sentry.capture(e)
  }
}
