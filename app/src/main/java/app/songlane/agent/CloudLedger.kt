package app.songlane.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class CloudReservation(val id: String, val model: IntentModel, val nanoUsd: Long)

/** Transactional, global across providers and parser instances; no prompt text or credentials. */
class CloudLedger(
    context: Context,
    name: String = "cloud-budget.db",
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) :
    SQLiteOpenHelper(
        context,
        name,
        null,
        1,
        { throw SQLiteDatabaseCorruptException("Cloud accounting must not be silently reset") },
    ) {
    private val appContext = context.applicationContext
    private val legacy = context.getSharedPreferences("agent-limits", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE policy (id INTEGER PRIMARY KEY CHECK(id=1), daily INTEGER NOT NULL, monthly INTEGER NOT NULL, timeout INTEGER NOT NULL, last_day TEXT NOT NULL, halted INTEGER NOT NULL)"
        )
        db.execSQL("INSERT INTO policy VALUES (1, 500000000, 5000000000, 30, '', 0)")
        db.execSQL(
            "CREATE TABLE requests (id TEXT PRIMARY KEY, provider TEXT NOT NULL, model TEXT NOT NULL, day TEXT NOT NULL, month TEXT NOT NULL, reserved INTEGER NOT NULL, charged INTEGER NOT NULL, state TEXT NOT NULL, input_tokens INTEGER, output_tokens INTEGER)"
        )
        db.execSQL("CREATE INDEX request_day ON requests(day)")
        db.execSQL("CREATE INDEX request_month ON requests(month)")
        // Previous versions kept only a daily count. Carry it as unknown conservative reservations.
        if (legacy.getString("day", "") == LocalDate.now().toString()) {
            val model = IntentModels.all.first()
            repeat(legacy.getInt("calls", 0).coerceIn(0, 10)) {
                insert(db, model, today(), "legacy")
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun snapshot(): CloudState = inTransaction { db -> snapshot(db, today()) }

    fun configure(limits: CloudLimits) = inTransaction { db ->
        db.execSQL(
            "UPDATE policy SET daily=?, monthly=?, timeout=? WHERE id=1",
            arrayOf<Any>(limits.dailyNanoUsd, limits.monthlyNanoUsd, limits.timeoutSeconds),
        )
    }

    fun reserve(model: IntentModel): CloudReservation = inTransaction { db ->
        val day = today()
        IntentModels.checkDate(day)
        val state = snapshot(db, day)
        require(!state.usage.halted) {
            "Unexpected provider usage paused cloud requests. Use local mode; update the app before reconnecting."
        }
        db.rawQuery("SELECT last_day FROM policy WHERE id=1", null).use {
            check(it.moveToFirst())
            require(it.getString(0) <= day.toString()) {
                "Device date moved backwards. Correct the date before using cloud mode."
            }
        }
        require(state.usage.callsToday < 10) {
            "Daily request limit reached (10). Local mode is available."
        }
        val amount = model.reservationNanoUsd
        require(state.usage.todayNanoUsd <= state.limits.dailyNanoUsd - amount) {
            "Daily USD ceiling cannot cover the reservation. Use local mode or review your ceiling in Settings."
        }
        require(state.usage.monthNanoUsd <= state.limits.monthlyNanoUsd - amount) {
            "Monthly USD ceiling cannot cover the reservation. Use local mode or review your ceiling in Settings."
        }
        db.execSQL("UPDATE policy SET last_day=? WHERE id=1", arrayOf(day.toString()))
        insert(db, model, day, "pending")
    }

    /** Settle once. An interrupted/missing/untrusted receipt always keeps its full reservation. */
    fun settle(reservation: CloudReservation, input: Long, output: Long) = inTransaction { db ->
        val amount = reservation.model.cost(input, output)
        db.execSQL(
            "UPDATE requests SET charged=?, state='settled', input_tokens=?, output_tokens=? WHERE id=? AND state='pending'",
            arrayOf<Any>(amount, input, output, reservation.id),
        )
    }

    fun unknown(reservation: CloudReservation) = inTransaction { db ->
        db.execSQL(
            "UPDATE requests SET state='unknown' WHERE id=? AND state='pending'",
            arrayOf(reservation.id),
        )
    }

    fun halt() = inTransaction { db -> db.execSQL("UPDATE policy SET halted=1 WHERE id=1") }

    /** Only the explicit Delete all Songlane data action may discard this accounting. */
    fun erase() {
        check(legacy.edit().clear().commit())
        try {
            inTransaction { db ->
                db.delete("requests", null, null)
                db.execSQL(
                    "UPDATE policy SET daily=500000000, monthly=5000000000, timeout=30, last_day='', halted=0 WHERE id=1"
                )
            }
        } catch (_: CloudRequestFailure) {
            close()
            check(appContext.deleteDatabase(databaseName)) { "Cloud budget could not be deleted" }
        }
    }

    private fun insert(
        db: SQLiteDatabase,
        model: IntentModel,
        day: LocalDate,
        state: String,
    ): CloudReservation {
        val entry = CloudReservation(UUID.randomUUID().toString(), model, model.reservationNanoUsd)
        check(
            db.insertOrThrow(
                "requests",
                null,
                ContentValues().apply {
                    put("id", entry.id)
                    put("provider", model.provider.name)
                    put("model", model.id)
                    put("day", day.toString())
                    put("month", day.toString().take(7))
                    put("reserved", entry.nanoUsd)
                    put("charged", entry.nanoUsd)
                    put("state", state)
                },
            ) != -1L
        )
        return entry
    }

    private fun snapshot(db: SQLiteDatabase, day: LocalDate): CloudState {
        val policy =
            db.rawQuery("SELECT daily, monthly, timeout, halted FROM policy WHERE id=1", null).use {
                check(it.moveToFirst())
                CloudLimits(it.getLong(0), it.getLong(1), it.getInt(2)) to (it.getInt(3) != 0)
            }
        val daily =
            db.rawQuery(
                    "SELECT COALESCE(SUM(charged),0), COUNT(*) FROM requests WHERE day=?",
                    arrayOf(day.toString()),
                )
                .use {
                    check(it.moveToFirst())
                    it.getLong(0) to it.getInt(1)
                }
        val monthly =
            db.rawQuery(
                    "SELECT COALESCE(SUM(charged),0), SUM(CASE WHEN state!='settled' THEN 1 ELSE 0 END) FROM requests WHERE month=?",
                    arrayOf(day.toString().take(7)),
                )
                .use {
                    check(it.moveToFirst())
                    it.getLong(0) to it.getInt(1)
                }
        return CloudState(
            policy.first,
            CloudUsage(daily.first, monthly.first, daily.second, monthly.second, policy.second),
        )
    }

    private fun <T> inTransaction(block: (SQLiteDatabase) -> T): T {
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val result = block(db)
                db.setTransactionSuccessful()
                return result
            } finally {
                db.endTransaction()
            }
        } catch (_: SQLiteException) {
            throw CloudRequestFailure(
                "Cloud budget storage is unavailable. Use local mode; the accounting database has not been reset."
            )
        }
    }
}
