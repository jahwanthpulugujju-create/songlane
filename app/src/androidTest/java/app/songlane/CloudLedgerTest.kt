package app.songlane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.agent.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudLedgerTest {
    private lateinit var context: Context
    private lateinit var ledger: CloudLedger
    private var day = IntentModels.reviewedOn
    private val model = IntentModels.all.first()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("cloud-ledger-test.db")
        context.getSharedPreferences("agent-limits", Context.MODE_PRIVATE).edit().clear().commit()
        day = IntentModels.reviewedOn
        ledger = CloudLedger(context, "cloud-ledger-test.db") { day }
    }

    @After
    fun cleanup() {
        ledger.close()
        context.deleteDatabase("cloud-ledger-test.db")
    }

    @Test
    fun unresolvedReservationSurvivesRecreationAndChangingProviderDoesNotResetBudget() {
        ledger.configure(CloudLimits(model.reservationNanoUsd, 5_000_000_000L))
        ledger.reserve(model)
        ledger.close()
        ledger = CloudLedger(context, "cloud-ledger-test.db") { day }
        assertEquals(model.reservationNanoUsd, ledger.snapshot().usage.todayNanoUsd)
        assertEquals(1, ledger.snapshot().usage.unresolved)
        assertTrue(runCatching { ledger.reserve(IntentModels.all.last()) }.isFailure)
        assertEquals(1, ledger.snapshot().usage.callsToday)
    }

    @Test
    fun settlementIsIdempotentAndLowerCeilingsNeverClearSpend() {
        val reservation = ledger.reserve(model)
        ledger.settle(reservation, 100, 50)
        ledger.settle(reservation, 0, 0)
        ledger.unknown(reservation)
        assertEquals(model.cost(100, 50), ledger.snapshot().usage.todayNanoUsd)
        assertEquals(0, ledger.snapshot().usage.unresolved)
        ledger.configure(CloudLimits(0, 0))
        assertTrue(runCatching { ledger.reserve(model) }.isFailure)
        assertEquals(1, ledger.snapshot().usage.callsToday)
    }

    @Test
    fun concurrentInstancesCannotOverbookTheSameCeiling() = runBlocking {
        ledger.configure(CloudLimits(model.reservationNanoUsd, 5_000_000_000L))
        val other = CloudLedger(context, "cloud-ledger-test.db") { day }
        try {
            val attempts =
                (0..11)
                    .map { index ->
                        async(Dispatchers.IO) {
                            runCatching { (if (index % 2 == 0) ledger else other).reserve(model) }
                                .isSuccess
                        }
                    }
                    .awaitAll()
            assertEquals(1, attempts.count { it })
            assertEquals(model.reservationNanoUsd, ledger.snapshot().usage.todayNanoUsd)
        } finally {
            other.close()
        }
    }

    @Test
    fun utcDailyAndMonthlyWindowsAndDateRollbackAreEnforced() {
        ledger.configure(CloudLimits(10_000_000_000L, model.reservationNanoUsd))
        ledger.reserve(model)
        day = day.plusDays(1)
        assertEquals(0, ledger.snapshot().usage.callsToday)
        assertTrue(runCatching { ledger.reserve(model) }.isFailure)
        day = day.withDayOfMonth(1).plusMonths(1)
        ledger.reserve(model)
        assertEquals(model.reservationNanoUsd, ledger.snapshot().usage.monthNanoUsd)
        day = day.minusDays(1)
        assertTrue(runCatching { ledger.reserve(model) }.isFailure)
    }

    @Test
    fun tenCallsStayLimitedEvenWhenReportedCostIsZero() {
        repeat(10) { ledger.settle(ledger.reserve(model), 0, 0) }
        assertTrue(runCatching { ledger.reserve(model) }.isFailure)
        assertEquals(10, ledger.snapshot().usage.callsToday)
    }

    @Test
    fun providerAnomalyHaltsRequestsWithoutClearingAccounting() {
        ledger.reserve(model)
        ledger.halt()
        assertTrue(ledger.snapshot().usage.halted)
        ledger.configure(CloudLimits(10_000_000_000L, 50_000_000_000L))
        assertTrue(runCatching { ledger.reserve(model) }.isFailure)
        ledger.erase()
        assertEquals(CloudState(), ledger.snapshot())
    }
}
