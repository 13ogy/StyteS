package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

public class BrokerPriviledgedInboundPort extends AbstractInboundPort implements PrivilegedClientCI {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public BrokerPriviledgedInboundPort(ComponentI owner) throws Exception {
		super(PrivilegedClientCI.class, owner);

	}


	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public boolean isAuthorisedUser(String channel, String uri) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression)
			throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception {
		// TODO Auto-generated method stub
		
	}


	
}