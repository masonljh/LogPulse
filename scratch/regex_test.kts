fun main() {
    val patternString = "Order {id} started"
    val idPlaceholder = "{id}"
    
    val escaped = Regex.escape(patternString)
    val placeholderEscaped = Regex.escape(idPlaceholder)
    
    println("Escaped: $escaped")
    println("Placeholder Escaped: $placeholderEscaped")
    
    val regexString = escaped.replace(placeholderEscaped, "(?<id>.*?)")
    println("Regex String: $regexString")
    
    val regex = Regex(regexString)
    val message = "Order flow_001 started"
    val match = regex.find(message)
    
    println("Match: ${match?.value}")
    println("Extracted ID: ${match?.groups?.get("id")?.value}")
}

main()
