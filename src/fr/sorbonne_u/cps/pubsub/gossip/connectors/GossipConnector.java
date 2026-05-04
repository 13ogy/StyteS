package fr.sorbonne_u.cps.pubsub.gossip.connectors;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

public class GossipConnector
        extends AbstractConnector
        implements GossipSenderCI
{

    @Override
    public void send(GossipMessageI[] gossipMessages) throws Exception {
        ((GossipReceiverCI) this.offering).receive(gossipMessages);
    }
}