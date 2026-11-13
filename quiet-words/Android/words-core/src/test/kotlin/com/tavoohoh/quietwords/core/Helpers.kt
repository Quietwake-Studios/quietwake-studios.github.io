package com.tavoohoh.quietwords.core

import com.tavoohoh.quiet.core.FNV1a

/** Checks every rule of a finished board; returns what's wrong (empty when valid). Port of Helpers.swift. */
fun problems(board: Board, level: Level, blocklist: List<String>): List<String> {
    val issues = mutableListOf<String>()
    val side = board.side
    if (side != level.side) issues += "side $side"
    if (board.letters.size != side * side) issues += "cells"
    val covered = HashSet<Int>()
    val letters = board.letters.map { it.single() }.toCharArray()
    for (placement in board.placements) {
        val cells = placement.cells(side)
        if (placement.direction !in level.directions) issues += "${placement.word.letters} runs ${placement.direction}"
        if (String(cells.map { letters[it] }.toCharArray()) != placement.word.letters) issues += "${placement.word.letters} misspelled"
        val hits = Generator.occurrences(placement.word.letters.toCharArray(), letters, side).size
        if (hits != 1) issues += "${placement.word.letters} reads $hits times"
        covered += cells
    }
    val leftover = (0 until side * side).filter { it !in covered }
    if (leftover != board.hiddenCells) issues += "leftover cells aren't the hidden cells"
    if (String(leftover.map { letters[it] }.toCharArray()) != board.hidden) issues += "leftover letters aren't ${board.hidden}"
    if (board.hidden.length !in level.hiddenLength) issues += "hidden ${board.hidden} length"
    val lines = board.placements.map { it.cells(side).toSet() to it.direction }
    for (blocked in blocklist) {
        for (hit in Generator.occurrences(blocked.toCharArray(), letters, side)) {
            val inside = lines.any { (cells, d) -> (d == hit.direction || d == hit.direction.reversed) && cells.containsAll(hit.cells) }
            if (!inside) issues += "blocked $blocked"
        }
    }
    return issues
}

/** A short fingerprint of a board for reference values (same as Swift's `String(hash, radix: 16)`). */
fun fingerprint(board: Board): String = FNV1a.hash(board.letters.joinToString("") + board.hidden).toString(16)
