package app.browserrouter

fun moveRule(rules: List<Rule>, id: String, target: Int): List<Rule> {
    val from = rules.indexOfFirst { it.id == id }
    if (from < 0 || target !in rules.indices || from == target) return rules
    return rules.toMutableList().apply { add(target, removeAt(from)) }
}

/** Cross a neighbour's centre before moving; this also handles cards of different heights. */
fun ruleDropTarget(rules: List<Rule>, id: String, centre: Float, visible: List<Pair<String, Float>>): Int {
    val from = rules.indexOfFirst { it.id == id }
    if (from < 0) return -1
    val positions = visible.mapNotNull { (key, midpoint) ->
        rules.indexOfFirst { it.id == key }.takeIf { it >= 0 }?.let { it to midpoint }
    }
    // A reorder can arrive before LazyColumn has laid out its new positions.
    if (positions.zipWithNext().any { (a, b) -> a.first >= b.first }) return from
    val crossed = positions.mapNotNull { (index, midpoint) ->
        index.takeIf { (it < from && centre < midpoint) || (it > from && centre > midpoint) }
    }
    return crossed.minOrNull()?.takeIf { it < from } ?: crossed.maxOrNull() ?: from
}
