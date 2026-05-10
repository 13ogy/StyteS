package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

import java.time.Instant;

/**
 * Message gossip propageant la <strong>désinscription d'un client</strong> à tous les courtiers
 * voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.5)</strong>
 *
 * <p>Équivaut, côté broker récepteur, à un appel local {@code unregister(clientReceptionPortURI)} :
 * chaque voisin oublie l'existence du client dans sa table d'enregistrement, supprime ses
 * souscriptions et libère les quotas de canaux privilégiés associés.
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong>
 *
 * <ul>
 *   <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via {@link #getEmitterURI()} (cf.
 *       {@code docs/GOSSIP.md} §4).
 *   <li>{@link #gossipMessageURI()} unique et immuable, clef de la dédup atomique (cf. {@code
 *       docs/GOSSIP.md} §3).
 *   <li>Opération idempotente : un voisin déjà sans le client traite gracieusement le message sans
 *       erreur métier.
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class UnregisterGossipMessage extends AbstractGossipMessage {

	private static final long serialVersionUID = 1L;

	// -------------------------------------------------------------------------
	// Champs requis par GossipMessageI
	// -------------------------------------------------------------------------

	/** URI unique de ce message gossip — pour éviter les boucles. */
	private final String gossipMessageURI;

	/** Instant de création — pour le nettoyage de la mémoire. */
	private final Instant timestamp;

	/** URI du courtier émetteur courant. */
	private final String emitterURI;

	// -------------------------------------------------------------------------
	// Payload — informations à propager
	// -------------------------------------------------------------------------

	/** URI du port de réception du client (son identité dans le système). */
	private final String clientReceptionPortURI;

	// -------------------------------------------------------------------------
	// Constructeur
	// -------------------------------------------------------------------------

	/**
	 * Construit un message gossip {@code Unregister}.
	 *
	 * @param gossipMessageURI URI unique du message (immuable).
	 * @param timestamp horodatage de création.
	 * @param emitterURI URI de réflexion du broker émetteur courant.
	 * @param clientReceptionPortURI URI de réception du client à désinscrire.
	 */
	public UnregisterGossipMessage(
			String gossipMessageURI,
			Instant timestamp,
			String emitterURI,
			String clientReceptionPortURI) {
		this.gossipMessageURI = gossipMessageURI;
		this.timestamp = timestamp;
		this.emitterURI = emitterURI;
		this.clientReceptionPortURI = clientReceptionPortURI;
	}

	// -------------------------------------------------------------------------
	// GossipMessageI
	// -------------------------------------------------------------------------

	/** {@inheritDoc} */
	@Override
	public String gossipMessageURI() {
		return this.gossipMessageURI;
	}

	/** {@inheritDoc} */
	@Override
	public Instant timestamp() {
		return this.timestamp;
	}

	/**
	 * @param newGossipEmitterURI URI de réflexion du nouvel émetteur courant.
	 * @return copie immuable avec {@code emitterURI} mis à jour ; URI gossip conservé.
	 */
	@Override
	public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
		return new UnregisterGossipMessage(
				this.gossipMessageURI, // on garde l'uri du messages
				this.timestamp,
				newGossipEmitterURI, // on change l'émeteur pour le nouveau
				this.clientReceptionPortURI);
	}

	// -------------------------------------------------------------------------
	// Getters pour le courtier receveur
	// -------------------------------------------------------------------------

	/**
	 * @return URI de réception du client à désinscrire.
	 */
	public String getClientReceptionPortURI() {
		return this.clientReceptionPortURI;
	}

	public String getEmitterURI() {
		return this.emitterURI;
	}

	// Visitor pattern
	@Override
	public void accept(GossipMessageVisitor visitor) {
		visitor.visit(this);
	}
}
