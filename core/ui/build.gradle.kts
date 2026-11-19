import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

// ---------------------------------------------------------------------------------------------
// UI strings. i18n/strings.csv holds every UI text (one row per key, one column per language);
// generateAppStrings turns it into GeneratedAppStrings.kt. See i18n/README.md.
// ---------------------------------------------------------------------------------------------

class AppStringsRow(val line: Int, val key: String, val args: List<String>, val values: List<String>)

class AppStringsTable(val languages: List<String>, val rows: List<AppStringsRow>)

val appStringsArgTypes = setOf("String", "Int", "Long")
val appStringsKotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while"
)

/** Decodes strictly as UTF-8 (a spreadsheet may save Shift_JIS or UTF-16), dropping a BOM and normalizing line ends. */
fun decodeAppStringsCsv(bytes: ByteArray, name: String): String {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw GradleException("$name is not valid UTF-8. Save it as \"CSV UTF-8\".", e)
    }
    return text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
}

/** RFC 4180 records: ',' between fields, quoted fields may hold ',', '\n' and '""'. Returns (first line, fields). */
fun parseCsvRecords(text: String, name: String): List<Pair<Int, List<String>>> {
    val records = mutableListOf<Pair<Int, List<String>>>()
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var closedQuote = false
    var line = 1
    var recordLine = 1
    var i = 0
    fun endField() {
        fields.add(field.toString())
        field.clear()
        closedQuote = false
    }
    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> { inQuotes = false; closedQuote = true }
                else -> { if (c == '\n') line++; field.append(c) }
            }
        } else {
            when (c) {
                ',' -> endField()
                '\n' -> {
                    endField()
                    records.add(recordLine to fields)
                    fields = mutableListOf()
                    line++
                    recordLine = line
                }
                '"' -> {
                    if (field.isNotEmpty() || closedQuote) throw GradleException("$name:$line: a '\"' inside an unquoted field. Quote the whole field and double the '\"'.")
                    inQuotes = true
                }
                else -> {
                    if (closedQuote) throw GradleException("$name:$line: text after a closing '\"'. Quote the whole field and double any '\"' inside it.")
                    field.append(c)
                }
            }
        }
        i++
    }
    if (inQuotes) throw GradleException("$name:$recordLine: a quoted field is never closed.")
    if (field.isNotEmpty() || fields.isNotEmpty() || closedQuote) {
        endField()
        records.add(recordLine to fields)
    }
    return records
}

fun parseAppStringsCsv(bytes: ByteArray, name: String): AppStringsTable {
    val records = parseCsvRecords(decodeAppStringsCsv(bytes, name), name)
        .filterNot { (_, fields) -> fields.all { it.isBlank() } }
    val header = records.firstOrNull()?.second?.map { it.trim() } ?: throw GradleException("$name is empty.")
    if (header.size < 3 || header[0] != "key" || header[1] != "args" || header[2] != "en") {
        throw GradleException("$name: the header must be key,args,en[,other languages...], found ${header.joinToString(",")}")
    }
    val languages = header.drop(2)
    languages.forEach { code ->
        if (!Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*").matches(code)) throw GradleException("$name: '$code' is not a language code such as ja or pt-BR.")
    }
    if (languages.toSet().size != languages.size) throw GradleException("$name: a language column appears twice.")
    val placeholder = Regex("""\{(\d+)\}""")
    val seen = mutableMapOf<String, Int>()
    val rows = records.drop(1).mapNotNull { (line, fields) ->
        val key = fields[0].trim()
        if (key.startsWith("#")) return@mapNotNull null
        if (!Regex("[a-z][A-Za-z0-9]*").matches(key) || key in appStringsKotlinKeywords) {
            throw GradleException("$name:$line: '$key' is not a valid key (use lowerCamelCase letters and digits).")
        }
        seen.put(key, line)?.let { throw GradleException("$name:$line: key '$key' is already defined on line $it.") }
        if (fields.size > header.size) throw GradleException("$name:$line: '$key' has ${fields.size} fields but the header has ${header.size}.")
        val args = fields.getOrElse(1) { "" }.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        args.forEach { if (it !in appStringsArgTypes) throw GradleException("$name:$line: '$key' has an unknown argument type '$it' (use ${appStringsArgTypes.joinToString("/")}, separated by ';').") }
        val values = languages.indices.map { fields.getOrElse(it + 2) { "" } }
        if (values[0].isBlank()) throw GradleException("$name:$line: '$key' has no English (en) text.")
        values.forEachIndexed { index, value ->
            if (value.isEmpty()) return@forEachIndexed
            val used = placeholder.findAll(value).map {
                it.groupValues[1].toIntOrNull()
                    ?: throw GradleException("$name:$line: the ${languages[index]} text of '$key' has a placeholder number that is too large: ${it.value}")
            }.toSet()
            val language = languages[index]
            used.filter { it >= args.size }.forEach {
                throw GradleException("$name:$line: the $language text of '$key' uses {$it}, but the args column declares ${args.size} argument(s).")
            }
            args.indices.filter { it !in used }.forEach {
                throw GradleException("$name:$line: the $language text of '$key' is missing {$it}.")
            }
        }
        AppStringsRow(line, key, args, values)
    }
    return AppStringsTable(languages, rows)
}

/** Character types written as \uXXXX in the generated code, so invisible characters stay visible there. */
val invisibleCharTypes: Set<Byte> = setOf(
    Character.CONTROL, Character.FORMAT, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
    Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED, Character.PRIVATE_USE, Character.SURROGATE
)

fun kotlinStringLiteral(value: String): String = buildString {
    append('"')
    for (c in value) {
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '$' -> append("\\$")
            c == '\n' -> append("\\n")
            c == '\t' -> append("\\t")
            c.code < 0x20 || (c.code > 0x7E && Character.getType(c).toByte() in invisibleCharTypes) ->
                append("\\u").append(c.code.toString(16).uppercase().padStart(4, '0'))
            else -> append(c)
        }
    }
    append('"')
}

