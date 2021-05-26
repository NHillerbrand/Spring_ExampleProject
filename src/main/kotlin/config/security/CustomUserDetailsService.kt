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
package de.hska.kunde.config.security

import de.hska.kunde.config.Settings.DEV
import de.hska.kunde.config.logger
import de.hska.kunde.config.security.Daten.users
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.dropCollection
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID.randomUUID

/**
 * Service-Klasse, um Benutzerkennungen zu suchen und neu anzulegen.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
@Service
class CustomUserDetailsService(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val ctx: ApplicationContext
) : ReactiveUserDetailsService, InitializingBean {
    /**
     * Im Profil _dev_ werden vorhandene Benutzerkennungen gelöscht und neu initialisiert.
     * Dazu wird vom Interface InitializingBean abgeleitet.
     *
     * Alternativen sind:
     * * Die Annotation `@PostConstruct` aus dem Artifakt `javax.annotation:javax.annotation-api`.
     * * SmartInitializingSingleton.
     */
    override fun afterPropertiesSet() {
        // Spring Security verwaltet einen Cache von UserDetails
        if (ctx.environment.activeProfiles.contains(DEV)) {
            mongoTemplate.dropCollection<CustomUser>()
                .thenMany(users)
                .flatMap(mongoTemplate::insert)
                .subscribe { logger.warn("{}", it) }
        }
    }

    /**
     * Zu einem gegebenen Username wird der zugehörige User gesucht.
     * @param username Username des gesuchten Users
     * @return Der gesuchte User
     */

    override fun findByUsername(username: String?): Mono<UserDetails> {
        val query = Query(where("username").isEqualTo(username?.toLowerCase()))
        return mongoTemplate.findOne<CustomUser>(query)
            .map { it as UserDetails }
    }

    /**
     * Einen neuen User anlegen
     * * @param user Der neue User
     * @return Der neu angelegte User einschließlich ID.
     * @throws UsernameExistsException Falls der Username bereits existiert.
     */
    @Suppress("LongMethod")
    fun create(user: CustomUser): Mono<CustomUser> {
        val query = Query(where("username").isEqualTo(user.username))
        return mongoTemplate.findOne<CustomUser>(query)
            .hasElement()
            .flatMap {
                if (it) {
                    throw UsernameExistsException(user.username)
                }

                // Die Account-Informationen des Kunden transformieren
                //  in Account-Informationen fuer die Security-Komp.
                val password = passwordEncoder.encode(user.password)
                val authorities = user.authorities!!
                    .map { grantedAuthority -> SimpleGrantedAuthority(grantedAuthority.authority) }
                val neuerUser = CustomUser(
                    id = randomUUID().toString(),
                    username = user.username.toLowerCase(),
                    password = password,
                    authorities = authorities
                )
                logger.trace("neuerUser = {}", neuerUser)
                mongoTemplate.save(neuerUser)
            }
    }

    private companion object {
        val logger = logger()
    }
}

/**
 * Exception, falls es eine gleichnamige BEnutzerkennung bereits gibt.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Eine Exception mit dem fehlerhaften Benutzernamen erstellen.
 */
open class UsernameExistsException(username: String) :
    RuntimeException("Der Username $username existiert bereits")
