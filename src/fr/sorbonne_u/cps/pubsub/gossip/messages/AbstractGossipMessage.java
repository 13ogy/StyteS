package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.BrokerGossipHandler;
import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

/**
 * Abstract base class for all gossip messages exchanged between {@link Broker} instances.
 * <p>
 * This class acts as an intermediary between the {@link GossipMessageI} interface,
 * which cannot be modified, and the visitor pattern used by the broker to dispatch
 * message processing without instanceof chains.
 * </p>
 * <p>
 * All concrete gossip message classes must extend this class and implement
 * {@link #accept} by calling {@code visitor.visit(this)}, enabling double-dispatch
 * to the correct {@link GossipMessageVisitor} method.
 * </p>
 * <p>
 * This class lives in the {@code components} package so that it can reference
 * {@link GossipMessageVisitor}, while the concrete subclasses may reside in a
 * different package.
 * </p>
 *
 * @see GossipMessageVisitor
 * @see BrokerGossipHandler
 *
 * @author Setbel Mélissa,
 */
public abstract class AbstractGossipMessage implements GossipMessageI {

    /**
     * Accepts a {@link GossipMessageVisitor} and dispatches processing to the
     * appropriate {@code visit} method based on the concrete type of this message.
     * <p>
     * Concrete subclasses must implement this as:
     * <pre>{@code
     * public void accept(GossipMessageVisitor visitor) {
     *     visitor.visit(this);
     * }
     * }</pre>
     * </p>
     *
     * @param visitor the visitor that will handle this message
     */
    abstract public void accept(GossipMessageVisitor visitor);

}
