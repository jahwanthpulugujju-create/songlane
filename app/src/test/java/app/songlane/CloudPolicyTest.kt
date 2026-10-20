package app.songlane

import app.songlane.agent.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CloudPolicyTest {
    @Test
    fun reservationsUseFullContextAndBoundedOutputWithIntegerMoney() {
        val haiku = IntentModels.all.first { it.provider == AiProvider.ANTHROPIC }
        val mini = IntentModels.all.first { it.provider == AiProvider.OPENAI }
        assertEquals(201_280_000L, haiku.reservationNanoUsd)
        assertEquals(301_152_000L, mini.reservationNanoUsd)
        assertEquals(350_000L, haiku.cost(100, 50))
        assertEquals(300_000L, mini.cost(100, 50))
        for (model in IntentModels.all) {
            assertTrue(runCatching { model.cost(model.contextTokens + 1, 1) }.isFailure)
            assertTrue(runCatching { model.cost(1, 257) }.isFailure)
            assertTrue(runCatching { model.cost(-1, 1) }.isFailure)
        }
    }

    @Test
    fun reviewedModelsAndDatesCannotSilentlyAcceptArbitraryPricing() {
        assertTrue(runCatching { IntentModels.find("gpt-999") }.isFailure)
        IntentModels.checkDate(IntentModels.reviewedOn)
        IntentModels.checkDate(IntentModels.validThrough)
        assertTrue(
            runCatching { IntentModels.checkDate(IntentModels.validThrough.plusDays(1)) }.isFailure
        )
        assertTrue(runCatching { IntentModels.checkDate(LocalDate.of(2020, 1, 1)) }.isFailure)
        assertEquals(IntentModels.all.size, IntentModels.all.map { it.id }.toSet().size)
        IntentModels.all.forEach {
            assertTrue(it.provider.endpoint.startsWith("https://${it.provider.host}/"))
        }
    }

    @Test
    fun currencyParsingRejectsExponentsSignsRoundingAndLocaleAmbiguity() {
        assertEquals(500_000_000L, parseUsd("0.5"))
        assertEquals(1_000L, parseUsd("0.000001"))
        assertEquals("0.5", usdInput(500_000_000L))
        assertEquals("USD 0.000001", usd(1))
        listOf("-1", "+1", "NaN", "1e4", "1,50", " 1", "0.0000001", "1000", ".5").forEach {
            assertTrue(it, runCatching { parseUsd(it) }.isFailure)
        }
    }

    @Test
    fun zeroCeilingsAreAllowedButUnsupportedTimeoutsAndAmountsAreRejected() {
        CloudLimits(0, 0, 15)
        CloudLimits(100_000_000_000L, 500_000_000_000L, 60)
        assertTrue(runCatching { CloudLimits(-1, 1, 30) }.isFailure)
        assertTrue(runCatching { CloudLimits(1, 1, 0) }.isFailure)
        assertTrue(runCatching { CloudLimits(101_000_000_000L) }.isFailure)
    }
}
