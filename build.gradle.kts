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

//  Aufrufe
//  1) Microservice uebersetzen und starten
//        .\gradlew -t
//        .\gradlew compileKotlin
//        .\gradlew compileTestKotlin
//
//  2) Microservice als selbstausfuehrendes JAR erstellen und ausfuehren
//        .\gradlew bootJar
//        java -jar build/libs/....jar --spring.profiles.active=dev
//
//  3) Tests und QS
//        .\gradlew test [--rerun-tasks] [--fail-fast] jacocoTestReport
//        .\gradlew check -x test
//        .\gradlew ktlint detekt
//        .\gradlew sonarqube -x test
//          SonarQube-Plugin funktioniert nicht mit Java 12
//        .\gradlew cleanTest
//        .\gradlew build
//
//  4) Sicherheitsueberpruefung durch OWASP Dependency Check
//        .\gradlew dependencyCheckAnalyze --info
//        .\gradlew dependencyCheckUpdate --info
//
//  5) "Dependencies Updates"
//        .\gradlew dependencyUpdates
//        .\gradlew versions
//
//  6) API-Dokumentation erstellen (funktioniert NICHT mit proxy.hs-karlruhe.de, sondern nur mit proxyads)
//        .\gradlew dokka
//
//  7) Entwicklerhandbuch in "Software Engineering" erstellen
//        .\gradlew asciidoctor asciidoctorPdf
//
//  8) Projektreport erstellen
//        .\gradlew projectReport
//        .\gradlew -q dependencyInsight --dependency spring-kafka
//        .\gradlew dependencies
//        .\gradlew dependencies --configuration runtimeOnly
//        .\gradlew buildEnvironment
//        .\gradlew htmlDependencyReport
//
//  9) Report ueber die Lizenzen der eingesetzten Fremdsoftware
//        .\gradlew generateLicenseReport
//
//  10) Abhaengigkeitsgraph
//        .\gradlew generateDependencyGraph
//
//  11)Docker-Image
//        .\gradlew jib
//
//  12) Daemon abfragen und stoppen
//        .\gradlew --status
//        .\gradlew --stop
//
//  13) Verfuegbare Tasks auflisten
//        .\gradlew tasks
//
//  14) Properties auflisten
//        .\gradlew properties
//        .\gradlew dependencyManagementProperties
//
//  15) Hilfe einschl. Typinformation
//        .\gradlew help --task bootRun
//
//  16) Initialisierung des Gradle Wrappers in der richtigen Version
//      (dazu ist ggf. eine Internetverbindung erforderlich)
//        gradle wrapper --gradle-version 5.4.1 --distribution-type=all

// https://github.com/gradle/kotlin-dsl/tree/master/samples
// https://docs.gradle.org/current/userguide/kotlin_dsl.html
// https://docs.gradle.org/current/userguide/task_configuration_avoidance.html
// https://guides.gradle.org/migrating-build-logic-from-groovy-to-kotlin/

import org.asciidoctor.gradle.jvm.pdf.AsciidoctorPdfTask
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.jetbrains.dokka.gradle.DokkaTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

buildscript {
    dependencies {
        extra["kotlin.version"] = Versions.kotlin
        classpath(kotlin("gradle-plugin", Versions.Plugins.kotlin))
        classpath(kotlin("allopen", Versions.Plugins.allOpen))
        classpath(kotlin("noarg", Versions.Plugins.noArg))

        extra["spring-boot.version"] = Versions.springBoot
        classpath("org.springframework.boot:spring-boot-gradle-plugin:${Versions.Plugins.springBoot}")

        //classpath("org.owasp:dependency-check-gradle:${Versions.Plugins.owaspDependencyCheck}")
        //classpath("gradle.plugin.com.vanniktech:gradle-dependency-graph-generator-plugin:0.5.0")
    }
}

