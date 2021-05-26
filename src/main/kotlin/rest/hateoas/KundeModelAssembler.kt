package de.hska.kunde.rest.hateoas

import de.hska.kunde.entity.Kunde
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.server.RepresentationModelAssembler
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Mit der Klasse [KundeModelAssembler] können Entity-Objekte der Klasse [de.hska.kunde.entity.Kunde].
 * in eine HATEOAS-Repräsentation transformiert werden.
 *
 * @author [Jürgen Zimmermann](mailto:Juergen.Zimmermann@HS-Karlsruhe.de)
 *
 * @constructor Ein KundeModelAssembler erzeugen.
 */
@Component
class KundeModelAssembler : RepresentationModelAssembler<Kunde, EntityModel<Kunde>> {
    /**
     * Konvertierung eines (gefundenen) Kunde-Objektes in ein Model gemäß
     * Spring HATEOAS .
     * @param kunde Gefundenes Kunde-Objekt oder null
     * @param request Der eingegangene Request mit insbesondere der aufgerufenen URI
     * @return Model für den Kunden mit Atom-Links für HATEOAS
     */
    fun toModel(kunde: Kunde, request: ServerRequest, alleLinks: Boolean = true): EntityModel<Kunde> {
        val id = kunde.id
        val uri = request.uri().toString()

        val baseUri = uri.removeSuffix("/").removeSuffix("/$id")
        val idUri = "$baseUri/$id"

        // Atom-Links
        val selfLink = Link(idUri)
        val kundeModel = toModel(kunde).add(selfLink)

        if (alleLinks) {
            val listLink = Link(baseUri, "list")
            val addLink = Link(baseUri, "add")
            val updateLink = Link(idUri, "update")
            val removeLink = Link(idUri, "remove")
            kundeModel.add(listLink, addLink, updateLink, removeLink)
        }

        return kundeModel
    }

    /**
     * Konvertierung eines (gefundenen) Kunde-Objektes in ein Model gemäß Spring HATEOAS, so dass anschließend
     * Atom-Links hinzugefügt werden können.
     * @param kunde Gefundenes Kunde-Objekt oder null
     */
    override fun toModel(kunde: Kunde) =
        // Model-Objekt zzgl. Self-Link mit der id erzeugen:
        // createModelWithId(kunde.id, kunde)
        EntityModel(kunde)
}
