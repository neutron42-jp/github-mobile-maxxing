package dev.maxxing.github.app

import androidx.compose.ui.text.platform.FontLoadResult
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle as SkTextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProviderWithFallback
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CompositeFontSkiaVerificationTest {
    @Test
    fun skiaParagraphUsesPrimaryForLatinAndFallbackForJapanese() {
        val ubuntuFile = File("src/commonMain/composeResources/font/ubuntu_regular.ttf")
        val kaiseiFile = File("src/commonMain/composeResources/font/kaisei_opti_regular.ttf")
        assertTrue(ubuntuFile.exists(), "ubuntu_regular.ttf missing at ${ubuntuFile.absolutePath}")
        assertTrue(kaiseiFile.exists(), "kaisei_opti_regular.ttf missing at ${kaiseiFile.absolutePath}")

        val fontMgr = FontMgr.default
        val ubuntuTypeface = fontMgr.makeFromData(Data.makeFromBytes(ubuntuFile.readBytes()))!!
        val kaiseiTypeface = fontMgr.makeFromData(Data.makeFromBytes(kaiseiFile.readBytes()))!!

        val provider = TypefaceFontProviderWithFallback()
        provider.registerTypeface(ubuntuTypeface, "alias_ubuntu")
        provider.registerTypeface(kaiseiTypeface, "alias_kaisei")

        val collection = FontCollection()
        collection.setDefaultFontManager(fontMgr)
        collection.setAssetFontManager(provider)

        fun measureWidth(text: String, families: Array<String>, explicitTypeface: org.jetbrains.skia.Typeface?): Float {
            val style = SkTextStyle().apply {
                fontSize = 32f
                setFontFamilies(families)
                if (explicitTypeface != null) setTypeface(explicitTypeface)
            }
            val pStyle = ParagraphStyle()
            val p = ParagraphBuilder(pStyle, collection).use { builder ->
                builder.pushStyle(style)
                builder.addText(text)
                builder.build()
            }
            p.layout(10000f)
            val w = p.maxIntrinsicWidth
            p.close()
            return w
        }

        val latinOnly = "Hello World 12345"
        val jpOnly = "日本語テスト漢字ひらがな"

        val wLatinUbuntu = measureWidth(latinOnly, arrayOf("alias_ubuntu"), ubuntuTypeface)
        val wLatinKaisei = measureWidth(latinOnly, arrayOf("alias_kaisei"), kaiseiTypeface)
        val wJpKaisei = measureWidth(jpOnly, arrayOf("alias_kaisei"), kaiseiTypeface)

        val wLatinComposite = measureWidth(latinOnly, arrayOf("alias_ubuntu", "alias_kaisei"), null)
        val wJpComposite = measureWidth(jpOnly, arrayOf("alias_ubuntu", "alias_kaisei"), null)

        assertNotEquals(wLatinUbuntu, wLatinKaisei)
        assertEquals(wLatinUbuntu, wLatinComposite, 0.01f)
        assertEquals(wJpKaisei, wJpComposite, 0.01f)
        val checkRef = FontLoadResult(null, listOf("a", "b"))
        assertEquals(2, checkRef.aliases.size)
    }
}
