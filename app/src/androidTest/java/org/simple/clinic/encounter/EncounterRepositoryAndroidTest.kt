package org.simple.clinic.encounter

import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.SyncStatus.DONE
import org.simple.clinic.patient.SyncStatus.PENDING
import org.simple.clinic.rules.LocalAuthenticationRule
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.TestUtcClock
import org.simple.clinic.util.generateEncounterUuid
import org.simple.clinic.util.toLocalDateAtZone
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class EncounterRepositoryAndroidTest {

  @Inject
  lateinit var appDatabase: AppDatabase

  @Inject
  lateinit var testUtcClock: TestUtcClock

  @Inject
  lateinit var repository: EncounterRepository

  @Inject
  lateinit var bloodPressureRepository: BloodPressureRepository

  @Inject
  lateinit var testData: TestData

  private val authenticationRule = LocalAuthenticationRule()

  private val rxErrorsRule = RxErrorsRule()

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(authenticationRule)
      .around(rxErrorsRule)!!

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
    testUtcClock.setDate(LocalDate.parse("2018-01-01"))
  }

  @Test
  fun record_count_should_return_the_count_of_no_of_encounters() {
    //given
    repository.save(listOf(testData.observationsForEncounter(testData.encounter(
        uuid = UUID.fromString("6f70d92d-340e-42ea-8274-ccbf3b174d7c"),
        patientUuid = UUID.fromString("c25805f2-033b-45ad-bd75-45a2c3d29e87")
    )))).blockingAwait()

    //when
    val count = repository.recordCount().blockingFirst()

    //then
    assertThat(count).isEqualTo(1)
  }

  @Test
  fun encounter_payload_should_merge_correctly_with_encounter_database_model() {
    //given
    val patientUuid = UUID.fromString("c2273c6c-036b-41f0-a128-84d5551be3ce")
    val encounterUuid1 = UUID.fromString("0b144e4a-ce13-431d-93f1-1d10d0639c09")
    val encounterUuid2 = UUID.fromString("0799e067-6b14-4e45-8171-e6a9d84626fb")
    val bpUuid1 = UUID.fromString("0347a91f-8776-418d-a117-7cf6a4ae0713")
    val bpUuid2 = UUID.fromString("cd90ca58-b8ea-413c-bdb5-c5941b943f96")

    val bpPayloads1 = listOf(testData.bpPayload(uuid = bpUuid1, patientUuid = patientUuid))
    val bpPayloads2 = listOf(testData.bpPayload(uuid = bpUuid2, patientUuid = patientUuid))

    val encountersPayload1 = testData.encounterPayload(uuid = encounterUuid1, patientUuid = patientUuid, updatedAt = Instant.parse("2018-02-11T00:00:00Z"), bpPayloads = bpPayloads1)
    val encountersPayload2 = testData.encounterPayload(uuid = encounterUuid2, patientUuid = patientUuid, bpPayloads = bpPayloads2)

    val encounter1 = testData.encounter(
        uuid = encounterUuid1,
        patientUuid = patientUuid,
        syncStatus = PENDING,
        updatedAt = Instant.parse("2018-02-13T00:00:00Z")
    )
    repository.save(listOf(testData.observationsForEncounter(
        encounter = encounter1
    ))).blockingAwait()

    //when
    repository.mergeWithLocalData(listOf(encountersPayload1, encountersPayload2)).blockingAwait()
    val observationsForEncounters = repository.recordsWithSyncStatus(SyncStatus.DONE).blockingGet()
    val pendingObservationsForEncounters = repository.recordsWithSyncStatus(PENDING).blockingGet()

    //then
    assertThat(observationsForEncounters.size).isEqualTo(1)
    with(observationsForEncounters.first()) {
      assertThat(encounter.uuid).isEqualTo(encounterUuid2)
      assertThat(bloodPressures.size).isEqualTo(1)
      assertThat(bloodPressures.first().uuid).isEqualTo(bpUuid2)
    }
    assertThat(pendingObservationsForEncounters.size).isEqualTo(1)
    with(pendingObservationsForEncounters.first().encounter) {
      assertThat(uuid).isEqualTo(encounterUuid1)
      assertThat(updatedAt).isEqualTo(Instant.parse("2018-02-13T00:00:00Z"))
    }
  }

  @Test
  fun when_a_bp_is_saved_encounter_should_get_created() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")
    val bpUuid = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val encounteredDate = LocalDate.parse("2018-01-01")
    val bloodPressureMeasurement = testData.bloodPressureMeasurement(
        uuid = bpUuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        syncStatus = PENDING,
        encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    )

    //when
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement).blockingAwait()

    //then
    val bps = appDatabase.bloodPressureDao().bloodPressure(bpUuid).blockingFirst()
    val observationsForEncounters = appDatabase.encountersDao().recordsWithSyncStatus(PENDING).blockingFirst().first()

    with(observationsForEncounters.encounter) {
      assertThat(encounteredOn).isEqualTo(encounteredDate)
      assertThat(createdAt).isEqualTo(bps.createdAt)
      assertThat(updatedAt).isEqualTo(bps.updatedAt)
      assertThat(deletedAt).isEqualTo(bps.deletedAt)
      assertThat(syncStatus).isEqualTo(bps.syncStatus)
    }

    with(observationsForEncounters.bloodPressures.first()) {
      assertThat(bps).isEqualTo(this)
      assertThat(bps.patientUuid).isEqualTo(patientUuid)
      assertThat(bps.encounterUuid).isEqualTo(observationsForEncounters.encounter.uuid)
    }
  }

  @Test
  fun when_a_bp_is_saved_new_encounter_should_get_created_only_if_it_does_not_exist() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")

    val bpUuid = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val encounteredDate = LocalDate.parse("2018-01-01")
    val encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    val bloodPressureToBeSaved = testData.bloodPressureMeasurement(
        uuid = bpUuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        syncStatus = PENDING,
        encounterUuid = encounterUuid
    )

    val recordedAtForAlreadySavedBp = Instant.parse("2018-01-01T22:00:00Z")
    val zone = TestUserClock(LocalDate.parse("2018-01-01")).zone
    val encounteredDateForAlreadySavedBp = recordedAtForAlreadySavedBp.toLocalDateAtZone(zone)
    val alreadySavedBloodPressure = testData.bloodPressureMeasurement(
        uuid = UUID.fromString("82d92668-ac3e-41b2-9f35-054d67a4523c"),
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = recordedAtForAlreadySavedBp,
        createdAt = Instant.parse("2018-01-01T21:00:00Z"),
        updatedAt = Instant.parse("2018-01-03T00:00:00Z"),
        syncStatus = SyncStatus.DONE,
        encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDateForAlreadySavedBp)
    )

    //when
    repository.saveBloodPressureMeasurement(alreadySavedBloodPressure).blockingAwait()
    repository.saveBloodPressureMeasurement(bloodPressureToBeSaved).blockingAwait()

    //then
    val updatedBp = appDatabase.bloodPressureDao().bloodPressure(bpUuid).blockingFirst()
    val encounters = appDatabase.encountersDao().recordsWithSyncStatus(PENDING).blockingFirst()

    assertThat(encounters.size).isEqualTo(1)

    with(encounters.first()) {
      with(encounter) {
        assertThat(encounteredOn).isEqualTo(encounteredDate)
        assertThat(createdAt).isEqualTo(updatedBp.createdAt)
        assertThat(updatedAt).isEqualTo(updatedBp.updatedAt)
        assertThat(deletedAt).isEqualTo(updatedBp.deletedAt)
        assertThat(syncStatus).isEqualTo(updatedBp.syncStatus)
      }
      assertThat(bloodPressures).isEqualTo(listOf(alreadySavedBloodPressure, bloodPressureToBeSaved))
    }
  }

  @Test
  fun when_a_bp_is_updated_then_its_encounter_should_also_get_updated_correctly() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")
    val bpUuid = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val encounteredDate = LocalDate.parse("2018-01-01")

    val bloodPressureMeasurement = testData.bloodPressureMeasurement(
        uuid = bpUuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        syncStatus = DONE,
        encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    )
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement).blockingAwait()

    //when
    val updatedMeasurement = bloodPressureMeasurement.copy(systolic = 200)
    testUtcClock.advanceBy(Duration.ofMinutes(10))
    repository.updateBloodPressure(updatedMeasurement).blockingAwait()

    //then
    val updatedBp = appDatabase.bloodPressureDao().bloodPressure(bpUuid).blockingFirst()
    val observationsForEncounter = appDatabase.encountersDao().recordsWithSyncStatus(PENDING).blockingFirst().first()

    with(observationsForEncounter.bloodPressures.first()) {
      assertThat(systolic).isEqualTo(updatedMeasurement.systolic)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(updatedAt).isEqualTo(Instant.now(testUtcClock))
    }

    with(observationsForEncounter.encounter) {
      assertThat(encounteredOn).isEqualTo(encounteredDate)
      assertThat(createdAt).isEqualTo(bloodPressureMeasurement.createdAt)
      assertThat(updatedAt).isEqualTo(updatedBp.updatedAt)
      assertThat(deletedAt).isEqualTo(bloodPressureMeasurement.deletedAt)
      assertThat(syncStatus).isEqualTo(PENDING)
    }
  }

  @Test
  fun when_bp_recorded_date_is_changed_then_encounterId_should_get_updated_and_old_encounter_should_be_deleted() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")
    val bpUuid = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val encounteredDate = LocalDate.parse("2018-01-01")

    val encounterUuidForOldBp = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    val bloodPressureMeasurement = testData.bloodPressureMeasurement(
        uuid = bpUuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        deletedAt = null,
        syncStatus = PENDING,
        encounterUuid = encounterUuidForOldBp
    )
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement).blockingAwait()

    //when
    val newRecordedAt = Instant.parse("2018-02-01T00:00:00Z")
    val updatedMeasurement = bloodPressureMeasurement.copy(recordedAt = newRecordedAt)
    repository.updateBloodPressure(updatedMeasurement).blockingAwait()

    //then
    val updatedBp = appDatabase.bloodPressureDao().bloodPressure(bpUuid).blockingFirst()
    val encounters = appDatabase.encountersDao().recordsWithSyncStatus(PENDING).blockingFirst()
    val expectedEncounterUuid = generateEncounterUuid(facilityUuid, patientUuid, LocalDate.parse("2018-02-01"))

    with(updatedBp) {
      assertThat(uuid).isEqualTo(bpUuid)
      assertThat(encounterUuid).isEqualTo(expectedEncounterUuid)
      assertThat(recordedAt).isEqualTo(newRecordedAt)
      assertThat(syncStatus).isEqualTo(PENDING)
    }

    assertThat(encounters.size).isEqualTo(2)
    with(encounters[0]) {
      assertThat(encounter.uuid).isEqualTo(encounterUuidForOldBp)
      assertThat(encounter.encounteredOn).isEqualTo(LocalDate.parse("2018-01-01"))
      assertThat(encounter.deletedAt).isNotNull()
    }

    with(encounters[1]) {
      assertThat(encounter.uuid).isEqualTo(expectedEncounterUuid)
      assertThat(encounter.encounteredOn).isEqualTo(LocalDate.parse("2018-02-01"))
      assertThat(encounter.deletedAt).isNull()
    }
  }

  @Test
  fun encounter_should_only_be_deleted_if_there_are_no_associated_active_observations() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")
    val bpUuid = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val encounteredDate = LocalDate.parse("2018-01-01")

    val encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    val bloodPressureMeasurement = testData.bloodPressureMeasurement(
        uuid = bpUuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        deletedAt = Instant.parse("2018-01-01T00:00:00Z"),
        syncStatus = PENDING,
        encounterUuid = encounterUuid
    )
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement).blockingAwait()

    //when
    repository.deleteEncounter(encounterUuid).blockingAwait()

    //then
    val encounter = appDatabase.encountersDao().getOne(encounterUuid)!!

    assertThat(encounter.deletedAt).isNotNull()
  }

  @Test
  fun encounter_should_be_marked_as_sync_pending_even_when_a_bp_is_deleted_but_that_encounter_is_not() {
    //given
    val patientUuid = UUID.fromString("6f725ffd-7008-4018-9e7c-33346964a0c1")
    val facilityUuid = UUID.fromString("22bdeedb-061e-4a17-8739-e946a4206593")
    val bpUuid1 = UUID.fromString("2edd4c06-e2de-4a18-a8e7-43e2e30c9aba")
    val bpUuid2 = UUID.fromString("da8d6348-24de-4259-bcd7-849f6052661f")
    val encounteredDate = LocalDate.parse("2018-01-01")

    val encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, encounteredDate)
    val bloodPressureMeasurement1 = testData.bloodPressureMeasurement(
        uuid = bpUuid1,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
        createdAt = Instant.parse("2018-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
        deletedAt = null,
        syncStatus = DONE,
        encounterUuid = encounterUuid
    )

    val bloodPressureMeasurement2 = testData.bloodPressureMeasurement(
        uuid = bpUuid2,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        recordedAt = Instant.parse("2018-01-01T00:02:00Z"),
        createdAt = Instant.parse("2018-01-01T00:02:00Z"),
        updatedAt = Instant.parse("2018-01-01T00:02:00Z"),
        deletedAt = null,
        syncStatus = DONE,
        encounterUuid = encounterUuid
    )
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement1).blockingAwait()
    repository.saveBloodPressureMeasurement(bloodPressureMeasurement2).blockingAwait()

    //when
    repository.setSyncStatus(listOf(encounterUuid), DONE).blockingAwait()
    bloodPressureRepository.markBloodPressureAsDeleted(bloodPressureMeasurement1).blockingAwait()

    //then
    val encounter = appDatabase.encountersDao().getOne(encounterUuid)!!

    assertThat(encounter.syncStatus).isEqualTo(PENDING)
    assertThat(encounter.deletedAt).isNull()
  }
}
