package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * Demo-only client component: a {@link PluginClient} that executes the timed
 * {@link TestScenario} steps assigned to its reflection inbound port URI.
 *
 * <p>
 * BCM4Java's {@code executeTestScenario(...)} schedules only the steps of the
 * component that calls it, hence every participant must call it.
 * </p>
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class ScenarioPluginClient extends PluginClient
{
	protected final TestScenario scenario;

	protected ScenarioPluginClient(
			String reflectionInboundPortURI,
			TestScenario scenario,
			int nbThreads,
			int nbSchedulableThreads) throws Exception
	{
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads);
		this.scenario = scenario;
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();
		if (this.scenario != null && this.scenario.entityAppearsIn(this.getReflectionInboundPortURI())) {
			this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI, this.scenario.getClockURI());
			// executeTestScenario already waits until the accelerated clock start.
			this.executeTestScenario(this.scenario);
		}
	}

	// ---------------------------------------------------------------------
	// Reception hook override (make receptions always visible in console)
	// ---------------------------------------------------------------------

	@Override
	public void onReceive(String channel, fr.sorbonne_u.cps.pubsub.interfaces.MessageI message)
	{
		super.onReceive(channel, message);
		if (message != null) {
			System.out.print(
				"[MidSemScenario][RECEIVE] " + this.getReflectionInboundPortURI()
					+ " <- " + channel + " payload=" + message.getPayload() + "\n");
		}
	}
}
