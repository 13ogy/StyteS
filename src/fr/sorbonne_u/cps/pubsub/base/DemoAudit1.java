package fr.sorbonne_u.cps.pubsub.base;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;

/**
 * End-to-end integration demo for Audit 1 (FREE).
 *
 * <p>
 * Scenario:
 * </p>
 * <ol>
 *   <li>Start a broker.</li>
 *   <li>Start 2 clients: subscriber + publisher.</li>
 *   <li>Both register as FREE.</li>
 *   <li>Subscriber subscribes to channel0 with filter: property "type" == "demo".</li>
 *   <li>Publisher publishes one message on channel0 with property "type" == "demo".</li>
 *   <li>Subscriber prints a trace when receiving the message.</li>
 * </ol>
 */
public class DemoAudit1 extends AbstractCVM
{
	public DemoAudit1() throws Exception
	{
		super();
	}

	@Override
	public void deploy() throws Exception
	{
		String brokerURI =
			AbstractComponent.createComponent(
				Broker.class.getCanonicalName(),
				new Object[] { 2, 0 });

		String subscriberURI =
			AbstractComponent.createComponent(
				Client.class.getCanonicalName(),
				new Object[] { 1, 0 });

		String publisherURI =
			AbstractComponent.createComponent(
				Client.class.getCanonicalName(),
				new Object[] { 1, 0 });

		super.deploy();

		Client sub = (Client) this.uri2component.get(subscriberURI);
		Client pub = (Client) this.uri2component.get(publisherURI);

		sub.register(RegistrationClass.FREE);
		pub.register(RegistrationClass.FREE);

		MessageFilter filter =
			new MessageFilter(
				new fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI[] {
					new PropertyFilter("type", new EqualsValueFilter("demo"))
				},
				new fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());

		sub.traceMessage("Subscribing to channel0 with filter type==demo...\n");
		sub.subscribe("channel0", filter);

		// Give some time for subscription to be processed before publishing.
		Thread.sleep(200L);

		Message m = new Message("Hello audit1");
		m.putProperty("type", "demo");

		pub.traceMessage("Publishing message on channel0...\n");
		pub.publish("channel0", m);

		// Give some time for delivery before lifecycle ends.
		Thread.sleep(500L);
	}

	public static void main(String[] args)
	{
		try {
			DemoAudit1 cvm = new DemoAudit1();
			cvm.startStandardLifeCycle(4000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