fun renderGeneratedAppStrings(table: AppStringsTable, sourcePath: String): String = buildString {
    appendLine("// Generated by :core:ui:generateAppStrings from $sourcePath. Do not edit: change the CSV instead.")
    appendLine("@file:Suppress(\"RedundantVisibilityModifier\")")
    appendLine()
    appendLine("package dev.maxxing.github.ui.i18n")
    appendLine()
    appendLine("/** One property per row of $sourcePath; the values come from the column of the given language code. */")
    appendLine("public abstract class GeneratedAppStrings internal constructor(languageCode: String) {")
    appendLine("    private val _texts: Array<String> = GeneratedStringTable.column(languageCode)")
    table.rows.forEachIndexed { index, row ->
        appendLine()
        appendLine("    /** ${kotlinStringLiteral(row.values[0]).removeSurrounding("\"").replace("*/", "* /").replace("/*", "/ *")} */")
        if (row.args.isEmpty()) {
            appendLine("    public val ${row.key}: String get() = _texts[$index]")
        } else {
            val params = row.args.indices.joinToString(", ") { "a$it" }
            appendLine(
                "    public val ${row.key}: (${row.args.joinToString(", ")}) -> String " +
                    "get() = { $params -> formatTemplate(_texts[$index], $params) }"
            )
        }
    }
    appendLine("}")
    appendLine()
    appendLine("internal object GeneratedStringTable {")
    appendLine("    /** Language columns in CSV order. The first (en) also fills the empty cells of the others. */")
    appendLine("    val languageCodes: List<String> = listOf(${table.languages.joinToString(", ") { kotlinStringLiteral(it) }})")
    appendLine()
    appendLine("    /** Keys in CSV order. */")
    appendLine("    val keys: List<String> = listOf(")
    table.rows.forEach { appendLine("        ${kotlinStringLiteral(it.key)},") }
    appendLine("    )")
    appendLine()
    appendLine("    /** Argument types of each key, in CSV order (empty for plain text). */")
    appendLine("    val argTypes: List<List<String>> = listOf(")
    table.rows.forEach { row -> appendLine("        listOf(${row.args.joinToString(", ") { kotlinStringLiteral(it) }}),") }
    appendLine("    )")
    appendLine()
    appendLine("    fun column(code: String): Array<String> = when (code) {")
    table.languages.forEachIndexed { index, code -> appendLine("        ${kotlinStringLiteral(code)} -> column$index()") }
    appendLine("        else -> throw IllegalArgumentException(\"$sourcePath has no column for language '\$code'\")")
    appendLine("    }")
    table.languages.forEachIndexed { index, code ->
        appendLine()
        appendLine("    /** $code */")
        appendLine("    private fun column$index(): Array<String> = arrayOf(")
        table.rows.forEach { row ->
            val value = row.values[index].ifEmpty { row.values[0] }
            appendLine("        ${kotlinStringLiteral(value)},")
        }
        appendLine("    )")
    }
    appendLine("}")
}

val appStringsCsv = layout.projectDirectory.file("i18n/strings.csv")
val appStringsOutDir = layout.buildDirectory.dir("generated/appStrings/kotlin")

val generateAppStrings by tasks.registering {
    description = "Generates GeneratedAppStrings.kt from i18n/strings.csv."
    group = "build"
    inputs.file(appStringsCsv)
    outputs.dir(appStringsOutDir)
    doLast {
        val source = appStringsCsv.asFile
        val table = parseAppStringsCsv(source.readBytes(), source.name)
        val dir = appStringsOutDir.get().asFile
        dir.deleteRecursively()
        val pkg = dir.resolve("dev/maxxing/github/ui/i18n")
        pkg.mkdirs()
        pkg.resolve("GeneratedAppStrings.kt").writeText(renderGeneratedAppStrings(table, "core/ui/i18n/strings.csv"))
    }
}

kotlin {
    androidLibrary {
        namespace = "dev.maxxing.github.core.ui"
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppStrings.map { appStringsOutDir })
        }
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.material3.adaptive.navsuite)
            api(libs.compose.ui)
            api(libs.compose.ui.backhandler)
            api(libs.navigationevent.compose)
            api(libs.compose.adaptive)
            api(libs.compose.adaptive.layout)
            api(libs.compose.adaptive.navigation)
            api(libs.material.kolor)
            api(libs.icons.lucide)
            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
            api(libs.coil.svg)
            api(libs.markdown.renderer)
            api(libs.markdown.renderer.m3)
            api(libs.markdown.renderer.coil3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// IDE sync (Android Studio / IntelliJ) also generates GeneratedAppStrings.kt, so strings.* resolves in a fresh clone.
tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach { dependsOn(generateAppStrings) }
