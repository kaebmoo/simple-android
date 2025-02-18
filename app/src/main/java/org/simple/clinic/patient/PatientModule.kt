package org.simple.clinic.patient

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.reactivex.Observable
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.BusinessIdMetaData
import org.simple.clinic.patient.businessid.BusinessIdMetaDataAdapter
import org.simple.clinic.patient.businessid.MoshiBusinessIdMetaDataAdapter
import org.simple.clinic.patient.filter.SearchPatientByName
import org.simple.clinic.patient.filter.SortByWeightedNameParts
import org.simple.clinic.patient.filter.WeightedLevenshteinSearch
import org.simple.clinic.phone.PhoneNumberMaskerConfig
import org.simple.clinic.remoteconfig.ConfigReader

@Module
open class PatientModule {

  @Provides
  open fun provideFilterPatientByName(): SearchPatientByName = WeightedLevenshteinSearch(
      minimumSearchTermLength = 3,
      maximumAllowedEditDistance = 350F,

      // Values are taken from what sqlite spellfix uses internally.
      characterSubstitutionCost = 150F,
      characterDeletionCost = 100F,
      characterInsertionCost = 100F,

      resultsComparator = SortByWeightedNameParts())

  @Provides
  fun providePatientConfig(configReader: ConfigReader) = PatientConfig.read(configReader)

  @Provides
  fun phoneNumberMaskerConfig(reader: ConfigReader): Observable<PhoneNumberMaskerConfig> =
      PhoneNumberMaskerConfig.read(reader)

  @Provides
  fun provideBusinessIdMetaAdapter(moshi: Moshi): BusinessIdMetaDataAdapter {
    @Suppress("UNCHECKED_CAST")
    val adapters: Map<BusinessId.MetaDataVersion, JsonAdapter<BusinessIdMetaData>> = mapOf(
        BusinessId.MetaDataVersion.BpPassportMetaDataV1 to
            moshi.adapter(BusinessIdMetaData.BpPassportMetaDataV1::class.java) as JsonAdapter<BusinessIdMetaData>
    )

    return MoshiBusinessIdMetaDataAdapter(adapters)
  }
}
