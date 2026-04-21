package fr.sorbonne_u.cps.pubsub.base.connectors;


import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Connector used by clients to call the broker registration service.
 *
 * @author Bogdan Styn
 */
public class ClientBrokerRegistrationConnector extends AbstractConnector implements RegistrationCI {

	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		return ((RegistrationCI) this.offering).registered(receptionPortURI);

	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception {
		return ((RegistrationCI) this.offering).registered(receptionPortURI, rc);

	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception {
		return ((RegistrationCI) this.offering).register(receptionPortURI, rc);

	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception {
		return ((RegistrationCI) this.offering).modifyServiceClass(receptionPortURI, rc);

	}

	@Override
	public void unregister(String receptionPortURI) throws Exception {
		((RegistrationCI) this.offering).unregister(receptionPortURI);

	}

	@Override
	public boolean channelExist(String channel) throws Exception {
		return ((RegistrationCI) this.offering).channelExist(channel);
	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		return ((RegistrationCI) this.offering).channelAuthorised(receptionPortURI, channel);

	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel) throws Exception {
		return ((RegistrationCI) this.offering).subscribed(receptionPortURI, channel);

	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception {
		((RegistrationCI) this.offering).subscribe(receptionPortURI, channel, filter);

	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel) throws Exception {
		((RegistrationCI) this.offering).unsubscribe(receptionPortURI, channel);

	}

	@Override
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter)
		throws Exception
	{
		return ((RegistrationCI) this.offering).modifyFilter(receptionPortURI, channel, filter);

	}
}
