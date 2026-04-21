package fr.sorbonne_u.cps.pubsub.base.ports;


import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerRegistrationConnector;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Outbound port used by clients to access the broker registration service.
 *
 * @author Bogdan Styn
 */
public class ClientRegistrationOutboundPort extends AbstractOutboundPort implements RegistrationCI{

	public ClientRegistrationOutboundPort(ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}


	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		return ((RegistrationCI) this.getConnector()).registered(receptionPortURI);

	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception {

		return ((RegistrationCI) this.getConnector()).registered(receptionPortURI, rc);

	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception {

		return ((RegistrationCI) this.getConnector()).register(receptionPortURI, rc);

	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception, AlreadyRegisteredException {
		return ((RegistrationCI) this.getConnector()).modifyServiceClass(receptionPortURI, rc);

	}

	@Override
	public void unregister(String receptionPortURI) throws Exception {

		((RegistrationCI) this.getConnector()).unregister(receptionPortURI);

	}

	@Override
	public boolean channelExist(String channel) throws Exception {
		return ((RegistrationCI) this.getConnector()).channelExist(channel);

	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		return ((RegistrationCI) this.getConnector()).channelAuthorised(receptionPortURI, channel);

	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel) throws Exception{
		return ((RegistrationCI) this.getConnector()).subscribed(receptionPortURI, channel);

	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception{
		((RegistrationCI) this.getConnector()).subscribe(receptionPortURI, channel, filter);

	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel) throws Exception {
		((RegistrationCI) this.getConnector()).unsubscribe(receptionPortURI, channel);

	}

	@Override
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception {
		return ((RegistrationCI) this.getConnector()).modifyFilter(receptionPortURI, channel, filter);

	}
	
}
