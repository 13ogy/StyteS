package fr.sorbonne_u.cps.pubsub.base.ports;


import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Inbound port exposing the broker publishing service.
 *
 * @author Bogdan Styn
 */
public class BrokerPublishingInboundPort extends AbstractInboundPort implements PublishingCI {

	public BrokerPublishingInboundPort(ComponentI owner) throws Exception {
		super(PublishingCI.class, owner);

	}
	public BrokerPublishingInboundPort(Class c, ComponentI owner) throws Exception{
		super (c, owner);
	}

	/**
	 * Explicit-URI constructor (Phase C.3): lets the broker pick a
	 * deterministic URI derived from its reflection inbound port URI.
	 */
	public BrokerPublishingInboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, PublishingCI.class, owner);
	}

	/**
	 * Explicit-URI + interface constructor (Phase C.3) used by subclasses
	 * (e.g. {@link BrokerPrivilegedInboundPort}) that want a deterministic
	 * URI while still declaring a more specific implemented interface.
	 */
	public BrokerPublishingInboundPort(String uri, Class c, ComponentI owner) throws Exception {
		super(uri, c, owner);
	}

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message)
		throws Exception
	{

		this.getOwner().handleRequest(o -> {
			((Broker) o).publish(receptionPortURI, channel,message);
			return null;
		});
	}

	@Override
	public void publish(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages
		) throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).publish(receptionPortURI, channel,messages);
			return null;
		});
	}

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		MessageI message,
		String notificationInbounhdPortURI
		) throws Exception
	{
		this.getOwner().runTask(
				o -> { try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, message, notificationInbounhdPortURI);
				} catch (Exception e) { e.printStackTrace(); }}
		);
	}

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		this.getOwner().runTask(
				o -> { try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, messages, notificationInbounhdPortURI);
				} catch (Exception e) { e.printStackTrace(); }}
		);
	}
}
