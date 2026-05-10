package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.BrokerGossipHandler;
import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

/**
 * Classe de base abstraite pour tous les messages gossip échangés entre
 * instances de {@link Broker}.
 *
 * <p>
 * Cette classe joue deux rôles :
 * </p>
 * <ul>
 *   <li>elle introduit le double-dispatch via {@link #accept(GossipMessageVisitor)}
 *   pour le {@link GossipMessageVisitor} (pattern Visitor — évite les chaînes
 *   {@code instanceof} dans {@code Broker.update(...)}, conformément au
 *   conseil donné en soutenance) ;</li>
 *   <li>elle propage le contrat {@link EmitterAwareGossipMessageI} à toutes
 *   les sous-classes concrètes, garantissant que chaque message gossip
 *   expose son {@code emitterURI} (clef du skip-echo, cf. {@code docs/GOSSIP.md} §4).</li>
 * </ul>
 *
 * <p>
 * Les sous-classes concrètes doivent implémenter {@link #accept} par :
 * </p>
 * <pre>{@code
 * public void accept(GossipMessageVisitor visitor) {
 *     visitor.visit(this);
 * }
 * }</pre>
 *
 * @see GossipMessageVisitor
 * @see BrokerGossipHandler
 * @see EmitterAwareGossipMessageI
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public abstract class AbstractGossipMessage implements EmitterAwareGossipMessageI {

    /**
     * Accepte un {@link GossipMessageVisitor} et dispatche le traitement vers
     * la méthode {@code visit} appropriée selon le type concret de ce message.
     *
     * @param visitor le visiteur qui traitera ce message.
     */
    abstract public void accept(GossipMessageVisitor visitor);

}
