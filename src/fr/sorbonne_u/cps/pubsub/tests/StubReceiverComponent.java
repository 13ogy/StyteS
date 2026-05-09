package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Minimal stub component that publishes a {@link ReceivingCI} inbound port so
 * the {@link fr.sorbonne_u.cps.pubsub.base.components.Broker} can connect to
 * it during broker registration tests.
 *
 * <p>
 * This component does nothing beyond creating and publishing the port. It is
 * used in {@link BrokerRegistrationTest} instead of
 * {@link fr.sorbonne_u.cps.pubsub.base.components.PluginClient} to avoid a
 * BCM precondition that fires with {@code -ea} when a plugin tries to add an
 * offered interface that is already declared in {@code @OfferedInterfaces}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
public class StubReceiverComponent extends AbstractComponent
{
	private final StubReceivingInboundPort receivingPort;

	/** Parameterless constructor for BCM's {@code createComponent} reflective factory. */
	protected StubReceiverComponent() throws Exception
	{
		super(1, 0);
		this.receivingPort = new StubReceivingInboundPort(this);
		this.receivingPort.publishPort();
	}

	/**
	 * Return the URI of the published {@link ReceivingCI} inbound port.
	 *
	 * @return URI as a String; never null.
	 */
	public String getReceptionPortURI() throws Exception
	{
		return this.receivingPort.getPortURI();
	}
}
