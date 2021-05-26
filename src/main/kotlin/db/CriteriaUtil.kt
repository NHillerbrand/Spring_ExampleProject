/*
 * Copyright (C) 2017 - present Juergen Zimmermann, Hochschule Karlsruhe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.hska.kunde.db

import de.hska.kunde.config.logger
import de.hska.kunde.entity.Adresse
import de.hska.kunde.entity.FamilienstandType
import de.hska.kunde.entity.GeschlechtType
import de.hska.kunde.entity.Kunde
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.CriteriaDefinition
import org.springframework.data.mongodb.core.query.div
import org.springframework.data.mongodb.core.query.gte
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.regex
import org.springframework.util.MultiValueMap

/**
 * Singleton-Klasse, um _Criteria Queries_ für _MongoDB_ zu bauen.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
object CriteriaUtil {
    private const val nachname = "nachname"
    private const val email = "email"
    private const val kategorie = "kategorie"
    private const val plz = "plz"
    private const val ort = "ort"
    private const val umsatzMin = "umsatzmin"
    private const val geschlecht = "geschlecht"
    private const val familienstand = "familienstand"
    private const val interessen = "interessen"
    private val logger = logger()

    /**
     * Eine `MultiValueMap` von _Spring_ wird in eine Liste von
     * `CriteriaDefinition` für _MongoDB_ konvertiert, um flexibel nach Kunden
     * suchen zu können.
     * @param queryParams Die Query-Parameter in einer `MultiValueMap`.
     * @return Eine Liste von `CriteriaDefinition`.
     */
    @Suppress("ComplexMethod", "LongMethod")
    fun getCriteria(queryParams: MultiValueMap<String, String>): List<CriteriaDefinition?> {
        val criteria = queryParams.map { (key, value) ->
            if (value?.size != 1) {
                null
            } else {
                val critVal = value[0]
                when (key) {
                    nachname -> getCriteriaNachname(critVal)
                    email -> getCriteriaEmail(critVal)
                    kategorie -> getCriteriaKategorie(critVal)
                    plz -> getCriteriaPlz(critVal)
                    ort -> getCriteriaOrt(critVal)
                    umsatzMin -> getCriteriaUmsatz(critVal)
                    geschlecht -> getCriteriaGeschlecht(critVal)
                    familienstand -> getCriteriaFamilienstand(critVal)
                    interessen -> getCriteriaInteressen(critVal)
                    else -> null
                }
            }
        }

        logger.debug("#Criteria: {}", criteria.size)
        criteria.forEach { logger.debug("Criteria: {}", it?.criteriaObject) }
        return criteria
    }

    // Nachname: Suche nach Teilstrings ohne Gross-/Kleinschreibung
    private fun getCriteriaNachname(nachname: String) = Kunde::nachname.regex(nachname, "i")

    // Email: Suche mit Teilstring ohne Gross-/Kleinschreibung
    private fun getCriteriaEmail(email: String) = Kunde::email.regex(email, "i")

    private fun getCriteriaKategorie(kategorieStr: String): Criteria? {
        val kategorieVal = kategorieStr.toIntOrNull() ?: return null
        return Kunde::kategorie isEqualTo kategorieVal
    }

    // PLZ: Suche mit Praefix
    private fun getCriteriaPlz(plz: String) = Kunde::adresse / Adresse::plz regex "^$plz"

    // Ort: Suche nach Teilstrings ohne Gross-/Kleinschreibung
    private fun getCriteriaOrt(ort: String): Criteria {
        return (Kunde::adresse / Adresse::ort).regex(ort, "i")
    }

    private fun getCriteriaUmsatz(umsatzStr: String): Criteria? {
        val umsatzVal = umsatzStr.toBigDecimalOrNull() ?: return null
        return Kunde::umsatz gte umsatzVal
    }

    private fun getCriteriaGeschlecht(geschlechtStr: String): Criteria? {
        val geschlechtVal = GeschlechtType.build(geschlechtStr) ?: return null
        return Kunde::geschlecht isEqualTo geschlechtVal
    }

    private fun getCriteriaFamilienstand(familienstandStr: String): Criteria? {
        val familienstandVal = FamilienstandType.build(familienstandStr) ?: return null
        return Kunde::familienstand isEqualTo familienstandVal
    }

    @Suppress("ReturnCount")
    private fun getCriteriaInteressen(interessenStr: String): Criteria? {
        val interessenList = interessenStr
            .split(",".toRegex())
            .dropLastWhile { it.isEmpty() }
        if (interessenList.isEmpty()) {
            return null
        }

        // Interessen mit "and" verknuepfen, falls mehr als 1
        val criteria = interessenList.asSequence()
            .map { Kunde::interessen isEqualTo it }
            .toMutableList()

        val firstCriteria = criteria[0]
        if (criteria.size == 1) {
            return firstCriteria
        }

        criteria.removeAt(0)
        @Suppress("SpreadOperator")
        return firstCriteria.andOperator(*criteria.toTypedArray())
    }
}
