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

package de.hska.kunde.service

import de.hska.kunde.config.security.CustomUser
import de.hska.kunde.config.security.CustomUserDetailsService
import de.hska.kunde.entity.Adresse
import de.hska.kunde.entity.FamilienstandType.LEDIG
import de.hska.kunde.entity.GeschlechtType.WEIBLICH
import de.hska.kunde.entity.InteresseType.LESEN
import de.hska.kunde.entity.InteresseType.REISEN
import de.hska.kunde.entity.Kunde
import de.hska.kunde.entity.Umsatz
import de.hska.kunde.mail.Mailer
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.remove
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.math.BigDecimal.ONE
import java.net.URL
import java.time.LocalDate
import java.util.Currency
import java.util.Locale.GERMANY
import java.util.UUID.randomUUID

@Tag("service")
@ExtendWith(MockKExtension::class)
@DisplayName("Anwendungskern fuer Kunden testen")
@DisabledOnJre(value = [JAVA_8, JAVA_9, JAVA_10, JAVA_11])
class KundeServiceTest {
    private var mongoTemplate: ReactiveMongoTemplate = mockk()
    private var userDetailsService: CustomUserDetailsService = mockk()
    private val mailer: Mailer = mockk()
    private val service = KundeService(mongoTemplate, userDetailsService, mailer)

    @BeforeEach
    fun beforeEach() {
        clearMocks(mongoTemplate, userDetailsService, mailer)
        assertThat(mongoTemplate).isNotNull()
        assertThat(userDetailsService).isNotNull()
        assertThat(mailer).isNotNull()
    }

    @Test
    @Order(100)
    fun `Immer erfolgreich`() {
        assertThat(true).isTrue()
    }

    @Test
    @Order(200)
    @Disabled
    fun `Noch nicht fertig`() {
        assertThat(false).isFalse()
    }

