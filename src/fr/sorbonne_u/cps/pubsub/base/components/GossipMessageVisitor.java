package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.cps.pubsub.gossip.messages.*;

/**
 * Visitor interface for processing gossip messages received by a {@link Broker}.
 * <p>
 * Each method handles a specific type of gossip message, allowing the broker
 * to dispatch processing without relying on instanceof checks. Implementations
 * are responsible for updating the broker's internal state accordingly.
 * </p>
 * <p>
 * This interface is intended to be used alongside {@link AbstractGossipMessage#accept},
 * which performs the double-dispatch to the appropriate visit method.
 * </p>
 * @author Setbel Mélissa, Bogdan Styn
 */
public interface GossipMessageVisitor {
    /** Handles a client registration propagated via gossip. */
    void visit(RegisterGossipMessage msg);

    /** Handles a message publication propagated via gossip. */
    void visit(PublishGossipMessage msg);

    /** Handles a channel creation propagated via gossip. */
    void visit(CreateChannelGossipMessage msg);

    /** Handles a channel destruction propagated via gossip. */
    void visit(DestroyChannelGossipMessage msg);

    /** Handles a service class modification propagated via gossip. */
    void visit(ModifyServiceClassGossipMessage msg);

    /** Handles an authorised users modification propagated via gossip. */
    void visit(ModifyAuthorisedUsersGossipMessage msg);

    /** Handles a client unregistration propagated via gossip. */
    void visit(UnregisterGossipMessage msg);
}