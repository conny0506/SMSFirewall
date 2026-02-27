package com.example.smsfirewall

data class SpamInsight(
    val score: Int,
    val reasons: List<String>
)

fun buildSpamInsight(
    sender: String,
    body: String,
    blockedWords: List<String>
): SpamInsight {
    val normalizedBody = body.lowercase()
    val matchedWords = blockedWords.filter { word ->
        word.isNotBlank() && normalizedBody.contains(word.lowercase())
    }
    val reasons = mutableListOf<String>()
    var score = 12

    if (matchedWords.isNotEmpty()) {
        val previewWords = matchedWords.take(2).joinToString(", ")
        val extraCount = matchedWords.size - 2
        val label = if (extraCount > 0) {
            "Engellenen kelime: $previewWords (+$extraCount)"
        } else {
            "Engellenen kelime: $previewWords"
        }
        reasons.add(label)
        score += 40
    }

    if (containsUrl(body)) {
        reasons.add("Baglanti iceriyor")
        score += 18
    }

    if (sender.length <= 5) {
        reasons.add("Kisa numaradan gonderildi")
        score += 12
    }

    val digitRatio = body.count { it.isDigit() }.toFloat() / body.length.coerceAtLeast(1)
    if (digitRatio > 0.35f) {
        reasons.add("Yuksek sayi orani")
        score += 10
    }

    if (body.length >= 25 && body.uppercase() == body) {
        reasons.add("Tamami buyuk harf")
        score += 8
    }

    val clamped = score.coerceIn(0, 100)
    if (reasons.isEmpty()) {
        reasons.add("Varsayilan spam filtresi")
    }

    return SpamInsight(
        score = clamped,
        reasons = reasons
    )
}

private fun containsUrl(body: String): Boolean {
    val regex = Regex("(https?://|www\\.)", RegexOption.IGNORE_CASE)
    return regex.containsMatchIn(body)
}
