package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

import java.time.Instant;

/**
 * Message gossip propageant la <strong>destruction d'un canal privilégié</strong>
 * à tous les courtiers voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.6)</strong></p>
 * <p>
 * Équivaut, côté broker récepteur, à un appel local
 * {@code destroyChannel(channel, ownerReceptionPortURI)} : chaque voisin
 * supprime sa réplique locale du canal et libère le quota associé au
 * propriétaire (cf. {@code Broker#update(...)} sur ce type).
 * </p>
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong></p>
 * <ul>
 * <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via
 * {@link #getEmitterURI()} (cf. {@code docs/GOSSIP.md} §4).</li>
 * <li>{@link #gossipMessageURI()} unique et immuable, clef de la dédup
 * atomique (cf. {@code docs/GOSSIP.md} §3).</li>
 * <li>{@link #copyWithNewEmitterURI(String)} ne change que l'émetteur.</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DestroyChannelGossipMessage extends AbstractGossipMessage {

 private static final long serialVersionUID = 1L;

 /** URI unique et immuable du message gossip (clef de dédup). */
 private final String gossipMessageURI;
 /** Horodatage de création. */
 private final Instant timestamp;
 /** URI de réflexion du broker qui vient d'émettre cette copie (skip-echo). */
 private final String emitterURI;

 /** Nom du canal privilégié à détruire. */
 private final String channel;
 /** URI de réception du propriétaire du canal (sert au calcul de quota). */
 private final String ownerReceptionPortURI;

 /**
 * Construit un message gossip {@code DestroyChannel}.
 *
 * @param gossipMessageURI URI unique du message.
 * @param timestamp horodatage de création.
 * @param emitterURI URI de réflexion du broker émetteur courant.
 * @param channel nom du canal privilégié à détruire.
 * @param ownerReceptionPortURI URI de réception du propriétaire du canal.
 */
 public DestroyChannelGossipMessage(
 String gossipMessageURI,
 Instant timestamp,
 String emitterURI,
 String channel,
 String ownerReceptionPortURI)
 {
 this.gossipMessageURI = gossipMessageURI;
 this.timestamp = timestamp;
 this.emitterURI = emitterURI;
 this.channel = channel;
 this.ownerReceptionPortURI = ownerReceptionPortURI;
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
 return new DestroyChannelGossipMessage(
 this.gossipMessageURI,
 this.timestamp,
 newGossipEmitterURI,
 this.channel,
 this.ownerReceptionPortURI);
 }

 /** @return nom du canal privilégié à détruire. */
 public String getChannel() { return this.channel; }
 /** @return URI de réception du propriétaire du canal. */
 public String getOwnerReceptionPortURI() { return this.ownerReceptionPortURI; }
 public String getEmitterURI() { return this.emitterURI; }

 // Visitor pattern
 @Override
 public void accept(GossipMessageVisitor visitor) {
 visitor.visit(this);
 }

}