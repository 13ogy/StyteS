package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message gossip propageant la <strong>modification de classe de service</strong>
 * (FREE/STANDARD/PREMIUM) d'un client à tous les courtiers voisins.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.5)</strong></p>
 * <p>
 * Équivaut, côté broker récepteur, à un appel local
 * {@code modifyServiceClass(clientReceptionPortURI, newRegistrationClass)} :
 * chaque voisin met à jour sa table d'enregistrement locale, ce qui modifie
 * notamment les quotas de canaux privilégiés du client.
 * </p>
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong></p>
 * <ul>
 * <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via
 * {@link #getEmitterURI()} (cf. {@code docs/GOSSIP.md} §4).</li>
 * <li>{@link #gossipMessageURI()} unique et immuable, clef de dédup
 * (cf. {@code docs/GOSSIP.md} §3).</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ModifyServiceClassGossipMessage extends AbstractGossipMessage {

 private static final long serialVersionUID = 1L;

 /** URI unique et immuable du message gossip (clef de dédup). */
 private final String gossipMessageURI;
 /** Horodatage de création. */
 private final Instant timestamp;
 /** URI de réflexion du broker qui vient d'émettre cette copie (skip-echo). */
 private final String emitterURI;

 /** URI de réception du client dont la classe de service change. */
 private final String clientReceptionPortURI;
 /** Nouvelle classe de service à appliquer côté voisin. */
 private final RegistrationClass newRegistrationClass;

 /**
 * Construit un message gossip {@code ModifyServiceClass}.
 *
 * @param gossipMessageURI URI unique du message.
 * @param timestamp horodatage de création.
 * @param emitterURI URI de réflexion du broker émetteur courant.
 * @param clientReceptionPortURI URI de réception du client visé.
 * @param newRegistrationClass nouvelle classe de service (FREE/STANDARD/PREMIUM).
 */
 public ModifyServiceClassGossipMessage(
 String gossipMessageURI,
 Instant timestamp,
 String emitterURI,
 String clientReceptionPortURI,
 RegistrationClass newRegistrationClass)
 {
 this.gossipMessageURI = gossipMessageURI;
 this.timestamp = timestamp;
 this.emitterURI = emitterURI;
 this.clientReceptionPortURI = clientReceptionPortURI;
 this.newRegistrationClass = newRegistrationClass;
 }

 /** {@inheritDoc} */
 @Override
 public String gossipMessageURI() { return this.gossipMessageURI; }

 /** {@inheritDoc} */
 @Override
 public Instant timestamp() { return this.timestamp; }

 /**
 * @param newGossipEmitterURI URI de réflexion du nouvel émetteur courant.
 * @return copie immuable avec {@code emitterURI} mis à jour ; URI gossip conservé.
 */
 @Override
 public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
 return new ModifyServiceClassGossipMessage(
 this.gossipMessageURI,
 this.timestamp,
 newGossipEmitterURI,
 this.clientReceptionPortURI,
 this.newRegistrationClass);
 }

 /** @return URI de réception du client visé. */
 public String getClientReceptionPortURI() { return this.clientReceptionPortURI; }
 /** @return nouvelle classe de service à appliquer. */
 public RegistrationClass getNewRegistrationClass() { return this.newRegistrationClass; }
 public String getEmitterURI() { return this.emitterURI; }



 // Visitor pattern
 @Override
 public void accept(GossipMessageVisitor visitor) {
 visitor.visit(this);
 }
}