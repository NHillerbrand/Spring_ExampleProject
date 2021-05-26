/*
 * Copyright (C) 2016 - present Juergen Zimmermann, Hochschule Karlsruhe
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
@file:Suppress("PackageDirectoryMismatch")

package de.hska.kunde.rest

import de.hska.kunde.config.Settings.DEV
import de.hska.kunde.config.logger
import de.hska.kunde.config.security.CustomUser
import de.hska.kunde.entity.Adresse
import de.hska.kunde.entity.GeschlechtType.WEIBLICH
import de.hska.kunde.entity.InteresseType.LESEN
import de.hska.kunde.entity.InteresseType.REISEN
import de.hska.kunde.entity.InteresseType.SPORT
import de.hska.kunde.entity.Kunde
import de.hska.kunde.entity.Kunde.Companion.ID_PATTERN
import de.hska.kunde.entity.Umsatz
import de.hska.kunde.rest.constraints.KundeConstraintViolation
import de.hska.kunde.rest.patch.PatchOperation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_10
import org.junit.jupiter.api.condition.JRE.JAVA_11
import org.junit.jupiter.api.condition.JRE.JAVA_8
import org.junit.jupiter.api.condition.JRE.JAVA_9
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.aggregator.ArgumentsAccessor
import org.junit.jupiter.params.aggregator.get
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.hateoas.EntityModel
import org.springframework.http.HttpHeaders.IF_MATCH
import org.springframework.http.HttpHeaders.IF_NONE_MATCH
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.NOT_MODIFIED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.PRECONDITION_FAILED
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.kotlin.core.publisher.toMono
import java.math.BigDecimal.ONE
import java.net.URL
import java.time.LocalDate
import java.util.Currency

@Tag("rest")
@ExtendWith(SpringExtension::class)
// Alternative zu @ContextConfiguration von Spring
// Default: webEnvironment = MOCK, d.h.
//          Mock Servlet Umgebung anstatt eines Embedded Servlet Containers
@SpringBootTest(webEnvironment = RANDOM_PORT)
// @SpringBootTest(webEnvironment = DEFINED_PORT, ...)
// ggf.: @DirtiesContext, falls z.B. ein Spring Bean modifiziert wurde
@ActiveProfiles(DEV)
@TestPropertySource(locations = ["/rest-test.properties"])
@DisplayName("REST-Schnittstelle fuer Kunden testen")
@DisabledOnJre(value = [JAVA_8, JAVA_9, JAVA_10, JAVA_11])
@Suppress("ClassName")
class KundeRestTest(@LocalServerPort private val port: Int) {
    private var baseUrl = "$SCHEMA://$HOST:$port"

    // WebClient auf der Basis von "Reactor Netty"
    // Alternative: Http Client von Java http://openjdk.java.net/groups/net/httpclient/intro.html
    private var client = WebClient.builder()
        .filter(basicAuthentication(USERNAME, PASSWORD))
        .baseUrl(baseUrl)
        .build()

    @Test
    @Order(100)
    fun `Immer erfolgreich`() {
        assertThat(true).isTrue()
    }

    @Test
    @Disabled("Noch nicht fertig")
    @Order(200)
    fun `Noch nicht fertig`() {
        assertThat(true).isFalse()
    }

    // -------------------------------------------------------------------------
    // L E S E N
    // -------------------------------------------------------------------------
    @Nested
    inner class Lesen {
        @Nested
        inner class `Suche anhand der ID` {
            @ParameterizedTest
            @ValueSource(strings = [ID_VORHANDEN, ID_UPDATE_PUT, ID_UPDATE_PATCH])
            @Order(1000)
            fun `Suche mit vorhandener ID`(id: String) {
                // act
                val kundeModel = client.get()
                    .uri(ID_PATH, id)
                    .retrieve()
                    .bodyToMono<EntityModel<Kunde>>()
                    .block()!!

                // assert
                val kunde = kundeModel.content!!
                logger.debug("Gefundener Kunde = {}", kunde)
                with(kunde) {
                    assertSoftly { softly ->
                        softly.assertThat(nachname).isNotEmpty()
                        softly.assertThat(email).isNotEmpty()
                        softly.assertThat(kundeModel.getLink("self").get().href).endsWith("/$id")
                    }
                }
            }

            @ParameterizedTest
            @CsvSource("$ID_VORHANDEN, 0")
            @Order(1100)
            fun `Suche mit vorhandener ID und vorhandener Version`(
                id: String,
                version: String
            ) {
                // act
                val response = client.get()
                    .uri(ID_PATH, id)
                    .header(IF_NONE_MATCH, version)
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NOT_MODIFIED)
            }

            @ParameterizedTest
            @CsvSource("$ID_VORHANDEN, xxx")
            @Order(1200)
            fun `Suche mit vorhandener ID und falscher Version`(
                id: String,
                version: String
            ) {
                // act
                val kundeModel = client.get()
                    .uri(ID_PATH, id)
                    .header(IF_NONE_MATCH, version)
                    .retrieve()
                    .bodyToMono<EntityModel<Kunde>>()
                    .block()!!

                // assert
                val kunde = kundeModel.content!!
                logger.debug("Gefundener Kunde = {}", kunde)
                with(kunde) {
                    assertSoftly { softly ->
                        softly.assertThat(nachname).isNotEmpty()
                        softly.assertThat(email).isNotEmpty()
                        softly.assertThat(kundeModel.getLink("self").get().href).endsWith("/$id")
                    }
                }
            }

            @ParameterizedTest
            @ValueSource(strings = [ID_INVALID, ID_NICHT_VORHANDEN])
            @Order(1300)
            fun `Suche mit syntaktisch ungueltiger oder nicht-vorhandener ID`(id: String) {
                // act
                val response = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NOT_FOUND)
            }

            @ParameterizedTest
            @CsvSource("$USERNAME, $PASSWORD_FALSCH, $ID_VORHANDEN")
            @Order(1400)
            fun `Suche mit ID, aber falschem Passwort`(
                username: String,
                password: String,
                id: String
            ) {
                // arrange
                val clientFalsch = WebClient.builder()
                    .filter(basicAuthentication(username, password))
                    .baseUrl(baseUrl)
                    .build()

                // act
                val response = clientFalsch.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED)
            }
        }

        @Test
        @Order(2000)
        fun `Suche nach allen Kunden`() {
            // act
            val kundenModel = client.get()
                .retrieve()
                .bodyToFlux<EntityModel<Kunde>>()
                .collectList()
                .block()!!

            // assert
            assertThat(kundenModel).isNotEmpty()
        }

        @ParameterizedTest
        @ValueSource(strings = [NACHNAME])
        @Order(2100)
        fun `Suche mit vorhandenem Nachnamen`(nachname: String) {
            // arrange
            val nachnameLower = nachname.toLowerCase()

            // act
            val kundenModel = client.get()
                .uri {
                    it.path(KUNDE_PATH)
                        .queryParam(NACHNAME_PARAM, nachnameLower)
                        .build()
                }
                .retrieve()
                .bodyToFlux<EntityModel<Kunde>>()
                .collectList()
                .block()!!

            // assert
            assertSoftly { softly ->
                softly.assertThat(kundenModel).isNotEmpty()
                softly.assertThat(kundenModel).allMatch {
                    model -> model.content!!.nachname.toLowerCase() == nachnameLower
                }
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [EMAIL_VORHANDEN])
        @Order(2200)
        fun `Suche mit vorhandener Email`(email: String) {
            // act
            val kunden = client.get()
                .uri {
                    it.path(KUNDE_PATH)
                        .queryParam(EMAIL_PARAM, email)
                        .build()
                }
                .retrieve()
                .bodyToFlux<EntityModel<Kunde>>()
                .collectList()
                .block()!!

            // assert
            assertThat(kunden).isNotEmpty()
            val emails = kunden.map { it.content!!.email }
            assertThat(emails).hasSize(1)
            assertThat(emails[0]).isEqualToIgnoringCase(email)
        }
    }

    // -------------------------------------------------------------------------
    // S C H R E I B E N
    // -------------------------------------------------------------------------
    @Nested
    inner class Schreiben {
        @Nested
        inner class Erzeugen {
            @ParameterizedTest
            @CsvSource(
                "$NEUER_NACHNAME, $NEUE_EMAIL, $NEUES_GEBURTSDATUM, $CURRENCY_CODE, $NEUE_HOMEPAGE, $NEUE_PLZ, " +
                    "$NEUER_ORT, $NEUER_USERNAME"
            )
            @Order(5000)
            fun `Abspeichern eines neuen Kunden`(args: ArgumentsAccessor) {
                // arrange
                val neuerKunde = Kunde(
                    id = null,
                    nachname = args.get<String>(0),
                    email = args.get<String>(1),
                    newsletter = true,
                    geburtsdatum = args.get<LocalDate>(2),
                    umsatz = Umsatz(betrag = ONE, waehrung = Currency.getInstance(args.get<String>(3))),
                    homepage = args.get<URL>(4),
                    geschlecht = WEIBLICH,
                    interessen = listOf(LESEN, REISEN),
                    adresse = Adresse(plz = args.get<String>(5), ort = args.get<String>(6))
                )
                neuerKunde.user = CustomUser(
                    id = null,
                    username = args.get<String>(7),
                    password = "p",
                    authorities = emptyList()
                )

                // act
                val response = client.post()
                    .body(neuerKunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(CREATED)
                        softly.assertThat(headers()).isNotNull()
                        val location = headers().asHttpHeaders().location
                        softly.assertThat(location).isNotNull()
                        val locationStr = location.toString()
                        softly.assertThat(locationStr).isNotEqualTo("")
                        val indexLastSlash = locationStr.lastIndexOf('/')
                        softly.assertThat(indexLastSlash).isPositive()
                        val idStr = locationStr.substring(indexLastSlash + 1)
                        softly.assertThat(idStr).matches((ID_PATTERN))
                        softly.assertThat(bodyToMono<String>().hasElement().block()!!).isFalse()
                    }
                }
            }

            @ParameterizedTest
            @CsvSource(
                "$NEUER_NACHNAME_INVALID, $NEUE_EMAIL_INVALID, $NEUES_GEBURTSDATUM, $NEUE_PLZ_INVALID, $NEUER_ORT"
            )
            @Order(5100)
            fun `Abspeichern eines neuen Kunden mit ungueltigen Werten`(args: ArgumentsAccessor) {
                // arrange
                val neuerKunde = Kunde(
                    id = null,
                    nachname = args.get<String>(0),
                    email = args.get<String>(1),
                    newsletter = true,
                    geburtsdatum = args.get<LocalDate>(2),
                    geschlecht = WEIBLICH,
                    interessen = listOf(LESEN, REISEN),
                    adresse = Adresse(plz = args.get<String>(3), ort = args.get<String>(4))
                )

                // act
                val response = client.post()
                    .body(neuerKunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(BAD_REQUEST)
                        val violations =
                            bodyToFlux<KundeConstraintViolation>().collectList().block()!!
                        softly.assertThat(violations)
                            .hasSize(3)
                            .doesNotHaveDuplicates()
                        val violationMsgPredicate = { msg: String ->
                            msg.contains("ist nicht 5-stellig") ||
                            msg.contains("Bei Nachnamen ist nach einem") ||
                            msg.contains("Die EMail-Adresse")
                        }
                        violations
                            .map { it.message!! }
                            .forEach { msg ->
                                softly.assertThat(msg).matches(violationMsgPredicate)
                            }
                    }
                }
            }

            @ParameterizedTest
            @CsvSource(
                "$NEUER_NACHNAME, $NEUE_EMAIL, $NEUES_GEBURTSDATUM, $CURRENCY_CODE, $NEUE_HOMEPAGE, $NEUE_PLZ, " +
                    "$NEUER_ORT, $NEUER_USERNAME"
            )
            @Order(5200)
            fun `Abspeichern eines neuen Kunden mit vorhandenem Usernamen`(args: ArgumentsAccessor) {
                // arrange
                val neuerKunde = Kunde(
                    id = null,
                    nachname = args.get<String>(0),
                    email = "${args.get<String>(1)}x",
                    newsletter = true,
                    geburtsdatum = args.get<LocalDate>(2),
                    umsatz = Umsatz(betrag = ONE, waehrung = Currency.getInstance(args.get<String>(3))),
                    homepage = args.get<URL>(4),
                    geschlecht = WEIBLICH,
                    interessen = listOf(LESEN, REISEN),
                    adresse = Adresse(plz = args.get<String>(5), ort = args.get<String>(6))
                )
                neuerKunde.user = CustomUser(
                    id = null,
                    username = args.get<String>(7),
                    password = "p",
                    authorities = emptyList()
                )

                // act
                val response = client.post()
                    .body(neuerKunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(BAD_REQUEST)
                        val body = bodyToMono<String>().block()!!
                        softly.assertThat(body).contains("Username")
                    }
                }
            }
        }

        @Nested
        inner class Aendern {
            @ParameterizedTest
            @ValueSource(strings = [ID_UPDATE_PUT])
            @Order(6000)
            fun `Aendern eines vorhandenen Kunden durch Put`(id: String) {
                // arrange
                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val kundeOrig = responseOrig!!
                    .bodyToMono<Kunde>()
                    .block()!!
                assertThat(kundeOrig).isNotNull()
                val kunde = kundeOrig.copy(id = id, email = "${kundeOrig.email}put")

                val etag = responseOrig.headers().asHttpHeaders().eTag
                assertThat(etag).isNotNull()
                val version = etag!!.substring(1, etag.length - 1)
                val versionInt = version.toInt() + 1

                // act
                val response = client.put()
                    .uri(ID_PATH, id)
                    .header(IF_MATCH, versionInt.toString())
                    .body(kunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(NO_CONTENT)
                        softly.assertThat(bodyToMono<String>().hasElement().block()!!).isFalse()
                    }
                }
                // ggf. noch GET-Request, um die Aenderung zu pruefen
            }

            @ParameterizedTest
            @CsvSource(
                value = [
                    "$ID_UPDATE_PUT, $EMAIL_VORHANDEN",
                    "$ID_UPDATE_PATCH, $EMAIL_VORHANDEN"
                ]
            )
            @Order(6100)
            fun `Aendern eines Kunden durch Put und Email existiert`(
                id: String,
                email: String
            ) {
                // arrange
                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val kundeOrig = responseOrig!!
                    .bodyToMono<Kunde>()
                    .block()!!
                assertThat(kundeOrig).isNotNull()
                val kunde = kundeOrig.copy(id = id, email = email)

                val etag = responseOrig.headers().asHttpHeaders().eTag
                assertThat(etag).isNotNull()
                val version = etag!!.substring(1, etag.length - 1)
                val versionInt = version.toInt() + 1

                // act
                val response = client.put()
                    .uri(ID_PATH, id)
                    .header(IF_MATCH, versionInt.toString())
                    .body(kunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(BAD_REQUEST)
                        val body = bodyToMono<String>().block()!!
                        softly.assertThat(body).contains(email)
                    }
                }
            }

            @ParameterizedTest
            @ValueSource(strings = [ID_VORHANDEN, ID_UPDATE_PUT, ID_UPDATE_PATCH])
            @Order(6200)
            fun `Aendern eines Kunden durch Put ohne Version`(id: String) {
                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val kunde = responseOrig!!
                    .bodyToMono<Kunde>()
                    .block()!!
                assertThat(kunde).isNotNull()

                // act
                val response = client.put()
                    .uri(ID_PATH, id)
                    .body(kunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(PRECONDITION_FAILED)
                        val body = bodyToMono<String>().block()!!
                        softly.assertThat(body).contains("Versionsnummer")
                    }
                }
            }

            @ParameterizedTest
            @CsvSource(
                value = [
                    "$ID_UPDATE_PUT, $NEUER_NACHNAME_INVALID, $NEUE_EMAIL_INVALID"
                ]
            )
            @Order(6300)
            fun `Aendern eines Kunden durch Put mit ungueltigen Daten`(
                id: String,
                nachname: String,
                email: String
            ) {
                // arrange
                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val kundeOrig = responseOrig!!
                    .bodyToMono<Kunde>()
                    .block()!!
                assertThat(kundeOrig).isNotNull()
                val kunde = kundeOrig.copy(id = id, nachname = nachname, email = email)

                val etag = responseOrig.headers().asHttpHeaders().eTag
                assertThat(etag).isNotNull()
                val version = etag!!.substring(1, etag.length - 1)
                val versionInt = version.toInt() + 1

                // act
                val response = client.put()
                    .uri(ID_PATH, id)
                    .header(IF_MATCH, versionInt.toString())
                    .body(kunde.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(BAD_REQUEST)
                        val violations = bodyToFlux<KundeConstraintViolation>().collectList().block()!!
                        softly.assertThat(violations).hasSize(2)
                        val violationMsgPredicate = { msg: String ->
                            msg.contains("Nachname") || msg.contains("EMail-Adresse")
                        }
                        violations
                            .map { it.message!! }
                            .forEach { msg ->
                                softly.assertThat(msg).matches(violationMsgPredicate)
                            }
                    }
                }
            }

            @ParameterizedTest
            @CsvSource(value = ["$ID_UPDATE_PATCH, $NEUE_EMAIL"])
            @Order(7000)
            fun `Aendern eines vorhandenen Kunden durch Patch`(
                id: String,
                email: String
            ) {
                // arrange
                val replaceOp = PatchOperation(
                    op = "replace",
                    path = "/email",
                    value = "${email}patch"
                )
                val addOp = PatchOperation(
                    op = "add",
                    path = "/interessen",
                    value = NEUES_INTERESSE.value
                )
                val removeOp = PatchOperation(
                    op = "remove",
                    path = "/interessen",
                    value = ZU_LOESCHENDES_INTERESSE.value
                )
                val operations = listOf(replaceOp, addOp, removeOp)

                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val etag = responseOrig!!.headers().asHttpHeaders().eTag
                assertThat(etag).isNotNull()
                val version = etag!!.substring(1, etag.length - 1)
                val versionInt = version.toInt() + 1

                // act
                val response = client.patch()
                    .uri(ID_PATH, id)
                    .header(IF_MATCH, versionInt.toString())
                    .body(operations.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(NO_CONTENT)
                        softly.assertThat(bodyToMono<String>().hasElement().block()!!).isFalse()
                    }
                }
                // ggf. noch GET-Request, um die Aenderung zu pruefen
            }

            @ParameterizedTest
            @CsvSource(value = ["$ID_UPDATE_PATCH, $NEUE_EMAIL_INVALID"])
            @Order(7100)
            fun `Aendern eines Kunden durch Patch mit ungueltigen Daten`(
                id: String,
                email: String
            ) {
                // arrange
                val replaceOp = PatchOperation(
                    op = "replace",
                    path = "/email",
                    value = email
                )
                val operations = listOf(replaceOp)

                val responseOrig = client.get()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()
                val etag = responseOrig!!.headers().asHttpHeaders().eTag
                assertThat(etag).isNotNull()
                val version = etag!!.substring(1, etag.length - 1)
                val versionInt = version.toInt() + 1

                // act
                val response = client.patch()
                    .uri(ID_PATH, id)
                    .header(IF_MATCH, versionInt.toString())
                    .body(operations.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(BAD_REQUEST)
                        val violations =
                            bodyToFlux<KundeConstraintViolation>().collectList().block()!!
                        softly.assertThat(violations).hasSize(1)
                        softly.assertThat(violations[0].message).contains("EMail-Adresse")
                    }
                }
                // ggf. noch GET-Request, um die Aenderung zu pruefen
            }

            @ParameterizedTest
            @CsvSource(
                value = [
                    "$ID_VORHANDEN, $NEUE_EMAIL_INVALID",
                    "$ID_UPDATE_PUT, $NEUE_EMAIL_INVALID",
                    "$ID_UPDATE_PATCH, $NEUE_EMAIL_INVALID"
                ]
            )
            @Order(7200)
            fun `Aendern eines Kunden durch Patch ohne Versionsnr`(
                id: String,
                email: String
            ) {
                // arrange
                val replaceOp = PatchOperation(
                    op = "replace",
                    path = "/email",
                    value = "${email}patch"
                )
                val operations = listOf(replaceOp)

                // act
                val response = client.patch()
                    .uri(ID_PATH, id)
                    .body(operations.toMono())
                    .exchange()
                    .block()!!

                // assert
                with(response) {
                    assertSoftly { softly ->
                        softly.assertThat(statusCode()).isEqualTo(PRECONDITION_FAILED)
                        val body = bodyToMono<String>().block()!!
                        softly.assertThat(body).contains("Versionsnummer")
                    }
                }
            }
        }

        @Nested
        inner class Loeschen {
            @ParameterizedTest
            @ValueSource(strings = [ID_DELETE])
            @Order(8000)
            fun `Loeschen eines vorhandenen Kunden mit der ID`(id: String) {
                // act
                val response = client.delete()
                    .uri(ID_PATH, id)
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NO_CONTENT)
            }

            @ParameterizedTest
            @ValueSource(strings = [EMAIL_DELETE])
            @Order(8100)
            fun `Loeschen eines vorhandenen Kunden mit Emailadresse`(email: String) {
                // act
                val response = client.delete()
                    .uri {
                        it.path(KUNDE_PATH)
                            .queryParam(EMAIL_PARAM, email)
                            .build()
                    }
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NO_CONTENT)
            }

            @ParameterizedTest
            @ValueSource(strings = [EMAIL_DELETE])
            @Order(8200)
            fun `Loeschen mit nicht-vorhandener Emailadresse`(email: String) {
                // act
                val response = client.delete()
                    .uri {
                        it.path(KUNDE_PATH)
                            .queryParam(EMAIL_PARAM, "${email}xxxx")
                            .build()
                    }
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NO_CONTENT)
            }

            @Test
            fun `Loeschen ohne Emailadresse`() {
                // act
                val response = client.delete()
                    .uri {
                        it.path(KUNDE_PATH)
                            .queryParam(EMAIL_PARAM, null)
                            .build()
                    }
                    .exchange()
                    .block()!!

                // assert
                assertThat(response.statusCode()).isEqualTo(NO_CONTENT)
            }
        }
    }

    private companion object {
        const val SCHEMA = "http"
        const val HOST = "localhost"
        const val KUNDE_PATH = "/"
        const val ID_PATH = "/{id}"
        const val NACHNAME_PARAM = "nachname"
        const val EMAIL_PARAM = "email"

        const val USERNAME = "admin"
        const val PASSWORD = "p"
        const val PASSWORD_FALSCH = "Falsches Passwort!"

        const val ID_VORHANDEN = "00000000-0000-0000-0000-000000000001"
        const val ID_INVALID = "YYYYYYYY-YYYY-YYYY-YYYY-YYYYYYYYYYYY"
        const val ID_NICHT_VORHANDEN = "99999999-9999-9999-9999-999999999999"
        const val ID_UPDATE_PUT = "00000000-0000-0000-0000-000000000002"
        const val ID_UPDATE_PATCH = "00000000-0000-0000-0000-000000000003"
        const val ID_DELETE = "00000000-0000-0000-0000-000000000004"
        const val EMAIL_VORHANDEN = "alpha@hska.edu"
        const val EMAIL_DELETE = "phi@hska.cn"

        const val NACHNAME = "alpha"

        const val NEUE_PLZ = "12345"
        const val NEUE_PLZ_INVALID = "1234"
        const val NEUER_ORT = "Testort"
        const val NEUER_NACHNAME = "Neuernachname"
        const val NEUER_NACHNAME_INVALID = "?!&NachnameUngueltig"
        const val NEUE_EMAIL = "email@test.de"
        const val NEUE_EMAIL_INVALID = "emailungueltig@"
        const val NEUES_GEBURTSDATUM = "2017-01-31"
        const val CURRENCY_CODE = "EUR"
        const val NEUE_HOMEPAGE = "https://test.de"
        const val NEUER_USERNAME = "test"

        val NEUES_INTERESSE = SPORT
        val ZU_LOESCHENDES_INTERESSE = LESEN

        val logger = logger()
    }
}
