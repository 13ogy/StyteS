package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Minimal ReceivingCI inbound port used by {@link StubReceiverComponent}
 * in broker registration tests.
 *
 * <p>
 * The port does nothing on {@code receive()} — it is only needed so the
 * broker can call {@code doPortConnection()} against a real published
 * {@link ReceivingCI} port URI.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class StubReceivingInboundPort
	extends AbstractInboundPort
	implements ReceivingCI
{
	private static final long serialVersionUID = 1L;

	public StubReceivingInboundPort(ComponentI owner) throws Exception
	{
		super(ReceivingCI.class, owner);
	}

	/** No-op: registration tests do not deliver messages. */
	@Override
	public void receive(String channel, MessageI message) throws Exception
	{
		// stub — intentionally empty
	}

	/** No-op: registration tests do not deliver messages. */
	@Override
	public void receive(String channel, MessageI[] messages) throws Exception
	{
		// stub — intentionally empty
	}
}
