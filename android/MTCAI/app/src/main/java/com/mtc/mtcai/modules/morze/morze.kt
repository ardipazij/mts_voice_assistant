package com.mtc.mtcai.modules.morze

fun convertToMorse(text: String): String {
    val morseMap = mapOf(
        'а' to ".-", 'б' to "-...", 'в' to ".--", 'г' to "--.", 'д' to "-..", 'е' to ".", 'ё' to ".",
        'ж' to "...-", 'з' to "--..", 'и' to "..", 'й' to ".---", 'к' to "-.-", 'л' to ".-..", 'м' to "--",
        'н' to "-.", 'о' to "---", 'п' to ".--.", 'р' to ".-.", 'с' to "...", 'т' to "-", 'у' to "..-",
        'ф' to "..-.", 'х' to "....", 'ц' to "-.-.", 'ч' to "---.", 'ш' to "----", 'щ' to "--.-",
        'ъ' to "--.--", 'ы' to "-.--", 'ь' to "-..-", 'э' to "..-..", 'ю' to "..--", 'я' to ".-.-",

        'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.",
        'g' to "--.", 'h' to "....", 'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..",
        'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.", 'q' to "--.-", 'r' to ".-.",
        's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
        'y' to "-.--", 'z' to "--..",

        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
    )

    return text.lowercase().map { char ->
        when {
            char == ' ' -> " "
            morseMap.containsKey(char) -> morseMap[char]
            else -> null
        }
    }.filterNotNull().joinToString(" ")
}