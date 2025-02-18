package org.simple.clinic.phone

import androidx.appcompat.app.AppCompatActivity
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.Observable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.util.RxErrorsRule

class PhoneDialerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private lateinit var phoneCaller: PhoneCaller
  private lateinit var config: PhoneNumberMaskerConfig
  private val dialer: Dialer = mock()
  private val activity: AppCompatActivity = mock()

  @Before
  fun setUp() {
    phoneCaller = PhoneCaller(Observable.fromCallable { config }, activity)
  }

  @Test
  fun `when masking is disabled then plain phone numbers should be called`() {
    config = PhoneNumberMaskerConfig(proxyPhoneNumber = "987", phoneMaskingFeatureEnabled = false)

    val plainNumber = "123"

    phoneCaller.normalCall(plainNumber, dialer = dialer).blockingAwait()

    verify(dialer).call(activity, plainNumber)
  }

  @Test
  fun `when masking is enabled then masked phone number should be called`() {
    config = PhoneNumberMaskerConfig(proxyPhoneNumber = "987", phoneMaskingFeatureEnabled = false)
    val plainNumber = "123"

    phoneCaller.secureCall(plainNumber, dialer = dialer).blockingAwait()

    verify(dialer).call(activity, "987,123#")
  }
}