    // -------------------------------------------------------------------------
    // L E S E N
    // -------------------------------------------------------------------------
    @Nested
    inner class Lesen {
        @Suppress("ClassName")
        @Nested
        inner class `Suche anhand der ID` {
            @ParameterizedTest
            @CsvSource(value = ["$ID_VORHANDEN, $NACHNAME, $USERNAME"])
            @Order(1000)
            fun `Suche mit vorhandener ID`(id: String, nachname: String, username: String) {
                // arrange
                val kundeMock = createKundeMock(id, nachname)
                every { mongoTemplate.findById<Kunde>(id) } returns kundeMock.toMono()

                // act
                val kunde = service.findById(id, username).block()!!

                // assert
                assertThat(kunde.id).isEqualTo(id)
            }

            @ParameterizedTest
            @ValueSource(strings = [ID_NICHT_VORHANDEN])
            @Order(1100)
            fun `Suche mit nicht vorhandener ID`(id: String) {
                // arrange
                every { mongoTemplate.findById<Kunde>(id) } returns Mono.empty()

                // act
                val result = service.findById(id, USERNAME).block()

                // assert
                assertThat(result).isNull()
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [NACHNAME])
        @Order(2000)
        fun `Suche alle Kunden`(nachname: String) {
            // arrange
            val kundeMock = createKundeMock(nachname)
            every { mongoTemplate.findAll<Kunde>() } returns Flux.just(kundeMock)
            val emptyQueryParams = LinkedMultiValueMap<String, String>()

            // act
            val kunden = service.find(emptyQueryParams).collectList().block()!!

            // assert
            assertThat(kunden).isNotEmpty()
        }

        @ParameterizedTest
        @ValueSource(strings = [NACHNAME])
        @Order(2100)
        fun `Suche mit vorhandenem Nachnamen`(nachname: String) {
            // arrange
            val kundeMock = createKundeMock(nachname)
            every { mongoTemplate.find<Kunde>(any()) } returns Flux.just(kundeMock)
            val queryParams = LinkedMultiValueMap(mapOf("nachname" to listOf(nachname.toLowerCase())))

            // act
            val kunden = service.find(queryParams).collectList().block()!!

            // assert
            assertThat(kunden)
                .isNotEmpty()
                .allMatch { kunde -> kunde.nachname == nachname }
        }

        @ParameterizedTest
        @CsvSource(value = ["$ID_VORHANDEN, $NACHNAME, $EMAIL"])
        @Order(2200)
        fun `Suche mit vorhandener Emailadresse`(id: String, nachname: String, email: String) {
            // arrange
            val kundeMock = createKundeMock(id, nachname, email.toLowerCase())
            every { mongoTemplate.find<Kunde>(any()) } returns Flux.just(kundeMock)
            val queryParams = LinkedMultiValueMap(mapOf("email" to listOf(email)))

            // act
            val kunden = service.find(queryParams).collectList().block()!!

            // assert
            assertSoftly { softly ->
                softly.assertThat(kunden).hasSize(1)
                val kunde = kunden[0]
                softly.assertThat(kunde).isNotNull()
                softly.assertThat(kunde.email).isEqualToIgnoringCase(email)
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [EMAIL])
        @Order(2300)
        fun `Suche mit nicht-vorhandener Emailadresse`(email: String) {
            // arrange
            every { mongoTemplate.find<Kunde>(any()) } returns Flux.empty()
            val queryParams = LinkedMultiValueMap(mapOf("email" to listOf(email)))

            // act
            val kunden = service.find(queryParams).collectList().block()!!

            // assert
            assertThat(kunden).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(value = ["$ID_VORHANDEN, $NACHNAME, $EMAIL, $PLZ"])
        @Order(2400)
        fun `Suche mit vorhandener PLZ`(id: String, nachname: String, email: String, plz: String) {
            // arrange
            val kundeMock = createKundeMock(id, nachname, email, plz)
            every { mongoTemplate.find<Kunde>(any()) } returns Flux.just(kundeMock)
            val queryParams = LinkedMultiValueMap<String, String>(mapOf("plz" to listOf(plz)))

            // act
            val kunden = service.find(queryParams).collectList().block()!!

            // assert
            val plzList = kunden.map { it.adresse.plz }
            assertThat(plzList)
                .isNotEmpty()
                .allMatch { p -> p == plz }
        }

        @ParameterizedTest
        @CsvSource(value = ["$ID_VORHANDEN, $NACHNAME, $EMAIL, $PLZ"])
        @Order(2500)
        fun `Suche mit vorhandenem Nachnamen und PLZ`(id: String, nachname: String, email: String, plz: String) {
            // arrange
            val kundeMock = createKundeMock(id, nachname, email, plz)
            every { mongoTemplate.find<Kunde>(any()) } returns Flux.just(kundeMock)
            val queryParams =
                LinkedMultiValueMap(mapOf("nachname" to listOf(nachname.toLowerCase()), "plz" to listOf(plz)))

            // act
            val kunden = service.find(queryParams).collectList().block()!!

            // assert
            assertThat(kunden)
                .isNotEmpty()
                .allMatch { kunde ->
                    kunde.nachname.toLowerCase() == nachname.toLowerCase() &&
                    kunde.adresse.plz == plz
                }
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
            @CsvSource(value = ["$NACHNAME, $EMAIL, $PLZ, $USERNAME, $PASSWORD"])
            @Order(5000)
            fun `Neuen Kunden abspeichern`(args: ArgumentsAccessor) {
                // arrange
                val nachname = args.get<String>(0)
                val email = args.get<String>(1)
                val plz = args.get<String>(2)
                val username = args.get<String>(3)
                val password = args.get<String>(4)

                every { mongoTemplate.exists<Kunde>(Query(Kunde::email isEqualTo email)) } returns false.toMono()
                val userMock = CustomUser(id = null, username = username, password = password)
                val userMockCreated =
                    CustomUser(id = randomUUID().toString(), username = username, password = password)
                every { userDetailsService.create(userMock) } returns userMockCreated.toMono()
                val kundeMock = createKundeMock(null, nachname, email, plz, username, password)
                val kundeResultMock = kundeMock.copy(id = randomUUID().toString())
                every { mongoTemplate.save(kundeMock) } returns kundeResultMock.toMono()
                every { mailer.send(kundeMock) } just Runs

                // act
                val kunde = service.create(kundeMock).block()!!

                // assert
                assertSoftly { softly ->
                    softly.assertThat(kunde.id).isNotNull()
                    softly.assertThat(kunde.nachname).isEqualTo(nachname)
                    softly.assertThat(kunde.email).isEqualTo(email)
                    softly.assertThat(kunde.adresse.plz).isEqualTo(plz)
                    softly.assertThat(kunde.username).isEqualTo(username)
                }
            }

            @ParameterizedTest
            @CsvSource(value = ["$NACHNAME, $EMAIL, $PLZ"])
            @Order(5100)
            fun `Neuer Kunde ohne Benutzerdaten`(nachname: String, email: String, plz: String) {
                // arrange
                val kundeMock = createKundeMock(null, nachname, email, plz)

                // act
                val thrown = catchThrowableOfType(
                    { service.create(kundeMock).block() },
                    InvalidAccountException::class.java)

                // assert
                assertThat(thrown.cause).isNull()
            }

            @ParameterizedTest
            @CsvSource(value = ["$NACHNAME, $EMAIL, $PLZ, $USERNAME, $PASSWORD"])
            @Order(5200)
            fun `Neuer Kunde mit existierender Email`(args: ArgumentsAccessor) {
                // arrange
                val nachname = args.get<String>(0)
                val email = args.get<String>(1)
                val plz = args.get<String>(2)
                val username = args.get<String>(3)
                val password = args.get<String>(4)

                val userMock = CustomUser(id = null, username = username, password = password)
                val userMockCreated =
                    CustomUser(id = randomUUID().toString(), username = username, password = password)
                every { userDetailsService.create(userMock) } returns userMockCreated.toMono()
                every {
                    mongoTemplate.exists<Kunde>(Query(Kunde::email isEqualTo email))
                } returns true.toMono()
                val kundeMock = createKundeMock(null, nachname, email, plz, username, password)

                // act
                val thrown = catchThrowableOfType(
                    { service.create(kundeMock).block()!! },
                    EmailExistsException::class.java)

                // assert
                assertThat(thrown.cause).isNull()
            }
        }

        @Nested
        inner class Aendern {
            @ParameterizedTest
            @CsvSource(value = ["$ID_UPDATE, $NACHNAME, $EMAIL, $PLZ"])
            @Order(6000)
            fun `Vorhandenen Kunden aktualisieren`(id: String, nachname: String, email: String, plz: String) {
                // arrange
                val kundeMock = createKundeMock(id, nachname, email, plz)
                every { mongoTemplate.findById<Kunde>(id) } returns kundeMock.toMono()
                every {
                    mongoTemplate.findOne<Kunde>(Query(Kunde::email isEqualTo email))
                } returns Mono.empty()
                every { mongoTemplate.save(kundeMock) } returns kundeMock.toMono()

                // act
                val kunde = service.update(kundeMock, id, kundeMock.version.toString()).block()!!

                // assert
                assertThat(kunde.id).isEqualTo(id)
            }

            @ParameterizedTest
            @CsvSource(value = ["$ID_NICHT_VORHANDEN, $NACHNAME, $EMAIL, $PLZ, $VERSION"])
            @Order(6100)
            fun `Nicht-existierenden Kunden aktualisieren`(args: ArgumentsAccessor) {
                // arrange
                val id = args.get<String>(0)
                val nachname = args.get<String>(1)
                val email = args.get<String>(2)
                val plz = args.get<String>(3)
                val version = args.get<String>(4)

                val kundeMock = createKundeMock(id, nachname, email, plz)
                every { mongoTemplate.findById<Kunde>(id) } returns Mono.empty()

                // act
                val kunde = service.update(kundeMock, id, version).block()

                // assert
                assertThat(kunde).isNull()
            }

            @ParameterizedTest
            @CsvSource(value = ["$ID_UPDATE, $NACHNAME, $EMAIL, $PLZ, $VERSION_INVALID"])
            @Order(6200)
            fun `Kunde aktualisieren mit falscher Versionsnummer`(args: ArgumentsAccessor) {
                // arrange
                val id = args.get<String>(0)
                val nachname = args.get<String>(1)
                val email = args.get<String>(2)
                val plz = args.get<String>(3)
                val version = args.get<String>(4)

                val kundeMock = createKundeMock(id, nachname, email, plz)
                every { mongoTemplate.findById<Kunde>(id) } returns kundeMock.toMono()

                // act
                val thrown = catchThrowableOfType(
                    { service.update(kundeMock, id, version).block() },
                    InvalidVersionException::class.java)

                // assert
                assertThat(thrown.message).isEqualTo("Falsche Versionsnummer $version")
            }

            @ParameterizedTest
            @CsvSource(value = ["$ID_UPDATE, $NACHNAME, $EMAIL, $PLZ, $VERSION_ALT"])
            @Order(6300)
            fun `Kunde aktualisieren mit alter Versionsnummer`(args: ArgumentsAccessor) {
                // arrange
                val id = args.get<String>(0)
                val nachname = args.get<String>(1)
                val email = args.get<String>(2)
                val plz = args.get<String>(3)
                val version = args.get<String>(4)

                val kundeMock = createKundeMock(id, nachname, email, plz)
                every { mongoTemplate.findById<Kunde>(id) } returns kundeMock.toMono()

                // act
                val thrown = catchThrowableOfType(
                    { service.update(kundeMock, id, version).block() },
                    InvalidVersionException::class.java)

                // assert
                assertThat(thrown.cause).isNull()
            }
        }

        @Nested
        inner class Loeschen {
            @ParameterizedTest
            @CsvSource(value = ["$ID_LOESCHEN, $NACHNAME"])
            @Order(7000)
            fun `Vorhandenen Kunden loeschen`(id: String, nachname: String) {
                // arrange
                val kundeMock = createKundeMock(id, nachname)
                every { mongoTemplate.findById<Kunde>(id) } returns kundeMock.toMono()
                every { mongoTemplate.remove<Kunde>(Query(Kunde::id isEqualTo id)) } returns Mono.empty()

                // act
                val kunde = service.deleteById(id).block()!!

                // assert
                assertThat(kunde.id).isEqualTo(id)
            }

            @ParameterizedTest
            @ValueSource(strings = [ID_LOESCHEN_NICHT_VORHANDEN])
            @Order(7100)
            fun `Nicht-vorhandenen Kunden loeschen`(id: String) {
                // arrange
                every { mongoTemplate.findById<Kunde>(id) } returns Mono.empty()

                // act
                val kunde = service.deleteById(id).block()

                // assert
                assertThat(kunde).isNull()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden fuer Mocking
    // -------------------------------------------------------------------------
    private fun createKundeMock(nachname: String): Kunde = createKundeMock(randomUUID().toString(), nachname)

    private fun createKundeMock(id: String, nachname: String): Kunde = createKundeMock(id, nachname, EMAIL)

    private fun createKundeMock(id: String, nachname: String, email: String) = createKundeMock(id, nachname, email, PLZ)

    private fun createKundeMock(id: String?, nachname: String, email: String, plz: String) =
        createKundeMock(id, nachname, email, plz, null, null)

    @SuppressWarnings("LongParameterList")
    private fun createKundeMock(
        id: String?,
        nachname: String,
        email: String,
        plz: String,
        username: String?,
        password: String?
    ): Kunde {
        val adresse = Adresse(plz = plz, ort = ORT)
        val kunde = Kunde(
            id = id,
            version = 0,
            nachname = nachname,
            email = email,
            newsletter = true,
            umsatz = Umsatz(betrag = ONE, waehrung = WAEHRUNG),
            homepage = URL(HOMEPAGE),
            geburtsdatum = GEBURTSDATUM,
            geschlecht = WEIBLICH,
            familienstand = LEDIG,
            interessen = listOf(LESEN, REISEN),
            adresse = adresse,
            username = USERNAME
        )
        if (username != null && password != null) {
            val customUser = CustomUser(id = null, username = username, password = password)
            kunde.user = customUser
        }
        return kunde
    }

    private companion object {
        const val ID_VORHANDEN = "00000000-0000-0000-0000-000000000001"
        const val ID_NICHT_VORHANDEN = "99999999-9999-9999-9999-999999999999"
        const val ID_UPDATE = "00000000-0000-0000-0000-000000000002"
        const val ID_LOESCHEN = "00000000-0000-0000-0000-000000000005"
        const val ID_LOESCHEN_NICHT_VORHANDEN = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        const val PLZ = "12345"
        const val ORT = "Testort"
        const val NACHNAME = "Test"
        const val EMAIL = "theo@test.de"
        val GEBURTSDATUM: LocalDate = LocalDate.of(2018, 1, 1)
        val WAEHRUNG: Currency = Currency.getInstance(GERMANY)
        const val HOMEPAGE = "https://test.de"
        const val USERNAME = "test"
        const val PASSWORD = "p"
        const val VERSION = "0"
        const val VERSION_INVALID = "!?"
        const val VERSION_ALT = "-1"
    }
}
