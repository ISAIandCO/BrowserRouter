package app.browserrouter

import org.junit.Assert.*
import org.junit.Test

class RuleOrderTest {
    private val rules = listOf("a", "b", "c", "d").map { Rule(id = it, host = "$it.example.com") }
    @Test fun movingInBothDirectionsPreservesRulesAndPriority() {
        val moved = moveRule(rules, "a", 3)
        assertEquals(listOf("b", "c", "d", "a"), moved.map { it.id })
        assertEquals(rules, moveRule(moved, "a", 0))
        assertEquals(rules.toSet(), moved.toSet())
        assertEquals(listOf("a", "b", "c", "d"), rules.map { it.id })
        assertEquals(rules, moveRule(rules, "missing", 1))
        assertEquals(rules, moveRule(rules, "a", -1))
        assertEquals(rules, moveRule(rules, "a", rules.size))
    }
    @Test fun crossingNeighboursHandlesDifferentHeightsAndSkippedCards() {
        val visible = listOf("a" to 50f, "b" to 200f, "c" to 350f, "d" to 550f)
        assertEquals(1, ruleDropTarget(rules, "b", 349f, visible))
        assertEquals(2, ruleDropTarget(rules, "b", 351f, visible))
        assertEquals(3, ruleDropTarget(rules, "a", 600f, visible))
        assertEquals(0, ruleDropTarget(rules, "d", 0f, visible))
        assertEquals(3, ruleDropTarget(rules, "d", 600f, visible))
        assertEquals(-1, ruleDropTarget(rules, "missing", 600f, visible))
    }
    @Test fun staleLayoutDoesNotUndoTheLastMove() {
        val oldLayout = listOf("a" to 50f, "b" to 200f, "c" to 350f, "d" to 550f)
        val moved = moveRule(rules, "b", 2)
        assertEquals(2, ruleDropTarget(moved, "b", 360f, oldLayout))
        val updated = listOf("a" to 50f, "c" to 150f, "b" to 300f, "d" to 550f)
        assertEquals(2, ruleDropTarget(moved, "b", 360f, updated))
        assertEquals(1, ruleDropTarget(moved, "b", 149f, updated))
    }
    @Test fun reorderingChangesTheFirstWinningRoute() {
        val first = Rule(id = "first", host = ".com", action = Action.BROWSER, browser = "org.mozilla.firefox")
        val second = first.copy(id = "second", browser = "app.bearium.browser")
        val ordered = listOf(first, second)
        val link = WebLink.parse("https://example.com")
        val apps = setOf(first.browser!!, second.browser!!)
        assertEquals(Route.Open(first.browser!!, first.id), route(Config(ordered), link, null, apps, "app.browserrouter"))
        assertEquals(Route.Open(second.browser!!, second.id),
            route(Config(moveRule(ordered, first.id, 1)), link, null, apps, "app.browserrouter"))
    }
}
