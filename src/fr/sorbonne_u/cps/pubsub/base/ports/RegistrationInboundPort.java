package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;


import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Inbound port exposing the broker registration/subscription services.
 *
 * <p>
 * Phase D.5 convention: business exceptions declared on the CI propagate
 * verbatim; every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}. Phase D.3: void-returning methods
 * ({@link #unregister(String)}, {@link #subscribe(String,String,MessageFilterI)},
 * {@link #unsubscribe(String,String)}) are dispatched through the broker's
 * dedicated reception executor so the RMI thread returns immediately.
 * Exceptions raised inside the lambda are logged on the broker tracer
 * (the contract is asynchronous; callers cannot observe them).
 * </p>
 *
 * @author Bogdan Styn
 */
public class RegistrationInboundPort extends AbstractInboundPort implements RegistrationCI{

	public RegistrationInboundPort( ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}

	/**
	 * Create the inbound port with an explicit, deterministic URI
	 * (Phase C.3). This lets the broker derive its registration port
	 * URI from its reflection inbound port URI, removing the need for
	 * a global static.
	 */
	public RegistrationInboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, RegistrationCI.class, owner);
	}

	@Override
	public boolean registered(String receptionPortURI) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).registered(receptionPortURI)
			);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc)
		throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).registered(receptionPortURI, rc)
			);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc)
		throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).register(receptionPortURI, rc));
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc)
	throws Exception {
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).modifyServiceClass(receptionPortURI, rc));
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unregister(String receptionPortURI) throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).unregister(receptionPortURI);
				} catch (Exception e) {
					((Broker) o).logMessage("[unregister async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelExist(String channel) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).channelExist(channel)
			);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel)
		throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o-> ((Broker) o).channelAuthorised(receptionPortURI, channel));
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel)
		throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o-> ((Broker) o).subscribed(receptionPortURI, channel));
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter)
		throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).subscribe(receptionPortURI, channel, filter);
				} catch (Exception e) {
					((Broker) o).logMessage("[subscribe async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel)
		throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).unsubscribe(receptionPortURI, channel);
				} catch (Exception e) {
					((Broker) o).logMessage("[unsubscribe async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean modifyFilter(
		String receptionPortURI,
		String channel,
		MessageFilterI filter
		) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o-> ((Broker) o).modifyFilter(receptionPortURI, channel, filter));
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
