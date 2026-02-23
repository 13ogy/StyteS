package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Minimal demo for CDC §3.5: client operations implemented using plugins.
 * Excludes CDC §3.5.3.
 */
public class DemoPluginsClient extends AbstractCVM
{
	public DemoPluginsClient() throws Exception
	{
		super();
	}

	public static final String C1_URI = "plugin-client-1";
	public static final String C2_URI = "plugin-client-2";
	public static final String CHANNEL = "channel0";

	@Override
	public void deploy() throws Exception
	{
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		AbstractComponent.createComponent(
			PluginClient.class.getCanonicalName(),
			new Object[] { C1_URI, 1, 0 });
		AbstractComponent.createComponent(
			PluginClient.class.getCanonicalName(),
			new Object[] { C2_URI, 1, 0 });

		super.deploy();

		// Drive a simple exchange using component tasks.
		this.toggleTracing(C2_URI);
		this.toggleTracing(C1_URI);
		this.toggleLogging(C2_URI);
		this.toggleLogging(C1_URI);
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();
		// Drive a deterministic scenario from the CVM.
		this.executeComponent(C2_URI); // make sure C2 is executed first
		this.executeComponent(C1_URI);
	}

	@Override
	public void executeComponent(String componentURI) throws Exception
	{
		// Override execution of PluginClient components with our scenario.
		if (C2_URI.equals(componentURI)) {
			this.uri2component.get(C2_URI).runTask(o -> {
				try {
					PluginClient c2 = (PluginClient) o;
					c2.register(RegistrationClass.FREE);
					Thread.sleep(1500);
					c2.subscribe(CHANNEL,
						new fr.sorbonne_u.cps.pubsub.messages.MessageFilter(
							new fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI[0],
							new fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertiesFilterI[0],
							new fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter()));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		} else if (C1_URI.equals(componentURI)) {
			this.uri2component.get(C1_URI).runTask(o -> {
				try {
					PluginClient c1 = (PluginClient) o;
					c1.register(RegistrationClass.FREE);
					Thread.sleep(2500);
					c1.publish(CHANNEL, new fr.sorbonne_u.cps.pubsub.messages.Message("hello-from-plugin-client"));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		} else {
			super.executeComponent(componentURI);
		}
	}

	public static void main(String[] args)
	{
		try {
			DemoPluginsClient cvm = new DemoPluginsClient();
			cvm.startStandardLifeCycle(4000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