plugins {
    idea
    jacoco
    `project-report`

    // FIXME https://youtrack.jetbrains.com/issue/KT-30227
    // https://dl.bintray.com/kotlin/kotlin-dev/org/jetbrains/kotlin/kotlin-compiler
    kotlin("jvm") version Versions.Plugins.kotlin
    // fuer Spring Beans
    id("org.jetbrains.kotlin.plugin.allopen") version Versions.Plugins.allOpen
    // fuer @ConfigurationProperties mit "data class"
    id("org.jetbrains.kotlin.plugin.noarg") version Versions.Plugins.noArg
    // fuer spring-boot-configuration-processor
    //id("org.jetbrains.kotlin.kapt") version Versions.Plugins.kapt

    id("com.adarshr.test-logger") version Versions.Plugins.testLogger

    // https://github.com/ben-manes/gradle-versions-plugin
    // https://github.com/ben-manes/gradle-versions-plugin/issues/263
    // https://github.com/ben-manes/gradle-versions-plugin/issues/270
    id("com.github.ben-manes.versions") version Versions.Plugins.versions

    // https://github.com/nwillc/vplugin
    id("com.github.nwillc.vplugin") version Versions.Plugins.vplugin

    //extra["spring-boot.version"] = Versions.springBoot
    //id("org.springframework.boot") version Versions.Plugins.springBoot

    // FIXME https://github.com/arturbosch/detekt/issues/1306
    // FIXME https://github.com/arturbosch/detekt/issues/1307
    // https://github.com/arturbosch/detekt/pull/1350
    id("io.gitlab.arturbosch.detekt") version Versions.Plugins.detekt

    // http://redirect.sonarsource.com/doc/gradle.html
    id("org.sonarqube") version Versions.Plugins.sonarqube

    id("org.jetbrains.dokka") version Versions.Plugins.dokka

    // https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin
    id("com.google.cloud.tools.jib") version Versions.Plugins.jib

    id("com.fizzpod.sweeney") version Versions.Plugins.sweeney

    id("org.owasp.dependencycheck") version Versions.Plugins.owaspDependencyCheck

    id("org.asciidoctor.jvm.convert") version Versions.Plugins.asciidoctorConvert
    id("org.asciidoctor.jvm.pdf") version Versions.Plugins.asciidoctorPdf

    // https://github.com/vanniktech/gradle-dependency-graph-generator-plugin
    // FIXME https://github.com/nidi3/graphviz-java/issues/86
    // FIXME https://github.com/vanniktech/gradle-dependency-graph-generator-plugin/issues/77
    //id("com.vanniktech.dependency.graph.generator") version "0.5.0"

    // https://github.com/intergamma/gradle-zap
    id("net.intergamma.gradle.gradle-zap-plugin") version Versions.Plugins.zap

    // https://github.com/jk1/Gradle-License-Report
    id("com.github.jk1.dependency-license-report") version Versions.Plugins.jk1DependencyLicenseReport

    // https://github.com/jaredsburrows/gradle-license-plugin
    //id("com.jaredsburrows.license") version Versions.Plugins.jaredsBurrowsLicense
}

apply(plugin = "org.springframework.boot")
//apply(plugin = "org.owasp.dependencycheck")
//apply(plugin = "com.vanniktech.dependency.graph.generator")

defaultTasks = mutableListOf("bootRun")
group = "de.hska"
version = "1.0"

repositories {
    mavenCentral()
    maven("http://dl.bintray.com/kotlin/kotlin-eap")
    // https://github.com/spring-projects/spring-framework/wiki/Spring-repository-FAQ
    // https://github.com/spring-projects/spring-framework/wiki/Release-Process
    maven("http://repo.spring.io/libs-milestone")
    //maven("http://repo.spring.io/libs-milestone-local")
    maven("http://repo.spring.io/release")

    mavenCentral()
    //jcenter()
    //google()

    // Snapshots von Spring Framework, Spring Data, Spring Security und Spring Cloud
    maven("http://repo.spring.io/libs-snapshot")
    // Snapshots von JaCoCo
    //maven("https://oss.sonatype.org/content/repositories/snapshots")
}

/**
 * Configuration-Objekt für PlantUML, um Artifakte zu definieren, die in der ANT-Task benötigt werden
 */
val plantumlCfg: Configuration by configurations.creating

/**
 * Configuration-Objekt für ktlint, um Artifakte zu definieren, die in der JavaExec-Task benötigt werden
 */
val ktlintCfg: Configuration by configurations.creating {
    exclude(module = "ktlint-test")
    exclude(group = "org.apache.maven")
}

