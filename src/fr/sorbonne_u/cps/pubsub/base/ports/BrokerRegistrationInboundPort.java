package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;


import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Inbound port exposing the broker registration/subscription services.
 *
 * @author Bogdan Styn
 */
public class BrokerRegistrationInboundPort extends AbstractInboundPort implements RegistrationCI{

	public BrokerRegistrationInboundPort( ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}

	@Override
	public boolean registered(String receptionPortURI) throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).registered(receptionPortURI)
		);
	}

	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc)
		throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).registered(receptionPortURI, rc)
		);
	}

	@Override
	public String register(String receptionPortURI, RegistrationClass rc)
		throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).register(receptionPortURI, rc));
	}

	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc)
	throws Exception {
		return this.getOwner().handleRequest(
				o -> ((Broker) o).modifyServiceClass(receptionPortURI, rc));

	}

	@Override
	public void unregister(String receptionPortURI) throws Exception
	{
		this.getOwner().handleRequest(
				o -> {((Broker) o).unregister(receptionPortURI);
				return null;}
		);
	}

	@Override
	public boolean channelExist(String channel) throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).channelExist(channel)
		);
	}

	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel)
		throws Exception
	{
		return this.getOwner().handleRequest(
				o-> ((Broker) o).channelAuthorised(receptionPortURI, channel));
	}

	@Override
	public boolean subscribed(String receptionPortURI, String channel)
		throws Exception
	{
		return this.getOwner().handleRequest(
				o-> ((Broker) o).subscribed(receptionPortURI, channel));
	}

	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter)
		throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).subscribe(receptionPortURI, channel, filter);
			return null;
		});
	}

	@Override
	public void unsubscribe(String receptionPortURI, String channel)
		throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).unsubscribe(receptionPortURI, channel);
			return null;
		});
	}

	@Override
	public boolean modifyFilter(
		String receptionPortURI,
		String channel,
		MessageFilterI filter
		) throws Exception
	{
		return this.getOwner().handleRequest(
				o-> ((Broker) o).modifyFilter(receptionPortURI, channel, filter));
	}
}

