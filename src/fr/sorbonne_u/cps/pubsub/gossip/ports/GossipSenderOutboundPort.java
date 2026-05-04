package fr.sorbonne_u.cps.pubsub.gossip.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.interfaces.RequiredCI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

public class GossipSenderOutboundPort extends AbstractOutboundPort implements GossipSenderCI {
    public GossipSenderOutboundPort(ComponentI owner) throws Exception {
        super(GossipSenderCI.class, owner);
    }

    public void send(GossipMessageI[] gossipMessageIS) throws Exception {
        ((GossipSenderCI) this.getConnector()).send(gossipMessageIS);
    }
}
