package dev.maxxing.github.app

/**
 * Encodes this target as a String content key ("owner/repo#number") for pane scaffold navigators.
 * The navigator saves its content keys into the Android saved-instance Bundle, which cannot hold
 * [IssuePullTarget]. [IssuePullTarget.hintTitle] is deliberately not encoded.
 */
internal fun IssuePullTarget.toPaneKey(): String = "$owner/$repo#$number"

/**
 * Decodes a key produced by [toPaneKey]. Returns null for malformed keys. The decoded target has
 * no hint title.
 */
internal fun issuePullTargetFromPaneKey(key: String): IssuePullTarget? {
    val hashIndex = key.lastIndexOf('#')
    if (hashIndex < 0) return null
    val number = key.substring(hashIndex + 1).toIntOrNull()?.takeIf { it > 0 } ?: return null
    val path = key.substring(0, hashIndex)
    val slashIndex = path.indexOf('/')
    if (slashIndex < 0) return null
    val owner = path.substring(0, slashIndex)
    val repo = path.substring(slashIndex + 1)
    if (owner.isBlank() || repo.isBlank() || '/' in repo) return null
    return IssuePullTarget(owner = owner, repo = repo, number = number)
}
