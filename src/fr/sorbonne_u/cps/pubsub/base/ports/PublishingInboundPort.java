package fr.sorbonne_u.cps.pubsub.base.ports;


import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Inbound port exposing the broker publishing service.
 *
 * <p>
 * Phase D.3: every void-returning method routes the actual broker call
 * through {@link Broker#getReceptionExecutorIndex()} via
 * {@link fr.sorbonne_u.components.AbstractComponent#runTask(int, fr.sorbonne_u.components.ComponentI.ComponentTask)},
 * so the RMI dispatch thread returns immediately. Any exception raised
 * inside the lambda (including precondition failures from CDC §C.2) is
 * logged on the broker tracer rather than propagated &mdash; publish is
 * fire-and-forget per CDC §3.5.
 * </p>
 *
 * <p>
 * Phase D.5: the (currently empty) outer try/catch follows the project
 * convention &mdash; business exceptions declared on the CI propagate
 * verbatim, every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn
 */
public class PublishingInboundPort extends AbstractInboundPort implements PublishingCI {

	public PublishingInboundPort(ComponentI owner) throws Exception {
		super(PublishingCI.class, owner);

	}
	public PublishingInboundPort(Class c, ComponentI owner) throws Exception{
		super (c, owner);
	}

	/**
	 * Explicit-URI constructor (Phase C.3): lets the broker pick a
	 * deterministic URI derived from its reflection inbound port URI.
	 */
	public PublishingInboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, PublishingCI.class, owner);
	}

	/**
	 * Explicit-URI + interface constructor (Phase C.3) used by subclasses
	 * (e.g. {@link PrivilegedClientInboundPort}) that want a deterministic
	 * URI while still declaring a more specific implemented interface.
	 */
	public PublishingInboundPort(String uri, Class c, ComponentI owner) throws Exception {
		super(uri, c, owner);
	}

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message)
		throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).publish(receptionPortURI, channel, message);
				} catch (Exception e) {
					((Broker) o).logMessage("[publish async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages
		) throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).publish(receptionPortURI, channel, messages);
				} catch (Exception e) {
					((Broker) o).logMessage("[publish(bulk) async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
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
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, message, notificationInbounhdPortURI);
				} catch (Exception e) {
					((Broker) o).logMessage("[asyncPublishAndNotify async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, messages, notificationInbounhdPortURI);
				} catch (Exception e) {
					((Broker) o).logMessage("[asyncPublishAndNotify(bulk) async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
