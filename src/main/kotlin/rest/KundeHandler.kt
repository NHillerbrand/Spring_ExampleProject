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
package de.hska.kunde.rest

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import de.hska.kunde.Router.Companion.idPathVar
import de.hska.kunde.config.logger
import de.hska.kunde.config.security.UsernameExistsException
import de.hska.kunde.entity.Kunde
import de.hska.kunde.rest.constraints.KundeConstraintViolation
import de.hska.kunde.rest.extensions.ifMatch
import de.hska.kunde.rest.extensions.ifNoneMatch
import de.hska.kunde.rest.hateoas.KundeModelAssembler
import de.hska.kunde.rest.patch.KundePatcher
import de.hska.kunde.rest.patch.PatchOperation
import de.hska.kunde.service.AccessForbiddenException
import de.hska.kunde.service.EmailExistsException
import de.hska.kunde.service.InvalidAccountException
import de.hska.kunde.service.InvalidVersionException
import de.hska.kunde.service.KundeService
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_MODIFIED
import org.springframework.http.HttpStatus.PRECONDITION_FAILED
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.created
import org.springframework.web.reactive.function.server.ServerResponse.noContent
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.ServerResponse.status
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToFlux
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.onErrorResume
import reactor.kotlin.core.publisher.toMono
import java.net.URI
import java.security.Principal
import javax.validation.ConstraintViolationException

/**
 * Eine Handler-Function wird von der Router-Function [de.hska.kunde.Router.router]
 * aufgerufen, nimmt einen Request entgegen und erstellt den Response.
 *
 * [Klassendiagramm](../../../../docs/images/KundeHandler.png)
 *
 * @author [J??rgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Einen KundeHandler mit einem injizierten [KundeService]
 *      erzeugen.
 */
@Component
@Suppress("TooManyFunctions", "LargeClass")
class KundeHandler(private val service: KundeService, private val modelAssembler: KundeModelAssembler) {
    /**
     * Suche anhand der Kunde-ID
     * @param request Der eingehende Request
     * @return Ein Mono-Objekt mit dem Statuscode 200 und dem gefundenen
     *      Kunden einschlie??lich HATEOAS-Links, oder aber Statuscode 204.
     */
    fun findById(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariable(idPathVar)

        return getUsername(request)
            .flatMap { username -> service.findById(id, username) }
            .flatMap { kunde -> toResponse(kunde, request) }
            .switchIfEmpty(notFound().build())
            .onErrorResume(AccessForbiddenException::class) {
                status(FORBIDDEN).build()
            }
    }

    private fun getUsername(request: ServerRequest) =
        request.principal()
            .map(Principal::getName)
            .doOnNext { logger.debug("username = {}", it) }

    private fun toResponse(kunde: Kunde, request: ServerRequest): Mono<ServerResponse> {
        val version = kunde.version
        return if (version == request.ifNoneMatch()?.toIntOrNull()) {
            status(NOT_MODIFIED).build()
        } else {
            val kundeModel = modelAssembler.toModel(kunde, request)

            // Entity Tag, um Aenderungen an der angeforderten
            // Ressource erkennen zu koennen.
            // Client: GET-Requests mit Header "If-None-Match"
            //         ggf. Response mit Statuscode NOT MODIFIED (s.o.)
            ok().eTag("\"$version\"").body(kundeModel.toMono())
        }
    }

    /**
     * Suche mit diversen Suchkriterien als Query-Parameter. Es wird
     * `Mono<List<Kunde>>` statt `Flux<Kunde>` zur??ckgeliefert, damit
     * auch der Statuscode 204 m??glich ist.
     * @param request Der eingehende Request mit den Query-Parametern.
     * @return Ein Mono-Objekt mit dem Statuscode 200 und einer Liste mit den
     *      gefundenen Kunden einschlie??lich HATEOAS-Links, oder aber
     *      Statuscode 204.
     */
    fun find(request: ServerRequest): Mono<ServerResponse> {
        val queryParams = request.queryParams()

        // https://stackoverflow.com/questions/45903813/...
        //     ...webflux-functional-how-to-detect-an-empty-flux-and-return-404
        return service.find(queryParams)
            .map { kunde -> modelAssembler.toModel(kunde, request, false) }
            .collectList()
            .flatMap {
                if (it.isEmpty())
                    notFound().build()
                else
                    ok().body(it.toMono())
            }
    }

    /**
     * Einen neuen Kunde-Datensatz anlegen.
     * @param request Der eingehende Request mit dem Kunde-Datensatz im Body.
     * @return Response mit Statuscode 201 einschlie??lich Location-Header oder
     *      Statuscode 400 falls Constraints verletzt sind oder der
     *      JSON-Datensatz syntaktisch nicht korrekt ist.
     */
    @Suppress("LongMethod")
    fun create(request: ServerRequest) = request.bodyToMono<Kunde>()
        .flatMap(service::create)
        .flatMap {
            logger.trace("Kunde abgespeichert: {}", it)
            val location = URI("${request.uri()}${it.id}")
            created(location).build()
        }
        .onErrorResume(ConstraintViolationException::class) {
            // Service-Funktion "create" und Parameter "kunde"
            handleConstraintViolation(it, "create.kunde.")
        }
        .onErrorResume(InvalidAccountException::class) {
            val msg = it.message ?: ""
            badRequest().body(msg.toMono())
        }
        .onErrorResume(EmailExistsException::class) {
            val msg = it.message ?: ""
            badRequest().body(msg.toMono())
        }
        .onErrorResume(UsernameExistsException::class) {
            val msg = it.message ?: ""
            badRequest().body(msg.toMono())
        }
        .onErrorResume(DecodingException::class) {
            val msg = it.message ?: ""
            badRequest().body(msg.toMono())
        }

