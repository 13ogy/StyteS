package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message gossip propageant la <strong>création d'un canal privilégié</strong> à tous les courtiers
 * voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.6)</strong>
 *
 * <p>Équivaut, côté broker récepteur, à un appel local {@code createChannel(channel, owner,
 * ownerClass, authorisedUsers)} : le voisin matérialise localement le canal privilégié pour pouvoir
 * y accepter publications et souscriptions. Voir le branchement de ce type dans {@code
 * Broker#update(...)}.
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong>
 *
 * <ul>
 *   <li>Implémente {@link EmitterAwareGossipMessageI} : le broker récepteur utilise {@link
 *       #getEmitterURI()} pour ne <em>pas</em> renvoyer le message au voisin qui vient de nous le
 *       transmettre (skip-echo, cf. {@code docs/GOSSIP.md} §4).
 *   <li>{@link #gossipMessageURI()} est un identifiant unique et immuable généré par l'émetteur
 *       originel ; il sert de clef à la dédup atomique {@code processedGossipURIs.putIfAbsent(...)}
 *       (cf. {@code docs/GOSSIP.md} §3).
 *   <li>{@link #copyWithNewEmitterURI(String)} ne modifie que l'émetteur, l'URI gossip reste
 *       identique : la dédup couvre l'intégralité du chemin.
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class CreateChannelGossipMessage extends AbstractGossipMessage {

	private static final long serialVersionUID = 1L;

	/** URI unique et immuable du message gossip (clef de dédup). */
	private final String gossipMessageURI;

	/** Horodatage de création (utilisé par {@code cleanupGossipMemory}). */
	private final Instant timestamp;

	/** URI de réflexion du broker qui vient d'émettre cette copie (skip-echo). */
	private final String emitterURI;

	// Payload — informations du canal à créer
	/** Nom du canal privilégié à créer localement. */
	private final String channel;

	/** URI du port de réception du client propriétaire du canal. */
	private final String ownerReceptionPortURI;

	/**
	 * Regex (au sens {@link java.util.regex.Pattern}) listant les URIs de réception autorisés à
	 * publier/souscrire ; peut être {@code null}.
	 */
	private final String authorisedUsers; // regex, peut être null

	/** Classe de service du propriétaire du canal (STANDARD/PREMIUM). */
	private final RegistrationClass ownerClass;

	/**
	 * Construit un message gossip {@code CreateChannel}.
	 *
	 * @param gossipMessageURI URI unique du message (immuable).
	 * @param timestamp horodatage de création.
	 * @param emitterURI URI de réflexion du broker émetteur courant.
	 * @param channel nom du canal privilégié.
	 * @param ownerReceptionPortURI URI de réception du propriétaire du canal.
	 * @param authorisedUsers regex des utilisateurs autorisés (peut être {@code null}).
	 * @param ownerClass classe de service du propriétaire.
	 */
	public CreateChannelGossipMessage(
			String gossipMessageURI,
			Instant timestamp,
			String emitterURI,
			String channel,
			String ownerReceptionPortURI,
			String authorisedUsers,
			RegistrationClass ownerClass) {
		this.gossipMessageURI = gossipMessageURI;
		this.timestamp = timestamp;
		this.emitterURI = emitterURI;
		this.channel = channel;
		this.ownerReceptionPortURI = ownerReceptionPortURI;
		this.authorisedUsers = authorisedUsers;
		this.ownerClass = ownerClass;
	}

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
	 * Retourne une copie identique au présent message, avec uniquement l'émetteur ré-écrit. L'URI
	 * gossip est conservé, ce qui permet à la dédup ({@code processedGossipURIs.putIfAbsent})
	 * d'attraper l'écho sur n'importe quel chemin du graphe.
	 *
	 * @param newGossipEmitterURI URI de réflexion du nouvel émetteur courant.
	 * @return copie immuable avec {@code emitterURI} mis à jour.
	 */
	@Override
	public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
		return new CreateChannelGossipMessage(
				this.gossipMessageURI,
				this.timestamp,
				newGossipEmitterURI, // seul ce champ change
				this.channel,
				this.ownerReceptionPortURI,
				this.authorisedUsers,
				this.ownerClass);
	}

	/**
	 * @return nom du canal privilégié à créer.
	 */
	public String getChannel() {
		return this.channel;
	}

	/**
	 * @return URI de réception du propriétaire du canal.
	 */
	public String getOwnerReceptionPortURI() {
		return this.ownerReceptionPortURI;
	}

	/**
	 * @return regex des utilisateurs autorisés ({@code null} = pas de restriction).
	 */
	public String getAuthorisedUsers() {
		return this.authorisedUsers;
	}

	public String getEmitterURI() {
		return this.emitterURI;
	}

	/**
	 * @return classe de service du propriétaire du canal.
	 */
	public RegistrationClass getOwnerClass() {
		return this.ownerClass;
	}

	// Visitor pattern
	@Override
	public void accept(GossipMessageVisitor visitor) {
		visitor.visit(this);
	}
}
