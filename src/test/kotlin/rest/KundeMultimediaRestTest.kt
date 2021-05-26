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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_10
import org.junit.jupiter.api.condition.JRE.JAVA_11
import org.junit.jupiter.api.condition.JRE.JAVA_8
import org.junit.jupiter.api.condition.JRE.JAVA_9
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType.IMAGE_PNG
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import reactor.kotlin.core.publisher.toMono
import java.nio.file.Files.readAllBytes
import java.nio.file.Paths

@Tag("multimediaRest")
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles(DEV)
@TestPropertySource(locations = ["/rest-test.properties"])
@DisplayName("REST-Schnittstelle fuer File-Upload und -Download testen")
@DisabledOnJre(value = [JAVA_8, JAVA_9, JAVA_10, JAVA_11])
class KundeMultimediaRestTest(@LocalServerPort private val port: Int) {
    private var baseUrl = "$SCHEMA://$HOST:$port"
    private var client = WebClient.builder()
        .filter(basicAuthentication(USERNAME, PASSWORD))
        .baseUrl(baseUrl)
        .build()

    @ParameterizedTest
    @CsvSource("$ID_UPDATE_IMAGE, $IMAGE_FILE_PNG")
    @Order(1000)
    fun `Upload und Download eines Bildes als Binaerdatei`(id: String, imageFile: String) {
        // arrange
        val image = Paths.get("config", "rest", imageFile)
        val bytesUpload = readAllBytes(image)

        // act
        val responseUpload = client.put()
            .uri(MULTIMEDIA_PATH, id)
            .header(CONTENT_TYPE, IMAGE_PNG.toString())
            .body(bytesUpload.toMono())
            .exchange()
            .block()!!

        // assert
        assertThat(responseUpload.statusCode()).isEqualTo(NO_CONTENT)

        val responseDownload = client.get()
            .uri(MULTIMEDIA_PATH, id)
            .accept(IMAGE_PNG)
            .exchange()
            .block()

        assertThat(responseDownload).isNotNull()
        assertThat(responseDownload!!.statusCode()).isEqualTo(OK)
        // ggf. responseDownload.body(toDataBuffers())
    }

    @ParameterizedTest
    @CsvSource("$ID_UPDATE_IMAGE, $IMAGE_FILE_JPG")
    @Order(1100)
    fun `Upload ohne MIME-Type `(id: String, imageFile: String) {
        // arrange
        val image = Paths.get("config", "rest", imageFile)
        val bytesUpload = readAllBytes(image)

        // act
        val responseUpload = client.put()
            .uri(MULTIMEDIA_PATH, id)
            .body(bytesUpload.toMono())
            .exchange()
            .block()!!

        // assert
        assertThat(responseUpload.statusCode()).isEqualTo(BAD_REQUEST)
    }

    private companion object {
        const val SCHEMA = "http"
        const val HOST = "localhost"
        const val MULTIMEDIA_PATH = "/multimedia/{id}"
        const val USERNAME = "admin"
        const val PASSWORD = "p"

        const val ID_UPDATE_IMAGE = "00000000-0000-0000-0000-000000000003"
        const val IMAGE_FILE_PNG = "image.png"
        const val IMAGE_FILE_JPG = "image.jpg"
    }
}
