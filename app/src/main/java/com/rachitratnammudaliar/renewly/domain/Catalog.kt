package com.rachitratnammudaliar.renewly.domain

/** Categories for grouping and filtering. Stored by [name]. */
enum class Category(val label: String) {
    ENTERTAINMENT("Entertainment"),
    MUSIC("Music"),
    SOFTWARE("Software & AI"),
    CLOUD("Cloud storage"),
    PHONE("Phone & internet"),
    FITNESS("Health & fitness"),
    NEWS("News & reading"),
    EDUCATION("Education"),
    SHOPPING("Shopping & food"),
    FINANCE("Finance"),
    OTHER("Other");

    companion object {
        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Known services, matched by name, so they get a recognisable colour and a sensible
 * category without any internet. Only colours are used; no logos are bundled.
 */
object Brands {

    data class Brand(val color: Long, val category: Category)

    private val known: List<Pair<List<String>, Brand>> = listOf(
        listOf("netflix") to Brand(0xFFE50914, Category.ENTERTAINMENT),
        listOf("youtube") to Brand(0xFFFF0000, Category.ENTERTAINMENT),
        listOf("prime video", "amazon prime", "prime") to Brand(0xFF00A8E1, Category.ENTERTAINMENT),
        listOf("hotstar", "disney") to Brand(0xFF1A2A6C, Category.ENTERTAINMENT),
        listOf("jiocinema") to Brand(0xFFD9008D, Category.ENTERTAINMENT),
        listOf("sonyliv", "sony liv") to Brand(0xFF2B2B2B, Category.ENTERTAINMENT),
        listOf("zee5") to Brand(0xFF8230C6, Category.ENTERTAINMENT),
        listOf("crunchyroll") to Brand(0xFFF47521, Category.ENTERTAINMENT),
        listOf("apple tv") to Brand(0xFF1C1C1E, Category.ENTERTAINMENT),
        listOf("spotify") to Brand(0xFF1DB954, Category.MUSIC),
        listOf("apple music") to Brand(0xFFFA2D48, Category.MUSIC),
        listOf("jiosaavn", "saavn") to Brand(0xFF2BC5B4, Category.MUSIC),
        listOf("gaana") to Brand(0xFFE72C30, Category.MUSIC),
        listOf("chatgpt", "openai") to Brand(0xFF10A37F, Category.SOFTWARE),
        listOf("claude", "anthropic") to Brand(0xFFC15F3C, Category.SOFTWARE),
        listOf("gemini") to Brand(0xFF4C7BF4, Category.SOFTWARE),
        listOf("lovable") to Brand(0xFFFF4F7A, Category.SOFTWARE),
        listOf("github") to Brand(0xFF24292F, Category.SOFTWARE),
        listOf("adobe") to Brand(0xFFFA0F00, Category.SOFTWARE),
        listOf("microsoft", "office 365", "microsoft 365") to Brand(0xFF0078D4, Category.SOFTWARE),
        listOf("canva") to Brand(0xFF00C4CC, Category.SOFTWARE),
        listOf("notion") to Brand(0xFF191919, Category.SOFTWARE),
        listOf("figma") to Brand(0xFFA259FF, Category.SOFTWARE),
        listOf("icloud") to Brand(0xFF3693F3, Category.CLOUD),
        listOf("google one", "google drive") to Brand(0xFF4285F4, Category.CLOUD),
        listOf("dropbox") to Brand(0xFF0061FF, Category.CLOUD),
        listOf("airtel") to Brand(0xFFE40000, Category.PHONE),
        listOf("jio") to Brand(0xFF0F3CC9, Category.PHONE),
        listOf("vodafone", "vi ") to Brand(0xFFEE1C25, Category.PHONE),
        listOf("telia", "tele2", "bite") to Brand(0xFF990AE3, Category.PHONE),
        listOf("linkedin") to Brand(0xFF0A66C2, Category.EDUCATION),
        listOf("duolingo") to Brand(0xFF58CC02, Category.EDUCATION),
        listOf("coursera") to Brand(0xFF0056D2, Category.EDUCATION),
        listOf("udemy") to Brand(0xFFA435F0, Category.EDUCATION),
        listOf("gym", "fitness", "cult") to Brand(0xFFFF5A36, Category.FITNESS),
        listOf("swiggy") to Brand(0xFFFC8019, Category.SHOPPING),
        listOf("zomato") to Brand(0xFFE23744, Category.SHOPPING),
        listOf("bolt") to Brand(0xFF34D186, Category.SHOPPING),
        listOf("wolt") to Brand(0xFF009DE0, Category.SHOPPING),
    )

    fun match(name: String): Brand? {
        val n = " ${name.lowercase().trim()} "
        return known.firstOrNull { (keys, _) -> keys.any { n.contains(it) } }?.second
    }

    private val palette = listOf(
        0xFF0B5FA5, 0xFF7B4DFF, 0xFF00897B, 0xFFB4462B, 0xFF5D4037, 0xFF3949AB, 0xFFAD1457, 0xFF2E7D32,
    )

    /** A stable colour for any name: brand colour if known, otherwise from a fixed palette. */
    fun colorFor(name: String): Long =
        match(name)?.color ?: palette[Math.floorMod(name.trim().lowercase().hashCode(), palette.size)]

    fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
}
