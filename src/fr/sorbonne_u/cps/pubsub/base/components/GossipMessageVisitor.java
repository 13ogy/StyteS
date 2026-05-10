package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.cps.pubsub.gossip.messages.*;

public interface GossipMessageVisitor {
    void visit(RegisterGossipMessage msg);
    void visit(PublishGossipMessage msg);
    void visit(CreateChannelGossipMessage msg);
    void visit(DestroyChannelGossipMessage msg);
    void visit(ModifyServiceClassGossipMessage msg);
    void visit(ModifyAuthorisedUsersGossipMessage msg);
    void visit(UnregisterGossipMessage msg);
}
