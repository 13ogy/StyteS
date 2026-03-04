package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * Simple component whose sole responsibility is to run a BCM4Java
 * {@link TestScenario} once the component is started.
 */
public class ScenarioRunner extends AbstractComponent
{
	protected final TestScenario scenario;

	protected ScenarioRunner(String reflectionInboundPortURI,
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
		if (this.scenario != null) {
			this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI, this.scenario.getClockURI());
			this.executeTestScenario(this.scenario);
		}
	}

	@Override
	public void shutdown() throws ComponentShutdownException
	{
		super.shutdown();
	}
}
