package fr.sorbonne_u.cps.pubsub.gossip.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipImplementationI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;

public class GossipReceiverInboundPort
        extends AbstractInboundPort
        implements GossipReceiverCI
{
    public GossipReceiverInboundPort(ComponentI owner) throws Exception {
        super(GossipReceiverCI.class, owner);
    }


    @Override
    public void receive(GossipMessageI[] gossipMessages) throws Exception {
        this.getOwner().handleRequest(o -> {
            ((GossipImplementationI) o).receive(gossipMessages);
            return null;
        });
    }
}