    // z.B. Service-Funktion "create|update" mit Parameter "kunde" hat dann Meldungen mit "create.kunde.nachname:"
    private fun handleConstraintViolation(exception: ConstraintViolationException, deleteStr: String):
        Mono<ServerResponse> {
        val violations = exception.constraintViolations
        if (violations.isEmpty()) {
            return badRequest().build()
        }

        val kundeViolations = violations.map { violation ->
            KundeConstraintViolation(
                property = violation.propertyPath.toString().replace(deleteStr, ""),
                message = violation.message
            )
        }
        logger.trace("violations: {}", kundeViolations)
        return badRequest().body(kundeViolations.toMono())
    }

    private fun handleDecodingException(e: DecodingException): Mono<ServerResponse> {
        logger.debug(e.message)
        return when (val exception = e.cause) {
            is JsonParseException -> {
                logger.debug(exception.message)
                badRequest().syncBody(exception.message ?: "")
            }
            is InvalidFormatException -> {
                logger.debug("${exception.message}")
                badRequest().syncBody(exception.message ?: "")
            }
            else -> status(INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Einen vorhandenen Kunde-Datensatz ??berschreiben.
     * @param request Der eingehende Request mit dem neuen Kunde-Datensatz im
     *      Body.
     * @return Response mit Statuscode 204 oder Statuscode 400, falls
     *      Constraints verletzt sind oder der JSON-Datensatz syntaktisch nicht
     *      korrekt ist.
     */
    @Suppress("LongMethod")
    fun update(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariable(idPathVar)
        val version = request.ifMatch()
            ?: return status(PRECONDITION_FAILED).body("Versionsnummer fehlt".toMono())

        return request.bodyToMono<Kunde>()
            .flatMap { service.update(it, id, version) }
            .flatMap {
                logger.trace("Kunde aktualisiert: {}", it)
                noContent().eTag("\"${it.version}\"").build()
            }
            .switchIfEmpty(notFound().build())
            .onErrorResume(ConstraintViolationException::class) {
                // Service-Funktion "update" und Parameter "kunde"
                handleConstraintViolation(it, "update.kunde.")
            }
            .onErrorResume(EmailExistsException::class) {
                logger.trace("EmailExistsException: {}", it.message)
                badRequest().syncBody(it.message ?: "")
            }
            .onErrorResume(InvalidVersionException::class) {
                logger.trace("InvalidVersionException: {}", it.message)
                status(PRECONDITION_FAILED).syncBody(it.message ?: "")
            }
            .onErrorResume(DecodingException::class) {
                logger.trace("DecodingException")
                handleDecodingException(it)
            }
    }

    /**
     * Einen vorhandenen Kunde-Datensatz durch PATCH aktualisieren.
     * @param request Der eingehende Request mit dem PATCH-Datensatz im Body.
     * @return Response mit Statuscode 204 oder Statuscode 400, falls
     *      Constraints verletzt sind oder der JSON-Datensatz syntaktisch nicht
     *      korrekt ist.
     */
    @Suppress("LongMethod")
    fun patch(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariable(idPathVar)
        val version = request.ifMatch()
            ?: return status(PRECONDITION_FAILED).body("Versionsnummer fehlt".toMono())
        logger.trace("Versionsnummer $version")

        return request.bodyToFlux<PatchOperation>()
            // Die einzelnen Patch-Operationen als Liste in einem Mono
            .collectList()
            .flatMap { patchOps ->
                getUsername(request)
                    .flatMap { username ->
                        service.findById(id, username)
                    }
                    .flatMap {
                        val patchedKunde = KundePatcher.patch(it, patchOps)
                        logger.trace("Kunde mit Patch-Ops: {}", patchedKunde)
                        service.update(patchedKunde, id, version)
                    }
                    .flatMap {
                        noContent().eTag("\"${it.version}\"").build()
                    }
                    .switchIfEmpty(notFound().build())
                    .onErrorResume(ConstraintViolationException::class) {
                        // Service-Funktion "update" und Parameter "kunde"
                        handleConstraintViolation(it, "update.kunde.")
                    }
                    .onErrorResume(EmailExistsException::class) {
                        badRequest().syncBody(it.message ?: "")
                    }
                    .onErrorResume(InvalidVersionException::class) {
                        val msg = it.message ?: ""
                        status(PRECONDITION_FAILED).body(msg.toMono())
                    }
                    .onErrorResume(DecodingException::class) {
                        handleDecodingException(it)
                    }
            }
    }

    /**
     * Einen vorhandenen Kunden anhand seiner ID l??schen.
     * @param request Der eingehende Request mit der ID als Pfad-Parameter.
     * @return Response mit Statuscode 204.
     */
    fun deleteById(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariable(idPathVar)
        return service.deleteById(id)
            .flatMap { noContent().build() }
            .switchIfEmpty(notFound().build())
    }

    /**
     * Einen vorhandenen Kunden anhand seiner Emailadresse l??schen.
     * @param request Der eingehende Request mit der Emailadresse als
     *      Query-Parameter.
     * @return Response mit Statuscode 204.
     */
    fun deleteByEmail(request: ServerRequest): Mono<ServerResponse> {
        val email = request.queryParam("email")
        return if (email.isPresent) {
            service.deleteByEmail(email.get())
                .flatMap { noContent().build() }
        } else {
            noContent().build()
        }
    }

    private companion object {
        val logger = logger()
    }
}
