package com.personal.portfolio.data.remote.quote

import com.personal.portfolio.domain.model.Quote
import java.math.BigDecimal
import java.math.RoundingMode

object TencentQuoteParser {
    /**
     * Parses body like:
     * v_sh600000="1~浦发银行~600000~11.48~11.20~...";
     * Multi-line / multi-symbol supported.
     */
    fun parse(body: String, providerId: String, nowEpochMs: Long = System.currentTimeMillis()): List<Quote> {
        val results = mutableListOf<Quote>()
        val regex = Regex("""v_([a-z]{2}\d+)="([^"]*)"""")
        for (match in regex.findAll(body)) {
            val code = match.groupValues[1]
            val payload = match.groupValues[2]
            if (payload.isBlank()) continue
            val fields = payload.split('~')
            if (fields.size < 5) continue
            val price = fields.getOrNull(3)?.toBdOrNull() ?: continue
            if (price.compareTo(BigDecimal.ZERO) <= 0) continue
            val prevClose = fields.getOrNull(4)?.toBdOrNull()
            val name = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
            val changePct = fields.getOrNull(32)?.toBdOrNull()?.divide(BigDecimal(100), 6, RoundingMode.HALF_UP)
                ?: if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    price.subtract(prevClose).divide(prevClose, 6, RoundingMode.HALF_UP)
                } else null
            results += Quote(
                symbol = code,
                name = name,
                price = price,
                prevClose = prevClose,
                changePct = changePct,
                asOfEpochMs = nowEpochMs,
                delayed = true,
                providerId = providerId
            )
        }
        return results
    }

    private fun String.toBdOrNull(): BigDecimal? =
        runCatching { BigDecimal(trim()) }.getOrNull()
}

object SinaQuoteParser {
    /**
     * Parses:
     * var hq_str_sh600000="浦发银行,开盘,昨收,现价,...";
     * Index: 0 name, 1 open, 2 prevClose, 3 price
     */
    fun parse(body: String, providerId: String, nowEpochMs: Long = System.currentTimeMillis()): List<Quote> {
        val results = mutableListOf<Quote>()
        val regex = Regex("""hq_str_([a-z]{2}\d+)="([^"]*)"""")
        for (match in regex.findAll(body)) {
            val code = match.groupValues[1]
            val payload = match.groupValues[2]
            if (payload.isBlank()) continue
            val fields = payload.split(',')
            if (fields.size < 4) continue
            val price = fields.getOrNull(3)?.toBdOrNull() ?: continue
            if (price.compareTo(BigDecimal.ZERO) <= 0) continue
            val prevClose = fields.getOrNull(2)?.toBdOrNull()
            val name = fields.getOrNull(0)?.takeIf { it.isNotBlank() }
            val changePct = if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
                price.subtract(prevClose).divide(prevClose, 6, RoundingMode.HALF_UP)
            } else null
            results += Quote(
                symbol = code,
                name = name,
                price = price,
                prevClose = prevClose,
                changePct = changePct,
                asOfEpochMs = nowEpochMs,
                delayed = true,
                providerId = providerId
            )
        }
        return results
    }

    private fun String.toBdOrNull(): BigDecimal? =
        runCatching { BigDecimal(trim()) }.getOrNull()
}
