package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;

/**
 * Midterm demo v2: illustrates CDC §3.5.2 together
 * with a pub/sub scenario.
 *
 * <p>
 * Scenario:
 * </p>
 * <ul>
 *   <li>Start a broker.</li>
 *   <li>Create 2 plugin-based clients: an owner (STANDARD) and a subscriber (FREE).</li>
 *   <li>The owner creates a privileged channel and authorises only itself and the subscriber.</li>
 *   <li>The subscriber subscribes with a single property filter (type == "demo").</li>
 *   <li>The owner publishes 2 messages: one accepted and one rejected by the filter.</li>
 *   <li>The owner then destroys the channel.</li>
 * </ul>
 *
 * @author Bogdan Styn
 */
public class DemoMidTermDemoV2 extends AbstractCVM
{
	public static final String OWNER_URI = "midterm-owner";
	public static final String SUBSCRIBER_URI = "midterm-subscriber";

	public static final String PRIV_CHANNEL = "midterm-priv-channel";

	public DemoMidTermDemoV2() throws Exception
	{
		super();
	}

	@Override
	public void deploy() throws Exception
	{
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { OWNER_URI, 1, 0 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { SUBSCRIBER_URI, 1, 0 });

		super.deploy();

		this.toggleTracing(OWNER_URI);
		this.toggleTracing(SUBSCRIBER_URI);
		this.toggleLogging(OWNER_URI);
		this.toggleLogging(SUBSCRIBER_URI);
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();

		PluginClient owner = (PluginClient) this.uri2component.get(OWNER_URI);
		PluginClient subscriber = (PluginClient) this.uri2component.get(SUBSCRIBER_URI);

		owner.register(RegistrationClass.STANDARD);
		subscriber.register(RegistrationClass.FREE);

		String subscriberReceptionURI = subscriber.getReceptionPortURI();
		String ownerReceptionURI = owner.getReceptionPortURI();
		String regex =
			"^(" + java.util.regex.Pattern.quote(subscriberReceptionURI)
				+ "|" + java.util.regex.Pattern.quote(ownerReceptionURI) + ")$";

		System.out.println("[MidTermDemoV2] owner creates channel " + PRIV_CHANNEL + " authorisedUsers=" + regex);
		owner.createChannel(PRIV_CHANNEL, regex);

		MessageFilterI filter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] {
				new PropertyFilter("type", new EqualsValueFilter("demo"))
			},
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		System.out.println("[MidTermDemoV2] subscriber subscribes with filter: type==demo");
		subscriber.subscribe(PRIV_CHANNEL, filter);

		Thread.sleep(200L);

		Message accepted = new Message("accepted-message");
		accepted.putProperty("type", "demo");
		System.out.println("[MidTermDemoV2] owner publishes accepted message");
		owner.publish(PRIV_CHANNEL, accepted);

		Message rejected = new Message("rejected-message");
		rejected.putProperty("type", "other");
		System.out.println("[MidTermDemoV2] owner publishes rejected message");
		owner.publish(PRIV_CHANNEL, rejected);

		Thread.sleep(500L);

		System.out.println("[MidTermDemoV2] owner destroys channel " + PRIV_CHANNEL);
		owner.destroyChannelNow(PRIV_CHANNEL);
	}

	public static void main(String[] args)
	{
		try {
			DemoMidTermDemoV2 cvm = new DemoMidTermDemoV2();
			cvm.startStandardLifeCycle(6000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
