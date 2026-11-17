package dev.maxxing.github.ui.i18n

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Walks every abstract string property of [AppStrings] with reflection, so a new key cannot ship blank,
 * untranslated or with a dropped placeholder without failing a test.
 */
class AppStringsCompletenessTest {

    /** Keys whose Japanese value is intentionally identical to English (product names, code, samples). */
    private val identicalInJapaneseAllowlist = setOf(
        "reasonCi",                    // "CI"
        "orgBadge",                    // "Org"
        "subjectIssue",                // "Issue" (GitHub term kept in Japanese UI)
        "repoDetailTabActions",        // "Actions" (product name)
        "repoHeadLabel",               // "Head" (git ref role)
        "repoBaseLabel",               // "Base" (git ref role)
        "fontPreviewPrimarySample",    // Latin pangram sample
        "fontPreviewSecondarySample",  // mixed sample text
        "fileDefaultCreateCommitMsg",  // commit messages stay English
        "fileDefaultUpdateCommitMsg"
    )

    private val stringSentinels = listOf("zqa1", "zqb2", "zqc3")
    private val intSentinels = listOf(7301, 7302, 7303)
    private val longSentinels = listOf(9301L, 9302L, 9303L)

    private val keyGetters: List<Method> = AppStrings::class.java.declaredMethods
        .filter { Modifier.isAbstract(it.modifiers) && it.parameterCount == 0 && it.name.startsWith("get") }
        .sortedBy { it.name }

    private fun keyName(getter: Method) = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }

    private fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as Class<*>
        is WildcardType -> rawClass(type.lowerBounds.firstOrNull() ?: type.upperBounds.first())
        else -> error("Unsupported type $type")
    }

    /** Renders a key to text: plain strings as-is, lambdas invoked with sentinel arguments. */
    private fun render(strings: AppStrings, getter: Method): Pair<String, List<String>> {
        val value = getter.invoke(strings)
        if (value is String) return value to emptyList()
        val generic = getter.genericReturnType as? ParameterizedType
            ?: fail("${keyName(getter)} is neither String nor a function type")
        val paramTypes = generic.actualTypeArguments.dropLast(1).map(::rawClass)
        var s = 0; var i = 0; var l = 0
        val args = paramTypes.map { type ->
            when (type) {
                String::class.java -> stringSentinels[s++]
                Integer::class.java -> intSentinels[i++]
                java.lang.Long::class.java -> longSentinels[l++]
                else -> fail("${keyName(getter)} has an unsupported parameter type $type")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val text = when (args.size) {
            1 -> (value as (Any) -> Any)(args[0])
            2 -> (value as (Any, Any) -> Any)(args[0], args[1])
            3 -> (value as (Any, Any, Any) -> Any)(args[0], args[1], args[2])
            else -> fail("${keyName(getter)} has ${args.size} parameters")
        }
        return text as String to args.map { it.toString() }
    }

    @Test
    fun reflectionSeesEveryKey() {
        assertTrue(keyGetters.size > 600, "expected the full key set, found ${keyGetters.size}")
    }

    @Test
    fun everyKeyIsNonBlankAndKeepsEveryPlaceholderInBothLanguages() {
        val problems = mutableListOf<String>()
        for (strings in listOf(EnglishStrings, JapaneseStrings)) {
            for (getter in keyGetters) {
                val (text, args) = render(strings, getter)
                val who = "${strings::class.simpleName}.${keyName(getter)}"
                if (text.isBlank()) problems += "$who is blank"
                args.filterNot { text.contains(it) }.forEach { problems += "$who dropped argument $it: \"$text\"" }
            }
        }
        assertEquals(emptyList(), problems)
    }

    /**
     * Pins every English value (lambdas rendered with sentinel arguments) to a golden file, so the English UI
     * cannot drift from upstream wording unnoticed. When an English change is intended, copy
     * build/english-strings.actual.txt over src/desktopTest/resources/i18n/english-strings.golden.txt.
     */
    @Test
    fun englishValuesMatchTheGoldenFile() {
        val actual = keyGetters.joinToString(separator = "\n", postfix = "\n") { getter ->
            val text = render(EnglishStrings, getter).first.replace("\\", "\\\\").replace("\n", "\\n")
            "${keyName(getter)}\t$text"
        }
        java.io.File("build/english-strings.actual.txt").apply { parentFile?.mkdirs() }.writeText(actual)
        val golden = javaClass.getResource("/i18n/english-strings.golden.txt")?.readText()
            ?: fail("missing golden file src/desktopTest/resources/i18n/english-strings.golden.txt")
        val goldenLines = golden.lines().filter { it.isNotEmpty() }.toSet()
        val actualLines = actual.lines().filter { it.isNotEmpty() }.toSet()
        assertEquals(
            emptyList(),
            (goldenLines - actualLines).map { "- $it" } + (actualLines - goldenLines).map { "+ $it" },
            "English strings differ from the golden file (- golden, + current)"
        )
    }

    @Test
    fun japaneseValuesDifferFromEnglishUnlessAllowlisted() {
        val identical = keyGetters
            .filter { render(JapaneseStrings, it).first == render(EnglishStrings, it).first }
            .map(::keyName)
            .toSet()
        assertEquals(
            emptySet(),
            identical - identicalInJapaneseAllowlist,
            "Japanese value equals English (untranslated?)"
        )
        assertEquals(
            emptySet(),
            identicalInJapaneseAllowlist - identical,
            "allowlisted keys that are now translated should be removed from the allowlist"
        )
    }

    @Test
    fun japaneseTextUsesSignInAndConsistentTerminology() {
        val japanese = keyGetters.map { render(JapaneseStrings, it).first }
        val banned = mapOf(
            "ログイン" to "use サインイン",
            "貢献者" to "use コントリビューター",
            "ドラフト" to "use 下書き",
            "レビュワー" to "use レビュアー",
            "署名済み" to "Verified is 検証済み",
            "個人トークン" to "use パーソナルアクセストークン",
            "割り当て" to "use アサイン",
            "担当" to "use アサイン"
        )
        val hits = japanese.flatMap { text -> banned.keys.filter { text.contains(it) }.map { "$it in \"$text\" (${banned[it]})" } }
        assertEquals(emptyList(), hits)
    }
}
