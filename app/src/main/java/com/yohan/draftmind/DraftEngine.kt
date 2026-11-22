package com.yohan.draftmind

import android.content.Context
import org.json.JSONArray

data class Hero(
    val name: String,
    val lanes: List<String>,
    val dmg: String,
    val tags: Set<String>,
    val syn: Set<String>,
    val win: Double,
    val pick: Double,
    val ban: Double,
    /** Official "counter hero" list for this hero, strongest counter first. */
    val counteredBy: List<String>
)

data class Suggestion(val hero: Hero, val score: Double, val reasons: List<String>)

object HeroRepo {
    val LANES = listOf("Gold", "Exp", "Mid", "Jungle", "Roam")

    fun load(context: Context): List<Hero> {
        val text = context.assets.open("heroes.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val list = ArrayList<Hero>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Hero(
                    name = o.getString("name"),
                    lanes = o.getJSONArray("lanes").toList(),
                    dmg = o.optString("dmg", "P"),
                    tags = o.getJSONArray("tags").toList().toSet(),
                    syn = o.getJSONArray("syn").toList().toSet(),
                    win = o.optDouble("win", 50.0),
                    pick = o.optDouble("pick", 0.0),
                    ban = o.optDouble("ban", 0.0),
                    counteredBy = o.optJSONArray("counteredBy")?.toList() ?: emptyList()
                )
            )
        }
        return list.sortedBy { it.name }
    }

    private fun JSONArray.toList(): List<String> = (0 until length()).map { getString(it) }
}

/**
 * The "draft brain". Scores every free hero against the current draft:
 * counters vs enemy picks, synergy with ally picks, missing lanes,
 * frontline / crowd control / damage-type balance.
 */
class DraftEngine(private val heroes: List<Hero>) {

