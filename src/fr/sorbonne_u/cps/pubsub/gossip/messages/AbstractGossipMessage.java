package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

public abstract class AbstractGossipMessage implements GossipMessageI {

    abstract public void accept(GossipMessageVisitor visitor);

}
