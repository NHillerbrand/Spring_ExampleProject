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
package de.hska.kunde.service

/**
 * Exception, falls es bereits einen Kunden mit der jeweiligen Emailadresse gibt.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Exception mit der bereits verwendeten Emailadresse.
 */
class EmailExistsException(email: String) : RuntimeException("Die Emailadresse $email existiert bereits")

/**
 * Exception, falls die Versionsnummer bei z.B. PUT oder PATCH ungültig ist.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 */
class InvalidVersionException
/**
 * Exception mit der ungültigen Versionsnummer erstellen
 * @param version Die ungültige Versionsnummer
 */(version: String) : RuntimeException("Falsche Versionsnummer $version")

/**
 * Exception, falls die Benutzerdaten zu einem (neuen) Kunden ungültig sind.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Exception mit der Information, dass die Benutzerdaten ungültig sind.
 */
class InvalidAccountException : RuntimeException("Ungueltiger Account")

/**
 * Exception, falls die Zugriffsrechte nicht ausreichen.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Exception mit der Information, dass die Rolle(n).
 */
class AccessForbiddenException(roles: Collection<String>) : RuntimeException("Unzureichende Rollen: $roles")
