package xyz.mpv.rex.ui.preferences

import kotlin.math.abs

/**
 * High-performance fuzzy search engine for settings and preference lookups.
 * Supports:
 * 1. Exact matches and prefix matches.
 * 2. Word-boundary matches and word-prefix matches.
 * 3. Substring matching with position weighting.
 * 4. Subsequence matching (e.g. acronyms like "hwdec" -> "hardware decoding").
 * 5. Typo tolerance via bounded Levenshtein distance (e.g. "subtile" -> "subtitles").
 * 6. Multi-word query token matching.
 */
object FuzzySearch {

    /**
     * Scores how well [query] matches [target].
     * Returns a score > 0 if there is a match, or -1 if no match.
     * Higher score indicates higher relevance.
     */
    fun score(query: String, target: String): Int {
        if (query.isBlank() || target.isBlank()) return -1

        val q = query.trim().lowercase()
        val t = target.trim().lowercase()

        // 1. Exact match
        if (q == t) return 1000

        val qNorm = normalizePhrase(q)
        if (qNorm.isEmpty()) return -1

        val tNorm = normalizePhrase(t)
        if (tNorm.isEmpty()) return -1

        if (qNorm == tNorm) return 980

        // 2. Target starts with query
        if (t.startsWith(q) || tNorm.startsWith(qNorm)) {
            val len = minOf(t.length, tNorm.length)
            return 850 + (100 - len.coerceAtMost(100))
        }

        // 3. Word boundary matches
        val words = tNorm.split(' ')

        if (words.any { it == qNorm || it == q }) {
            return 750
        }

        val prefixWord = words.find { it.startsWith(qNorm) || it.startsWith(q) }
        if (prefixWord != null) {
            return 650 + (50 - prefixWord.length.coerceAtMost(50))
        }

        // 4. Substring match
        val subIndex = if (tNorm.contains(qNorm)) tNorm.indexOf(qNorm) else t.indexOf(q)
        if (subIndex >= 0) {
            return 600 - subIndex.coerceAtMost(50)
        }

        // 5. Multi-token query check
        val qTokens = qNorm.split(' ')
        if (qTokens.size > 1) {
            val availableWords = words.toMutableList()
            var allMatch = true
            var scoreSum = 0
            for (token in qTokens) {
                var bestScore = -1
                var bestIdx = -1
                for (i in availableWords.indices) {
                    val s = wordScore(token, availableWords[i])
                    if (s > bestScore) {
                        bestScore = s
                        bestIdx = i
                    }
                }
                if (bestScore <= 0 || bestIdx < 0) {
                    allMatch = false
                    break
                }
                scoreSum += bestScore
                availableWords.removeAt(bestIdx)
            }
            if (allMatch) {
                return 400 + (scoreSum / qTokens.size)
            }
        }

        // 6. Subsequence match
        val subseq = subsequenceScore(qNorm, t)
        if (subseq > 0) return subseq

        // 7. Typo match against individual words
        val bestTypoScore = words.maxOfOrNull { wordTypoScore(qNorm, it) } ?: -1
        if (bestTypoScore > 0) return bestTypoScore

        // 8. Typo match against target as whole
        if (abs(qNorm.length - tNorm.length) <= 3) {
            val wholeTypoScore = wordTypoScore(qNorm, tNorm)
            if (wholeTypoScore > 0) return wholeTypoScore
        }

        return -1
    }

    private fun normalizePhrase(s: String): String {
        val sb = StringBuilder(s.length)
        var prevSpace = false
        for (c in s) {
            if (!c.isLetterOrDigit()) {
                if (!prevSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    prevSpace = true
                }
            } else {
                sb.append(c)
                prevSpace = false
            }
        }
        return sb.toString().trimEnd()
    }

    private fun wordScore(token: String, word: String): Int {
        if (word == token) return 100
        if (word.startsWith(token)) return 80
        if (token.length >= 2 && word.contains(token)) return 60
        val dist = typoDistance(token, word)
        if (dist > 0) return (55 - (dist * 15)).coerceAtLeast(10)
        return -1
    }

    private fun subsequenceScore(query: String, target: String): Int {
        if (query.length < 2 || target.length < query.length) return -1

        fun match(qIdx: Int, tIdx: Int, consecutive: Int, boundaryMatches: Int, contiguousMatches: Int, bonus: Int): Int {
            if (qIdx == query.length) {
                val meaningfulMatches = boundaryMatches + contiguousMatches
                val minRequired = maxOf(2, (query.length + 1) / 2)
                return if (meaningfulMatches >= minRequired) {
                    260 + bonus.coerceAtMost(140)
                } else {
                    -1
                }
            }
            if (tIdx >= target.length || target.length - tIdx < query.length - qIdx) return -1

            val isBoundary = tIdx == 0 || !target[tIdx - 1].isLetterOrDigit()

            if (query[qIdx] == target[tIdx]) {
                if (qIdx == 0 && !isBoundary) {
                    return match(qIdx, tIdx + 1, 0, boundaryMatches, contiguousMatches, bonus)
                }

                val newConsecutive = consecutive + 1
                val newContiguous = if (newConsecutive > 1) contiguousMatches + 1 else contiguousMatches
                val newBoundary = if (isBoundary) boundaryMatches + 1 else boundaryMatches
                val boundaryBonus = if (isBoundary) 25 else 0
                val matchBonus = 10 + (newConsecutive * 5)
                val takeScore = match(
                    qIdx + 1,
                    tIdx + 1,
                    newConsecutive,
                    newBoundary,
                    newContiguous,
                    bonus + boundaryBonus + matchBonus
                )

                if (!isBoundary) {
                    val skipScore = match(qIdx, tIdx + 1, 0, boundaryMatches, contiguousMatches, bonus)
                    return maxOf(takeScore, skipScore)
                }
                return takeScore
            } else {
                return match(qIdx, tIdx + 1, 0, boundaryMatches, contiguousMatches, bonus)
            }
        }

        return match(0, 0, 0, 0, 0, 0)
    }

    private fun typoDistance(query: String, word: String): Int {
        val qLen = query.length
        if (qLen < 3) return -1

        val maxAllowedDistance = when {
            qLen <= 4 -> 1
            qLen <= 8 -> 2
            else -> 3
        }

        val dist = boundedLevenshtein(query, word, maxAllowedDistance)
        return if (dist in 1..maxAllowedDistance) dist else -1
    }

    private fun wordTypoScore(query: String, word: String): Int {
        val dist = typoDistance(query, word)
        return if (dist > 0) 320 - (dist * 70) else -1
    }

    private fun boundedLevenshtein(s1: String, s2: String, maxLimit: Int): Int {
        if (abs(s1.length - s2.length) > maxLimit) return -1
        val m = s1.length
        val n = s2.length

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost // substitution
                )
                minInRow = minOf(minInRow, curr[j])
            }
            if (minInRow > maxLimit) return -1
            val temp = prev
            prev = curr
            curr = temp
        }
        return if (prev[n] <= maxLimit) prev[n] else -1
    }
}
