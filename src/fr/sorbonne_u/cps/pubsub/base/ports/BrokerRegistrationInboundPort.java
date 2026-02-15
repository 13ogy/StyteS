package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

public class BrokerRegistrationInboundPort extends AbstractInboundPort implements RegistrationCI{

	public BrokerRegistrationInboundPort( ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}

	@Override
	public boolean registered(String receptionPortURI) throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.registered(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc)
		throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.registered(receptionPortURI, rc);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc)
		throws RemoteException, AlreadyRegisteredException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.register(receptionPortURI, rc);
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc)
		throws RemoteException, AlreadyRegisteredException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.modifyServiceClass(receptionPortURI, rc);
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unregister(String receptionPortURI) throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.unregister(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelExist(String channel) throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.channelExist(channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel)
		throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.channelAuthorised(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel)
		throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.subscribed(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter)
		throws RemoteException, UnknownChannelException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.subscribe(receptionPortURI, channel, filter);
		} catch (UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel)
		throws RemoteException, UnknownChannelException, NotSubscribedChannelException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.unsubscribe(receptionPortURI, channel);
		} catch (UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean modifyFilter(
		String receptionPortURI,
		String channel,
		MessageFilterI filter
		) throws RemoteException, UnknownChannelException, NotSubscribedChannelException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.modifyFilter(receptionPortURI, channel, filter);
		} catch (UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
	

	

}
