package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;

/**
 * Integration demo for step 2 (STANDARD/PREMIUM + privileged channels).
 *
 * Scenario:
 * - start a broker
 * - create 3 clients: owner (STANDARD), authorised subscriber (FREE), unauthorised subscriber (FREE)
 * - owner creates a privileged channel with an authorisedUsers regex that matches only the authorised subscriber
 * - authorised subscriber subscribes successfully
 * - unauthorised subscriber subscription is rejected
 * - owner publishes one message -> delivered only to authorised subscriber
 * - quota check: create channels until STANDARD quota is exceeded -> ChannelQuotaExceededException expected
 */
public class DemoAudit2Privileged extends AbstractCVM
{
	public static final String PRIV_CHANNEL = "priv-channel0";

	public DemoAudit2Privileged() throws Exception
	{
		super();
	}

	@Override
	public void deploy() throws Exception
	{
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		String ownerURI =
			AbstractComponent.createComponent(Client.class.getCanonicalName(), new Object[] { 1, 0 });
		String authorisedURI =
			AbstractComponent.createComponent(Client.class.getCanonicalName(), new Object[] { 1, 0 });
		String unauthorisedURI =
			AbstractComponent.createComponent(Client.class.getCanonicalName(), new Object[] { 1, 0 });

		super.deploy();

		Client owner = (Client) this.uri2component.get(ownerURI);
		Client authorised = (Client) this.uri2component.get(authorisedURI);
		Client unauthorised = (Client) this.uri2component.get(unauthorisedURI);

		// Register clients with service classes
		owner.register(RegistrationClass.STANDARD);
		authorised.register(RegistrationClass.FREE);
		unauthorised.register(RegistrationClass.FREE);

		// Create privileged channel allowing only the owner and the authorised subscriber
		// (both are identified by their reception port URI).
		String authorisedReceptionURI = authorised.getReceptionPortURI();
		String ownerReceptionURI = owner.getReceptionPortURI();
		String regex =
			"^(" + java.util.regex.Pattern.quote(authorisedReceptionURI)
				+ "|" + java.util.regex.Pattern.quote(ownerReceptionURI) + ")$";
		System.out.println("[DemoAudit2] Creating privileged channel " + PRIV_CHANNEL + " authorisedUsers=" + regex);
		owner.createChannel(PRIV_CHANNEL, regex);

		// Prepare a filter that accepts type == demo
		MessageFilterI filter =
			new MessageFilter(
				new MessageFilterI.PropertyFilterI[] {
					new PropertyFilter("type", new EqualsValueFilter("demo"))
				},
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());

		// authorised subscribe OK
		System.out.println("[DemoAudit2] authorised subscriber subscribes...");
		authorised.subscribe(PRIV_CHANNEL, filter);

		// unauthorised subscribe must fail
		System.out.println("[DemoAudit2] unauthorised subscriber tries to subscribe (expected failure)...");
		try {
			unauthorised.subscribe(PRIV_CHANNEL, filter);
			throw new AssertionError("Expected UnauthorisedClientException not thrown.");
		} catch (Exception expected) {
			// Depending on the ports/connectors, UnauthorisedClientException can be wrapped
			// inside RemoteException. For the demo, it is enough to show that it is rejected.
			System.out.println("[DemoAudit2] unauthorised subscribe rejected as expected: " + expected.getClass().getSimpleName());
		}

		Thread.sleep(200L);

		// Publish one message; only authorised should receive
		Message m = new Message("Hello privileged");
		m.putProperty("type", "demo");

		System.out.println("[DemoAudit2] owner publishes on privileged channel...");
		owner.publish(PRIV_CHANNEL, m);

		Thread.sleep(500L);

		// Quota demo for STANDARD (quota = " + Broker.STANDARD_PRIVILEGED_CHANNEL_QUOTA + ")
		System.out.println("[DemoAudit2] quota test for STANDARD...");
		try {
			for (int i = 1; i <= Broker.STANDARD_PRIVILEGED_CHANNEL_QUOTA + 1; i++) {
				String c = "priv-q" + i;
				owner.createChannel(c, ".*");
				System.out.println("[DemoAudit2] created channel " + c);
			}
			throw new AssertionError("Expected ChannelQuotaExceededException not thrown.");
		} catch (ChannelQuotaExceededException expected) {
			System.out.println("[DemoAudit2] quota exceeded as expected.");
		}
	}

	public static void main(String[] args)
	{
		try {
			DemoAudit2Privileged cvm = new DemoAudit2Privileged();
			cvm.startStandardLifeCycle(6000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
