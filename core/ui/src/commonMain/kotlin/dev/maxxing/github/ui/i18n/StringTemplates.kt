package dev.maxxing.github.ui.i18n

/**
 * Fills the `{0}`, `{1}`, ... placeholders of a strings.csv text with [args]. Any other brace is kept as is,
 * and the build already checked that every placeholder has an argument.
 */
internal fun formatTemplate(template: String, vararg args: Any): String {
    val out = StringBuilder(template.length + 16)
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c == '{') {
            var end = i + 1
            while (end < template.length && template[end].isAsciiDigit()) end++
            if (end > i + 1 && end < template.length && template[end] == '}') {
                val index = template.substring(i + 1, end).toIntOrNull()
                if (index != null && index < args.size) {
                    out.append(args[index].toString())
                    i = end + 1
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
