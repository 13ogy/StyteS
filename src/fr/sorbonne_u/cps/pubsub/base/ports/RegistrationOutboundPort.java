package fr.sorbonne_u.cps.pubsub.base.ports;


import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Outbound port used by clients to access the broker registration service.
 *
 * <p>
 * Phase D.5: business exceptions declared on the CI propagate verbatim;
 * every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn
 */
public class RegistrationOutboundPort extends AbstractOutboundPort implements RegistrationCI{

	public RegistrationOutboundPort(ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}


	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).registered(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).registered(receptionPortURI, rc);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).register(receptionPortURI, rc);
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception, AlreadyRegisteredException {
		try {
			return ((RegistrationCI) this.getConnector()).modifyServiceClass(receptionPortURI, rc);
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unregister(String receptionPortURI) throws Exception {
		try {
			((RegistrationCI) this.getConnector()).unregister(receptionPortURI);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelExist(String channel) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).channelExist(channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).channelAuthorised(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel) throws Exception{
		try {
			return ((RegistrationCI) this.getConnector()).subscribed(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception{
		try {
			((RegistrationCI) this.getConnector()).subscribe(receptionPortURI, channel, filter);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel) throws Exception {
		try {
			((RegistrationCI) this.getConnector()).unsubscribe(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).modifyFilter(receptionPortURI, channel, filter);
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
