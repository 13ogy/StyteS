package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.cps.pubsub.base.ports.BrokerPriviledgedInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.components.connectors.AbstractConnector;



public class ClientBrokerPriviledgedConnector extends AbstractConnector implements PrivilegedClientCI {


	
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}


	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws RemoteException {
		try {
			return ((BrokerPriviledgedInboundPort) this.offering).channelQuotaReached(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}



	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).destroyChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).destroyChannelNow(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}

	}

	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws RemoteException {

		try {
			return ((BrokerPriviledgedInboundPort) this.offering).hasCreatedChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	
			
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).createChannel(receptionPortURI, channel,autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
		
	}

	@Override
	public boolean isAuthorisedUser(String channel, String uri) throws RemoteException {
		try {
			return ((BrokerPriviledgedInboundPort) this.offering).isAuthorisedUser(channel, uri);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}		
	}

	@Override
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression)
			throws RemoteException {
		try {
			((BrokerPriviledgedInboundPort) this.offering).removeAuthorisedUsers(receptionPortURI, channel, regularExpression);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}		
		
	}

}
