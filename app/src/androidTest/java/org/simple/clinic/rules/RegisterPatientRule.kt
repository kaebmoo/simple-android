package org.simple.clinic.rules

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.encounter.sync.EncounterPushRequest
import org.simple.clinic.encounter.sync.EncounterSyncApi
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.sync.PatientPushRequest
import org.simple.clinic.patient.sync.PatientSyncApi
import org.simple.clinic.user.UserSession
import java.util.UUID
import javax.inject.Inject

class RegisterPatientRule(val patientUuid: UUID) : TestRule {

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var userSession: UserSession

  @Inject
  lateinit var facilityRepository: FacilityRepository

  @Inject
  lateinit var patientSyncApi: PatientSyncApi

  @Inject
  lateinit var encounterSyncApi: EncounterSyncApi

  /**
   * This registers a patient **AND** a blood pressure at the current logged in facility. This was
   * done because the server restricts syncing patient data only to users in the same facility
   * group, and the only way to associate a patient with a facility (currently) is by recording
   * a blood pressure for the patient at the facility.
   **/
  private fun registerPatient() {
    val registeredFacilityUuid = facilityRepository
        .currentFacilityUuid(userSession.loggedInUserImmediate()!!)!!

    val patientPayload = testData.patientPayload(uuid = patientUuid)
    val patientPushRequest = PatientPushRequest(listOf(patientPayload))

    val bloodPressureMeasurementPayload = testData
        .bloodPressureMeasurement(patientUuid = patientUuid, facilityUuid = registeredFacilityUuid)
        .toPayload()
    val encounterPayload = testData.encounterPayload(
        uuid = UUID.randomUUID(),
        patientUuid = patientUuid,
        bpPayloads = listOf(bloodPressureMeasurementPayload)
    )
    val encounterPushRequest = EncounterPushRequest(listOf(encounterPayload))

    patientSyncApi
        .push(patientPushRequest)
        .concatWith(encounterSyncApi.push(encounterPushRequest))
        .ignoreElements()
        .blockingAwait()
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        TestClinicApp.appComponent().inject(this@RegisterPatientRule)
        registerPatient()
        base.evaluate()
      }
    }
  }
}
