# UI strings (`strings.csv`)

Every text the app shows lives in [`strings.csv`](strings.csv): one row per key, one column per language.
At build time `:core:ui:generateAppStrings` turns the file into `GeneratedAppStrings.kt` (under
`core/ui/build/generated/appStrings`), which gives `AppStrings` one property per row. Kotlin code never
contains UI text. It only picks a row, e.g. `strings.settingsTitle` or `strings.pollIntervalMinutes(5)`.

## Format

```csv
key,args,en,ja
# Settings screen
settingsTitle,,Settings & Appearance,設定と外観
pollIntervalMinutes,Int,{0} min,{0} 分
signInCodeCopiedManual,String;String,Code {0} copied. Open {1} in your browser.,コード {0} をコピーしました。ブラウザで {1} を開いてください。
```

- **key**: the property name used in code (lowerCamelCase). Keys must be unique.
- **args**: the argument types, separated by `;` (`String`, `Int` or `Long`). Leave it empty for plain text.
- **en, ja, …**: one column per language. `{0}`, `{1}`, … are replaced by the arguments in order. A
  translation may reorder them, but it must use every one of them.
- A row whose key starts with `#` is a section heading and is ignored.
- An empty cell in a language other than English falls back to the English text.
- A cell may span several lines. Quote it, as spreadsheet apps do. Some texts contain a no-break space
  (U+00A0), e.g. between `HTTP` and the status code, so that the code never wraps to the next line.

The file can be edited in a text editor or in a spreadsheet app (Excel, LibreOffice, Google Sheets).
When saving from a spreadsheet, choose **CSV UTF-8**. The build rejects other encodings, unknown
argument types, missing or extra placeholders, duplicate keys and empty English cells, and reports the line.

### Sections whose English column is a reference

In three sections near the end of the file (errors and sign-in status, values from the GitHub API, and
the activity feed and timeline), the English UI shows GitHub's original text as is: the API value, or the
English message or phrase that the network layer builds. The English cells there are a reference for
translators. Other languages show their translation.

## Adding or changing a text

1. Add a row (or edit the cells) in `strings.csv`.
2. Use the key in code as `strings.<key>`. The build regenerates the properties.
3. Run `./gradlew :core:ui:desktopTest`. `AppStringsCompletenessTest` checks that no key is blank, that
   no placeholder is dropped, and that no Japanese text is identical to English unless the key is
   allowlisted. It also pins every English value to `src/desktopTest/resources/i18n/english-strings.golden.txt`.
   If you meant to change an English text, copy `core/ui/build/english-strings.actual.txt` over the golden file.

## Adding a language

1. Add a column to `strings.csv` with the language code as its header, e.g. `ko` or `pt-BR`, and fill
   it in. Cells you leave empty show English.
2. Add an entry to `AppLanguage` (`core/model/.../ThemeModels.kt`) with that code. The entry name is what
   the settings store, so do not rename existing entries.
3. In `AppStrings.kt`, add an `object` for the language and a branch in `stringsFor`. The compiler points
   to the missing branch.

The language then appears in the settings automatically, shown in its own name (`languageName`).
