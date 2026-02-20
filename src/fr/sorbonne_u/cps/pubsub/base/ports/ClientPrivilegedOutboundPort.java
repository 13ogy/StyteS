package fr.sorbonne_u.cps.pubsub.base.ports;

import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

public class ClientPrivilegedOutboundPort extends AbstractOutboundPort implements PrivilegedClientCI {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ClientPrivilegedOutboundPort(ComponentI owner)
			throws Exception {
		super(PrivilegedClientCI.class, owner);
	}


	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		 ((PrivilegedClientCI)this.getConnector()).publish(receptionPortURI, channel, message);
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		((PrivilegedClientCI)this.getConnector()).publish(receptionPortURI, channel, messages);
	}


	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception {
		return ((PrivilegedClientCI)this.getConnector()).channelQuotaReached(receptionPortURI);
	}


	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception{
		((PrivilegedClientCI)this.getConnector()).destroyChannel(receptionPortURI, channel);
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception  {
		((PrivilegedClientCI)this.getConnector()).destroyChannelNow(receptionPortURI, channel);
	}


	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception {
		return ((PrivilegedClientCI)this.getConnector()).hasCreatedChannel(receptionPortURI,channel);
	}


	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception {
		((PrivilegedClientCI)this.getConnector()).createChannel(receptionPortURI, channel, autorisedUsers);		
	}


	@Override
	public boolean isAuthorisedUser(String channel, String uri) throws Exception {
		return ((PrivilegedClientCI)this.getConnector()).isAuthorisedUser(channel,uri);
	}


	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception {
		((PrivilegedClientCI)this.getConnector()).modifyAuthorisedUsers(receptionPortURI,channel,autorisedUsers);
		
	}


	@Override
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression)
			throws Exception {
		((PrivilegedClientCI)this.getConnector()).removeAuthorisedUsers(receptionPortURI,channel,regularExpression);
		
	}

}
