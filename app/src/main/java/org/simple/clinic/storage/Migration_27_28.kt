package org.simple.clinic.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.f2prateek.rx.preferences2.Preference
import org.simple.clinic.util.Optional
import javax.inject.Named

@Suppress("ClassName")
class Migration_27_28 @javax.inject.Inject constructor(
    @Named("last_facility_pull_token")
    val lastPullToken: Preference<Optional<String>>
) : Migration(27, 28) {

  override fun migrate(database: SupportSQLiteDatabase) {
    lastPullToken.delete()

    database.execSQL("""
      ALTER TABLE "Facility"
      ADD COLUMN "location_latitude" REAL
      DEFAULT null
    """)

    database.execSQL("""
      ALTER TABLE "Facility"
      ADD COLUMN "location_longitude" REAL
      DEFAULT null
    """)
  }
}
