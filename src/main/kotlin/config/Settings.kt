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

package de.hska.kunde.config

// import org.apache.kafka.common.utils.AppInfoParser
import org.springframework.boot.Banner
import org.springframework.boot.SpringBootVersion
import org.springframework.core.SpringVersion
import org.springframework.security.core.SpringSecurityCoreVersion
import java.net.InetAddress

/**
 * Singleton-Klasse, um sinnvolle Konfigurationswerte für den Microservice vorzugeben.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
object Settings {
    /**
     * Konstante für das Spring-Profile "dev".
     */
    const val DEV = "dev"

    private const val version = "1.0"

    /**
     * Banner für den Start des Microservice in der Konsole.
     */
    val banner = Banner { _, _, out ->
        out.println(
            """
            |       __                                    _____
            |      / /_  _____  _________ ____  ____     /__  /
            | __  / / / / / _ \/ ___/ __ `/ _ \/ __ \      / /
            |/ /_/ / /_/ /  __/ /  / /_/ /  __/ / / /     / /___
            |\____/\__,_/\___/_/   \__, /\___/_/ /_/     /____(_)
            |                     /____/
            |
            |(C) Juergen Zimmermann, Hochschule Karlsruhe
            |Version          $version
            |Spring Boot      ${SpringBootVersion.getVersion()}
            |Spring Security  ${SpringSecurityCoreVersion.getVersion()}
            |Spring Framework ${SpringVersion.getVersion()}
            |Kotlin           ${KotlinVersion.CURRENT}
            |OpenJDK          ${System.getProperty("java.runtime.version")}
            |Betriebssystem   ${System.getProperty("os.name")}
            |Rechnername      ${InetAddress.getLocalHost().hostName}
            |""".trimMargin("|")
        )
    }

    private val parentPkgName by lazy {
        val pkgName = Settings::class.java.`package`.name
        pkgName.substringBeforeLast('.')
    }

    private val appName = parentPkgName.substringAfterLast('.')

    /**
     * Properties, die berücksichtigt werden, wenn der Microservice in der
     * Konsole gestartet wird.
     */
    val props = mapOf(
        "spring.application.name" to appName,
        "spring.devtools.livereload.enabled" to false,
        "spring.devtools.restart.trigger-file=" to "/restart.txt",
        "spring.profiles.default" to "prod",
        // Functional bean definition Kotlin DSL
        // "context.initializer.classes" to "$parentPkgName.BeansInitializer"

        // siehe org.springframework.boot.autoconfigure.jackson.JacksonProperties
        "spring.jackson.serialization.indent_output" to true,
        // "spring.jackson.default-property-inclusion" to "non_null",

        // -Dreactor.trace.operatorStacktrace=true
        "spring.reactor.stacktrace-mode.enabled" to true,

        "spring.security.user.password" to "p",

        "management.endpoints.web.exposure.include" to "*",
        "management.endpoint.shutdown.enabled" to true,
        "management.endpoint.mappings.enabled" to true,
        "management.endpoint.env.enabled" to true,
        // "management.endpoint.health.enabled" to true,
        // "management.endpoint.health.show-details" to true,

        "spring.jmx.enabled" to false,

        // Microservice soll nicht starten, falls der config-Server nicht erreichbar ist
        "spring.cloud.config.failFast" to true,

        "spring.cloud.consul.discovery.registerHealthCheck" to false,
        "spring.cloud.consul.discovery.scheme" to "https",

        // "spring.zipkin.baseUrl" to "http://localhost:9411",
        "spring.sleuth.sampler.probability" to 1.0,
        "sleuth.sampler.percentage" to 1.0,

        "spring.thymeleaf.cache" to false,
        "spring.thymeleaf.enabled" to true
    )

    // "server.error.whitelabel.enabled" to false,
}