// https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation
dependencies {
    // https://info.michael-simons.eu/2018/07/15/spring-boots-configuration-metadata-with-kotlin
    //kapt("org.springframework.boot:spring-boot-configuration-processor")

    // https://docs.gradle.org/current/userguide/managing_transitive_dependencies.html#sec:bom_import
    // https://github.com/spring-cloud/spring-cloud-release/blob/master/docs/src/main/asciidoc/spring-cloud-starters.adoc#using-spring-cloud-dependencies-with-spring-io-platform
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-bom/pom.xml
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:${Versions.kotlin}"))
    //implementation(platform("io.projectreactor:reactor-bom:${Versions.reactorBom}"))
    //implementation(platform("org.springframework.data:spring-data-releasetrain:${Versions.springDataReleasetrain}"))
    //implementation(platform("org.springframework.security:spring-security-bom:${Versions.springSecurity}"))
    implementation(platform("org.springframework.integration:spring-integration-bom:${Versions.springIntegration}"))
    implementation(platform("org.junit:junit-bom:${Versions.junitJupiterBom}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${Versions.springBoot}"))

    // https://github.com/apache/incubator-zipkin-brave
    implementation(platform("io.zipkin.brave:brave-bom:${Versions.braveBom}"))

    implementation(platform("org.springframework.cloud:spring-cloud-stream-dependencies:${Versions.springCloudStreamBom}"))
    implementation(platform("org.springframework.cloud:spring-cloud-circuitbreaker-dependencies:${Versions.springCloudCircuitbreakerBom}"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${Versions.springCloud}"))

    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    // fuer YML-Konfigurationsdateien
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    // fuer Validierung von Methodenargumenten
    implementation("com.thoughtworks.paranamer:paranamer:${Versions.paranamer}")

    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("io.projectreactor:reactor-tools:${Versions.reactorTools}")
    implementation("io.projectreactor.tools:blockhound:${Versions.blockhound}")
    // CAVEAT: Falls spring-boot-starter-web als Dependency enthalten ist, wird Spring MVC konfiguriert,
    //         damit in MVC-Anwendungen der "reactive" WebClient nutzbar ist
    // spring-boot-starter-webflux enthaelt Reactor Netty als Default "Web Engine"
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-tomcat"){
        exclude(module = "tomcat-embed-el")
        exclude(module = "tomcat-embed-websocket")
    }

    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure basiert auf SpringMVC, d.h. Servlets statt reactive
    implementation("org.springframework.hateoas:spring-hateoas") {
        exclude(module = "spring-plugin-core")
        exclude(module = "json-path")
    }

    implementation("org.springframework.boot:spring-boot-actuator")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.springframework.security:spring-security-rsa:${Versions.springSecurityRsa}") {
        exclude(module = "bcpkix-jdk15on")
    }

    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-consul-discovery") {
        exclude(module = "spring-cloud-netflix-core")
        // FIXME Ohne Netflix Ribbon wird der von Consul gelieferte Service-Name als Rechnername interpretiert
        //exclude(module = "spring-cloud-starter-netflix-ribbon")
    }

    // https://piotrminkowski.wordpress.com/2019/04/05/the-future-of-spring-cloud-microservices-after-netflix-era
    implementation("org.springframework.cloud:spring-cloud-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j") {
        exclude(module = "reactor-test")
    }
    // https://github.com/spring-cloud/spring-cloud-stream-starters/issues/52
    implementation("org.springframework.cloud:spring-cloud-stream") {
        exclude(module = "spring-integration-jmx")
    }
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")
    //implementation("org.springframework.cloud:spring-cloud-starter-stream-rabbit")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Sleuth with Zipkin via HTTP
    // org.springframework.cloud.sleuth.instrument.messaging.TraceMessagingAutoConfiguration
    implementation("org.springframework.cloud:spring-cloud-starter-zipkin") {
        exclude(module = "brave-instrumentation-spring-rabbit")
        exclude(module = "brave-instrumentation-spring-webmvc")
        exclude(module = "brave-instrumentation-jms")
    }

    // Mit graphql-java-kickstart/graphql-spring-boot funktionieren die REST-Tests nicht mehr, weil es zusaetzlich
    // zu WebFluxSecurityConfiguration auch WebMvcSecurityConfiguration gibt :-(
    //implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:5.7.2")
    //runtimeOnly("com.graphql-java-kickstart:graphiql-spring-boot-starter:5.7.2")

    // https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#beans-scanning-index
    // generiert Index-Datei META-INF/spring.components fuer 500+ Klassen statt Scanning des Classpath
    // in IntelliJ: spring-context-indexer muss als "annotation processor" registriert werden
    //compileOnly("org.springframework:spring-context-indexer")

    // https://www.vojtechruzicka.com/spring-boot-devtools
    runtimeOnly("org.springframework.boot:spring-boot-devtools:${Versions.springBoot}")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.junit.jupiter:junit-jupiter:${Versions.junitJupiter}")
    testCompile("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "junit")
        exclude(module = "hamcrest-core")
        exclude(module = "hamcrest-library")
        //exclude(module = "assertj-core")
        exclude(module = "mockito-core")
        exclude(module = "json-path")
        exclude(module = "jsonassert")
        exclude(module = "xmlunit-core")
    }
    testImplementation("org.springframework.security:spring-security-test")

    ktlintCfg("com.github.shyiko:ktlint:${Versions.ktlint}") {

    }
    // https://youtrack.jetbrains.net/issue/KT-27463
    @Suppress("UnstableApiUsage")
    constraints {
        ktlintCfg("org.apache.httpcomponents:httpclient:${Versions.httpClientKtlint}")
    }

    plantumlCfg("net.sourceforge.plantuml:plantuml:${Versions.plantuml}")
    plantumlCfg("org.apache.ant:ant-junit:${Versions.antJunit}")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains" && requested.name == "annotations") {
            useVersion(Versions.annotations)
        }

        if (requested.group == "io.github.resilience4j") {
            useVersion(Versions.resilience4j)
        }
        if (requested.name == "spring-cloud-config-client" || requested.name == "spring-cloud-starter-config") {
            useVersion(Versions.springCloudConfig)
        }
        if (requested.name == "spring-cloud-consul-core" || requested.name == "spring-cloud-consul-discovery" ||
            requested.name == "spring-cloud-starter-consul" ||
            requested.name == "spring-cloud-starter-consul-discovery") {
            useVersion(Versions.springCloudConsul)
        }
        if (requested.name == "spring-cloud-loadbalancer") {
            useVersion(Versions.springCloudLoadBalancer)
        }
        if (requested.name == "spring-cloud-function-context") {
            useVersion(Versions.springCloudFunctionContext)
        }
        if (requested.name == "spring-cloud-netflix-core" ||
            requested.name == "spring-cloud-netflix-archaius" ||
            requested.name == "spring-cloud-netflix-ribbon" ||
            requested.name == "spring-cloud-starter-netflix-archaius" ||
            requested.name == "spring-cloud-starter-netflix-ribbon") {
            useVersion(Versions.springCloudNetflix)
        }
        if (requested.name == "spring-cloud-stream" || requested.name == "spring-cloud-stream-binder-kafka" ||
            requested.name == "spring-cloud-stream-binder-kafka-core" ||
            requested.name == "spring-cloud-starter-stream-kafka") {
            useVersion(Versions.springCloudStream)
        }
        if (requested.name == "spring-cloud-starter-zipkin" || requested.name == "spring-cloud-sleuth-zipkin" ||
            requested.name == "spring-cloud-sleuth-core" || requested.name == "spring-cloud-starter-sleuth") {
            useVersion(Versions.springCloudZipkin)
        }
        if (requested.name == "spring-integration-core") {
            useVersion(Versions.springIntegration)
        }

        //if (requested.group == "org.aspectj") {
        //    useVersion(Versions.aspectj)
        //}
        if (requested.name == "hibernate-validator") {
            useVersion(Versions.hibernateValidator)
        }
        if (requested.group == "com.fasterxml.jackson.core" || requested.group == "com.fasterxml.jackson.datatype" ||
            requested.group == "com.fasterxml.jackson.dataformat" ||
            requested.group == "com.fasterxml.jackson.module") {
            useVersion(Versions.jackson)
        }
        //if (requested.name == "jakarta.mail") {
        //    useVersion(Versions.jakartaMail)
        //}
        //if (requested.name == "jakarta.validation-api") {
        //    useVersion(Versions.jakartaValidationApi)
        //}
        if (requested.group == "org.junit.jupiter") {
            useVersion(Versions.junitJupiter)
        }
        if (requested.group == "org.junit.platform") {
            useVersion(Versions.junitPlatform)
        }
        //if (requested.name == "kafka-clients") {
        //    useVersion(Versions.kafka)
        //}
        //if (requested.name == "mongodb-driver-reactivestreams") {
        //    useVersion(Versions.mongoDriverReactivestreams)
        //}
        //if (requested.name == "mongodb-driver-async" || requested.name == "mongodb-driver" ||
        //    requested.name == "mongodb-driver-core" || requested.name == "bson") {
        //    useVersion(Versions.mongodb)
        //}
        //if (requested.name == "spring-hateoas") {
        //    useVersion(Versions.springHateoas)
        //}
        //if (requested.name == "spring-kafka") {
        //    useVersion(Versions.springKafka)
        //}
        //if (requested.name == "thymeleaf-spring5" || requested.name == "thymeleaf") {
        //    useVersion(Versions.thymeleaf)
        //}
        if (requested.group == "org.apache.tomcat.embed") {
            useVersion(Versions.tomcat)
        }
    }
}


