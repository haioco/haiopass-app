package com.haio.bypass.ui.util

import java.util.Calendar

object JalaliDate {

    private val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val jalaliMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    data class JDate(val year: Int, val month: Int, val day: Int)

    fun convert(gYear: Int, gMonth: Int, gDay: Int): JDate {
        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm > 1 && (gy % 4 == 0 && gy % 100 != 0 || gy % 400 == 0)) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79

        var jNp = jDayNo / 12053
        jDayNo %= 12053

        var jYear = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jYear += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jMonth = 0
        var jDay: Int
        while (true) {
            val days = if (jMonth < 6) 31 else if (jMonth < 11) 30 else {
                val isLeap = (jYear - 979) % 33 % 4 == 0 && ((jYear - 979) % 33 % 4 == 0 || (jYear - 979) % 33 != 0)
                if (isLeap) 30 else 29
            }
            if (jDayNo < days) {
                jDay = jDayNo + 1
                break
            }
            jDayNo -= days
            jMonth++
        }

        return JDate(jYear, jMonth + 1, jDay)
    }

    fun parseGregorian(isoString: String): JDate? {
        return try {
            val datePart = isoString.substringBefore("T").take(10)
            val parts = datePart.split("-")
            if (parts.size < 3) return null
            convert(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: Exception) {
            null
        }
    }

    fun format(isoString: String): String {
        val jd = parseGregorian(isoString) ?: return isoString.take(19)
        val monthName = jalaliMonthNames.getOrElse(jd.month - 1) { jd.month.toString() }
        return "${jd.day} $monthName ${jd.year}"
    }

    fun formatMillis(millis: Long): String {
        return try {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val jd = convert(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            val monthName = jalaliMonthNames.getOrElse(jd.month - 1) { jd.month.toString() }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            "${jd.day} $monthName ${jd.year}  ${String.format("%02d:%02d", hour, min)}"
        } catch (_: Exception) {
            "نامشخص"
        }
    }
}
