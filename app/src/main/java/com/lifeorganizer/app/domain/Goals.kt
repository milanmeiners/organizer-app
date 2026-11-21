package com.lifeorganizer.app.domain

import com.lifeorganizer.app.data.Goal
import com.lifeorganizer.app.data.GoalStatus

data class GoalProgress(val percent: Int, val calculated: Boolean)

/** A goal with its depth in the hierarchy, used for flattened tree rendering. */
data class GoalNode(val goal: Goal, val depth: Int, val hasChildren: Boolean)

class GoalTree(goals: List<Goal>) {
    private val byId: Map<Long, Goal> = goals.associateBy { it.id }
    private val childrenOf: Map<Long?, List<Goal>> = goals
        .groupBy { if (it.parentId != null && byId.containsKey(it.parentId)) it.parentId else null }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.id })) }
    private val progressCache = mutableMapOf<Long, GoalProgress>()

    fun get(id: Long?): Goal? = id?.let { byId[it] }

    fun roots(): List<Goal> = childrenOf[null].orEmpty()

    fun children(id: Long): List<Goal> = childrenOf[id].orEmpty()

    /** Children that count for progress (archived subgoals are ignored). */
    fun activeChildren(id: Long): List<Goal> = children(id).filter { it.status != GoalStatus.ARCHIVED }

    fun descendants(id: Long): Set<Long> {
        val out = mutableSetOf<Long>()
        val stack = ArrayDeque(children(id))
        while (stack.isNotEmpty()) {
            val g = stack.removeLast()
            if (out.add(g.id)) stack.addAll(children(g.id))
        }
        return out
    }

    /** Path from the root down to (and including) the goal. Safe against corrupted cyclic data. */
    fun path(id: Long): List<Goal> {
        val out = mutableListOf<Goal>()
        val seen = mutableSetOf<Long>()
        var cur = byId[id]
        while (cur != null && seen.add(cur.id)) {
            out.add(0, cur)
            cur = cur.parentId?.let { byId[it] }
        }
        return out
    }

    /** True if making [newParentId] the parent of [goalId] would create a loop. */
    fun wouldCreateCycle(goalId: Long, newParentId: Long?): Boolean {
        if (newParentId == null) return false
        if (newParentId == goalId) return true
        return newParentId in descendants(goalId)
    }

    /**
     * Progress of a goal. Goals with active subgoals get the equal-weight average of their direct children
     * (calculated); leaf goals use the manually entered value (100 if explicitly completed).
     */
    fun progress(id: Long): GoalProgress = progress(id, mutableSetOf())

    private fun progress(id: Long, visiting: MutableSet<Long>): GoalProgress {
        progressCache[id]?.let { return it }
        val goal = byId[id] ?: return GoalProgress(0, false)
        if (!visiting.add(id)) return GoalProgress(0, false)
        val kids = activeChildren(id)
        val result = if (kids.isEmpty()) {
            val p = if (goal.status == GoalStatus.COMPLETED) 100 else (goal.manualProgress ?: 0)
            GoalProgress(p.coerceIn(0, 100), calculated = false)
        } else {
            val sum = kids.sumOf { progress(it.id, visiting).percent }
            GoalProgress(Math.round(sum.toDouble() / kids.size).toInt(), calculated = true)
        }
        visiting.remove(id)
        progressCache[id] = result
        return result
    }

    /**
     * Depth-first flattened tree. Only goals whose status is in [statuses] are shown; a goal's subtree is only
     * walked when it is in [expanded].
     */
    fun flatten(expanded: Set<Long>, statuses: Set<GoalStatus>, rootsOverride: List<Goal>? = null): List<GoalNode> {
        val out = mutableListOf<GoalNode>()
        fun walk(g: Goal, depth: Int) {
            val kids = children(g.id).filter { it.status in statuses }
            out += GoalNode(g, depth, kids.isNotEmpty())
            if (g.id in expanded) kids.forEach { walk(it, depth + 1) }
        }
        (rootsOverride ?: roots()).filter { it.status in statuses }.forEach { walk(it, 0) }
        return out
    }

    /** All goals in tree order with depth (used for parent selectors). Excludes [exclude] ids. */
    fun allInTreeOrder(exclude: Set<Long> = emptySet()): List<GoalNode> {
        val out = mutableListOf<GoalNode>()
        fun walk(g: Goal, depth: Int) {
            if (g.id in exclude) return
            out += GoalNode(g, depth, children(g.id).isNotEmpty())
            children(g.id).forEach { walk(it, depth + 1) }
        }
        roots().forEach { walk(it, 0) }
        return out
    }
}