    fun suggest(
        allies: List<Hero>,
        enemies: List<Hero>,
        bans: List<Hero>,
        myLane: String?,
        limit: Int = 5
    ): List<Suggestion> {
        if (allies.isEmpty() && enemies.isEmpty()) return metaPicks(bans, myLane, limit)
        val taken = (allies + enemies + bans).map { it.name }.toSet()
        val filledLanes = assignLanes(allies)
        val missingLanes = HeroRepo.LANES.filter { it !in filledLanes }
        val enemyTag = enemies.flatMap { it.tags }.groupingBy { it }.eachCount()
        fun et(t: String) = enemyTag[t] ?: 0
        val allyHasTank = allies.any { "tank" in it.tags }
        val allyHasCc = allies.any { "cc" in it.tags }
        val allySetup = allies.filter { "setup" in it.tags }
        val allyPhys = allies.count { it.dmg == "P" }
        val allyMagic = allies.count { it.dmg == "M" }

        val pool = heroes.filter { it.name !in taken && (myLane == null || myLane in it.lanes) }

        return pool.map { c ->
            var score = 0.0
            val reasons = ArrayList<String>()

            // 1) Direct counters (official data: who counters whom)
            val beats = enemies.filter { c.name in it.counteredBy }
            val losesTo = enemies.filter { it.name in c.counteredBy }
            beats.forEach { e -> score += weight(e.counteredBy.indexOf(c.name)) }
            losesTo.forEach { e -> score -= weight(c.counteredBy.indexOf(e.name)) }
            if (beats.isNotEmpty()) reasons.add("Counters " + beats.joinToString(", ") { it.name })

            // Current meta strength
            score += (c.win - 50.0) * 0.3
            if (c.win >= 52.0) reasons.add("Strong now: %.1f%% win".format(c.win))

            // 2) Counters by play style
            if (et("dash") > 0 && "antidash" in c.tags) {
                score += 1.5 * minOf(et("dash"), 2); reasons.add("Shuts down dash heroes")
            }
            if ((et("heal") > 0 || et("sustain") >= 2) && "antiheal" in c.tags) {
                score += 1.5; reasons.add("Cuts enemy healing")
            }
            if (et("cc") >= 2 && "purify" in c.tags) {
                score += 1.5; reasons.add("Cleanses heavy enemy CC")
            }
            if (et("tank") >= 2 && "antitank" in c.tags) {
                score += 1.5; reasons.add("Shreds their tanks")
            }
            if (et("dive") >= 2 && "antidive" in c.tags) {
                score += 1.0; reasons.add("Peels enemy divers")
            }
            if (et("immobile") > 0 && "dive" in c.tags) {
                score += 1.0; reasons.add("Can reach their immobile carry")
            }
            if (et("poke") >= 2 && ("dive" in c.tags || "antipoke" in c.tags)) {
                score += 0.5; reasons.add("Handles poke")
            }

            // 3) Synergy with my team
            val combos = allies.filter { it.name in c.syn || c.name in it.syn }
            if (combos.isNotEmpty()) {
                score += 1.5 * combos.size
                reasons.add("Combos with " + combos.joinToString(", ") { it.name })
            }
            if (allySetup.isNotEmpty() && ("burst" in c.tags || "aoe" in c.tags)) {
                score += 1.0; reasons.add("Follows up ${allySetup.first().name}'s setup")
            }

            // 4) Team composition
            if (myLane == null && allies.isNotEmpty()) {
                val fills = c.lanes.firstOrNull { it in missingLanes }
                if (fills != null) { score += 2.0; reasons.add("Fills $fills lane") } else score -= 2.0
            }
            if (allies.size >= 2 && !allyHasTank && "tank" in c.tags) {
                score += 1.5; reasons.add("Team needs a frontline")
            }
            if (allies.size >= 2 && !allyHasCc && "cc" in c.tags) {
                score += 1.0; reasons.add("Adds crowd control")
            }
            if (allies.size >= 2 && allyMagic == 0 && c.dmg == "M") {
                score += 1.0; reasons.add("Adds magic damage")
            }
            if (allies.size >= 2 && allyPhys == 0 && c.dmg == "P") {
                score += 1.0; reasons.add("Adds physical damage")
            }

            if (losesTo.isNotEmpty()) reasons.add("Careful: " + losesTo.joinToString(", ") { it.name } + " counters it")
            Suggestion(c, score, reasons)
        }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** Heroes that are free to pick but countered by 2+ enemy picks. */
    fun avoid(allies: List<Hero>, enemies: List<Hero>, bans: List<Hero>, myLane: String?): List<Pair<Hero, List<Hero>>> {
        val taken = (allies + enemies + bans).map { it.name }.toSet()
        return heroes
            .filter { it.name !in taken && (myLane == null || myLane in it.lanes) }
            .map { c -> c to enemies.filter { it.name in c.counteredBy } }
            .filter { it.second.size >= 2 }
            .sortedByDescending { it.second.size }
            .take(3)
    }

    /** 1st listed counter is the strongest. */
    private fun weight(pos: Int) = 3.0 - 0.4 * pos.coerceIn(0, 4)

    /** Before anyone has picked: strongest heroes right now (also good ban targets). */
    private fun metaPicks(bans: List<Hero>, myLane: String?, limit: Int): List<Suggestion> {
        val banned = bans.map { it.name }.toSet()
        return heroes.filter { it.name !in banned && (myLane == null || myLane in it.lanes) }
            .map { h -> Suggestion(h, (h.win - 50.0) + h.ban * 0.05,
                listOf("%.1f%% win · %.1f%% ban rate".format(h.win, h.ban))) }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** Greedy lane guess for already-picked allies (fewest lane options first). */
    fun assignLanes(allies: List<Hero>): Set<String> {
        val filled = mutableSetOf<String>()
        allies.sortedBy { it.lanes.size }.forEach { h ->
            h.lanes.firstOrNull { it !in filled }?.let { filled.add(it) }
        }
        return filled
    }
}