//kotlin {
//    experimental {
//        newInference = ENABLE
//    }
//}

allOpen {
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.stereotype.Service")
    annotation("org.springframework.boot.context.properties.ConfigurationProperties")
}

noArg {
    annotation("org.springframework.boot.context.properties.ConfigurationProperties")
}

// https://kotlinlang.org/docs/reference/kapt.html
//kapt {
//    includeCompileClasspath = false
//    correctErrorTypes = true
//}

sweeney {
    enforce(mapOf("type" to "gradle", "expect" to "[5.4.1,)"))
    // IntelliJ mit OpenJSK 13:   java.lang.IllegalArgumentException: Unsupported class file major version 57
    // SonarQube mit OpenJDK 12:  Sonar Scanner NumberFormatException: For input string: "12-ea"
    enforce(mapOf("type" to "jdk", "expect" to "[1.8.0,13)"))
    validate()
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "12"
            verbose = true
            freeCompilerArgs = listOf("-Xjsr305=strict")
            // ggf. wegen Kotlin-Daemon: %TEMP%\kotlin-daemon.* und %LOCALAPPDATA%\kotlin\daemon
            // https://youtrack.jetbrains.com/issue/KT-18300
            //  $env:LOCALAPPDATA\kotlin\daemon
            //  $env:TEMP\kotlin-daemon.<ZEITSTEMPEL>
            //allWarningsAsErrors = true
        }
        // https://github.com/spring-projects/spring-boot/blob/master/spring-boot-project/spring-boot-docs/src/main/asciidoc/appendix-configuration-metadata.adoc#generating-your-own-metadata-by-using-the-annotation-processor
        dependsOn(processResources)
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            verbose = true
            freeCompilerArgs = listOf("-Xjsr305=strict")
            //allWarningsAsErrors = true
        }
    }

    // https://docs.gradle.org/current/userguide/task_configuration_avoidance.html
    named<BootRun>("bootRun") {
        val args = ArrayList(jvmArgs).apply {
            add("-Dspring.profiles.active=dev")
            add("-Dspring.config.location=classpath:/bootstrap.yml,classpath:/application.yml,classpath:/application-dev.yml")
            add("-Djavax.net.ssl.trustStore=${System.getProperty("user.dir")}/src/main/resources/truststore.p12")
            add("-Djavax.net.ssl.trustStorePassword=zimmermann")
            //add("-noverify")
            // Remote Debugger:   .\gradlew bootRun --debug-jvm
            //add("-verbose:class")
            //add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
        }
        jvmArgs = args
    }

    named<BootJar>("bootJar") {
        doLast {
            println("")
            println("Aufruf der ausfuehrbaren JAR-Datei:")
            println("java -jar build/libs/${archiveFileName.get()} --spring.profiles.active=dev")
            println("")
        }
    }

    test {
        @Suppress("UnstableApiUsage")
        useJUnitPlatform {
            includeEngines("junit-jupiter")

            includeTags("rest", "multimediaRest", "streamingRest", "service")
            //includeTags("rest")
            //includeTags("multimediaRest")
            //includeTags("streamingRest")
            //includeTags("service")

            //excludeTags("service")
        }

        //filter {
        //    includeTestsMatching(includeTests)
        //}

        systemProperty("javax.net.ssl.trustStore", "./src/main/resources/truststore.p12")
        systemProperty("javax.net.ssl.trustStorePassword", "zimmermann")
        systemProperty("junit.platform.output.capture.stdout", true)
        systemProperty("junit.platform.output.capture.stderr", true)
        //systemProperty("java.util.logging.manager", "org.slf4j.bridge.SLF4JBridgeHandler")

        // https://docs.gradle.org/current/userguide/java_testing.html#sec:debugging_java_tests
        // https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html
        // https://docs.gradle.org/current/userguide/java_testing.html#sec:debugging_java_tests
        //debug = true

        // damit nach den Tests immer ein HTML-Report von JaCoCo erstellt wird
        finalizedBy(jacocoTestReport)
    }

    jacoco {
        //toolVersion = Versions.jacocoVersion
    }

    //jacocoTestReport {
    //    // Default: nur HTML-Report im Verzeichnis $buildDir/reports/jacoco
    //    // XML-Report fuer CI, z.B. Jenkins
    //    reports {
    //        xml.isEnabled = true
    //        html.isEnabled = true
    //    }
    //}
    getByName<JacocoReport>("jacocoTestReport") {
        reports {
            @Suppress("UnstableApiUsage")
            xml.isEnabled = true
            @Suppress("UnstableApiUsage")
            html.isEnabled = true
        }
        // afterEvaluate gibt es nur bei getByName<> ("eager"), nicht bei named<> ("lazy")
        // https://docs.gradle.org/5.0/release-notes.html#configuration-avoidance-api-disallows-common-configuration-errors
        afterEvaluate {
            classDirectories.setFrom(files(classDirectories.files.map {
                fileTree(it) { exclude("**/config/**", "**/entity/**") }
            }))
        }
    }

    // Docker-Image durch jib von Google
    // https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#example
    jib {
        from {
            image = "openjdk:alpine"
        }
        to {
            image = "my-docker-id/hska-${project.name}"
        }
    }

    // https://android.github.io/kotlin-guides/style.html
    // https://kotlinlang.org/docs/reference/coding-conventions.html
    // https://www.jetbrains.com/help/idea/code-style-kotlin.html
    // https://github.com/android/kotlin-guides/issues/37
    // https://github.com/shyiko/ktlint
    val ktlint by registering(JavaExec::class) {
        group = "verification"

        classpath = ktlintCfg
        main = "com.github.shyiko.ktlint.Main"
        //https://github.com/shyiko/ktlint/blob/master/ktlint/src/main/kotlin/com/github/shyiko/ktlint/Main.kt
        args = listOf(
            "--verbose",
            "--reporter=plain",
            "--reporter=checkstyle,output=$buildDir/reports/ktlint.xml",
            "src/**/*.kt")
    }
    check { dependsOn(ktlint) }

    detekt {
        buildUponDefaultConfig = true
        failFast = true

        val configDir = "config"
        val reportsDir = "$buildDir/reports"
        config = files(project.rootDir.resolve("$configDir/detekt.yml"))
        reports {
            xml {
                //enabled = true
                destination = file("$reportsDir/detekt.xml")
            }
            html {
                //enabled = true
                destination = file("$reportsDir/detekt.html")
            }

        }
        idea {
            path = "${System.getenv("USERPROFILE")}/.IntelliJIdea${Versions.intellij}"
            inspectionsProfile = "$projectDir/.idea/inspectionProfiles/Project_Default.xml"
        }
    }

    // http://stackoverflow.com/questions/34143530/sonar-maven-analysis-class-not-found#answer-34151150
    sonarqube {
        properties {
            // property("sonar.tests", "src/test/kotlin")
            // property("sonar.exclusions", "src/test/resources/truststore.p12")
            property("sonar.scm.disabled", true)
            // https://docs.sonarqube.org/display/SONAR/Authentication
            property("sonar.login", "admin")
            property("sonar.password", "admin")
        }
    }

    // https://github.com/jeremylong/DependencyCheck/blob/master/src/site/markdown/dependency-check-gradle/configuration.md
    // https://github.com/jeremylong/DependencyCheck/issues/1732
    dependencyCheck {
        suppressionFile = "$projectDir/config/owasp.xml"
        //skipConfigurations = mutableListOf("ktlint", "detekt", "asciidoctor")
        data {
            directory = "C:/Zimmermann/owasp-dependency-check"
            username = "dc"
            password = "p"
        }

        analyzers {
            //  ".NET Assembly Analyzer" wird nicht benutzt
            assemblyEnabled = false
        }

        format = org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
    }

    val plantuml by registering {
        doLast {
            //https://github.com/gradle/kotlin-dsl/blob/master/samples/ant/build.gradle.kts
            ant.withGroovyBuilder {
                "taskdef"(
                    "name" to "plantuml",
                    "classname" to "net.sourceforge.plantuml.ant.PlantUmlTask",
                    "classpath" to plantumlCfg.asPath)

                // PNG-Bilder fuer HTML bei AsciiDoctor und Dokka
                mkdir("$buildDir/docs/images")
                "plantuml"(
                    "output" to "$buildDir/docs/images",
                    "graphvizDot" to "C:\\Zimmermann\\Graphviz\\bin\\dot.exe",
                    "verbose" to true) {
                    "fileset"("dir" to "$projectDir/src/main/kotlin") {
                        "include"("name" to "**/*.puml")
                    }
                }

                // PNG-Bilder kopieren fuer AsciiDoctor mit dem IntelliJ-Plugin
                mkdir("$projectDir/config/images")
                "copy"("todir" to "$projectDir/config/images") {
                    "fileset"("dir" to "$buildDir/docs/images") {
                        "include"("name" to "*.png")
                    }
                }
            }
        }
    }

    named<DokkaTask>("dokka") {
        includes = listOf("Module.md")
        apiVersion = "1.3"
        languageVersion = apiVersion
        noStdlibLink = true
        noJdkLink = true

        dependsOn(plantuml)
    }

    // https://docs.gradle.org/current/dsl/org.gradle.api.tasks.Exec.html
    //val dependencyGraph by registering(Exec::class) {
    //    //workingDir("../tomcat/bin")
    //    commandLine("dot", "$projectDir\\build\\reports\\dependency-graph\\dependency-graph.dot", "-Tpng", "-o", "$projectDir\\build\\dependency-graph.png")
    //}
    //val dependencyGraphPng by registering(Exec::class) {
    //    executable = "dot"
    //    setArgs(listOf("$buildDir\\dependency-graph.dot", "-Tpng", "-o", "$buildDir\\dependency-graph.png"))
    //    dependsOn(dependencyGraph)
    //}

    // https://github.com/asciidoctor/asciidoctor-gradle-plugin/tree/release_2_0_0_alpha_5
    named<AsciidoctorTask>("asciidoctor") {
        asciidoctorj {
            setVersion(Versions.asciidoctorj)
            //requires("asciidoctor-diagram")
        }

        setSourceDir(file("config/docs"))
        //setOutputDir(file("$buildDir/docs/asciidoc"))
        logDocuments = true

        //attributes(mutableMapOf(
        //        "source-higlighter" to "coderay",
        //        "coderay-linenums-mode" to "table",
        //        "toc" to "left",
        //        "icon" to "font",
        //        "linkattrs" to true,
        //        "encoding" to "utf-8"))

        doLast {
            val separator = System.getProperty("file.separator")
            println("Das Entwicklerhandbuch ist in $buildDir${separator}docs${separator}asciidoc${separator}entwicklerhandbuch.html")
        }

        dependsOn(plantuml)
    }

    named<AsciidoctorPdfTask>("asciidoctorPdf") {
        asciidoctorj {
            setVersion(Versions.asciidoctorj)
            modules.pdf.setVersion(Versions.asciidoctorjPdf)
        }

        setSourceDir(file("config/docs"))
        //outputDir file("${buildDir}/docs/asciidocPdf")
        attributes(mutableMapOf("imagesdir" to "$buildDir/docs/images"))
        logDocuments = true

        doLast {
            val separator = System.getProperty("file.separator")
            println("Das Entwicklerhandbuch ist in $buildDir${separator}docs${separator}asciidocPdf${separator}entwicklerhandbuch.pdf")
        }

        dependsOn(plantuml)
    }

    idea {
        module {
            isDownloadJavadoc = true

            // https://info.michael-simons.eu/2018/07/15/spring-boots-configuration-metadata-with-kotlin
            //val kaptMain = file("${project.buildDir}/generated/source/kapt/main")
            //sourceDirs = sourceDirs.toMutableSet().apply { add(kaptMain) }
            //generatedSourceDirs = generatedSourceDirs.toMutableSet().apply { add(kaptMain) }
            //outputDir = file("${project.buildDir}/classes/main")
            //testOutputDir = file("${project.buildDir}/classes/test")
        }
    }
}
