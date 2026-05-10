package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;

/**
 * Phase E.1 — opt-in abstract base for clients composed from the four
 * pub/sub plugins ({@link ClientRegistrationPlugin},
 * {@link ClientSubscriptionPlugin}, {@link ClientPublicationPlugin},
 * {@link ClientPrivilegedPlugin}).
 *
 * <p>
 * This class exists alongside {@link PluginClient}, which is kept concrete
 * because several existing demos and the {@code application/meteo} layer
 * instantiate it directly (or extend it via {@code ScenarioPluginClient}).
 * New components that want to demonstrate clean plugin composition (for
 * example dedicated {@code SubscriberClient} / {@code PublisherClient}
 * variants) should extend {@code AbstractPluginComponent} instead, override
 * {@link #scenarioExecute()} with the role-specific behaviour, and pass
 * an {@link RegistrationClass} at construction time so that the lifecycle
 * template registers them automatically.
 * </p>
 *
 * <h2>Lifecycle template</h2>
 * <ol>
 *   <li>The constructor installs the four plugins and configures the
 *       reception callback to {@link #onReceive(String, MessageI)} (which
 *       subclasses may override).</li>
 *   <li>BCM4Java calls {@link #execute()}; this method is {@code final} and
 *       follows the convention "register in {@code execute()}, never in
 *       {@code start()} or constructors":
 *     <ul>
 *       <li>{@code super.execute()} runs first;</li>
 *       <li>if an {@code initialRC} was supplied, the registration plugin's
 *           {@code register(initialRC)} is invoked;</li>
 *       <li>then {@link #scenarioExecute()} is called, where subclasses
 *           drive the actual scenario (subscribe / publish / privileged
 *           channel management).</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>
 * Subclasses that need additional plugins should install them in their own
 * constructor <em>after</em> calling {@code super(...)}; they will be live
 * before {@link #execute()} runs.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = {
	RegistrationCI.class,
	PublishingCI.class,
	PrivilegedClientCI.class
})
public abstract class AbstractPluginComponent extends AbstractComponent
{
	protected final ClientRegistrationPlugin registrationPlugin;
	protected final ClientSubscriptionPlugin subscriptionPlugin;
	protected final ClientPublicationPlugin publicationPlugin;
	protected final ClientPrivilegedPlugin privilegedPlugin;

	/** Service class to register with on {@link #execute()}; {@code null}
	 *  means "do not auto-register, the subclass will register manually
	 *  inside {@link #scenarioExecute()}". */
	protected final RegistrationClass initialRC;

	protected AbstractPluginComponent(
		String reflectionInboundPortURI,
		int nbThreads,
		int nbSchedulableThreads,
		String brokerReflectionURI,
		RegistrationClass initialRC) throws Exception
	{
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads);

		this.initialRC = initialRC;

		this.registrationPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
		this.registrationPlugin.setPluginURI(reflectionInboundPortURI + "-registration-plugin");
		this.installPlugin(this.registrationPlugin);

		this.subscriptionPlugin = new ClientSubscriptionPlugin(
			this.registrationPlugin,
			this::onReceive);
		this.subscriptionPlugin.setPluginURI(reflectionInboundPortURI + "-subscription-plugin");
		this.installPlugin(this.subscriptionPlugin);

		this.publicationPlugin = new ClientPublicationPlugin(this.registrationPlugin, brokerReflectionURI);
		this.publicationPlugin.setPluginURI(reflectionInboundPortURI + "-publication-plugin");
		this.installPlugin(this.publicationPlugin);

		this.privilegedPlugin = new ClientPrivilegedPlugin(this.registrationPlugin, brokerReflectionURI);
		this.privilegedPlugin.setPluginURI(reflectionInboundPortURI + "-privileged-plugin");
		this.installPlugin(this.privilegedPlugin);
	}

	/**
	 * Final lifecycle template — see class javadoc for the ordered steps.
	 * Subclasses override {@link #scenarioExecute()} instead of this method.
	 */
	@Override
	public final void execute() throws Exception
	{
		super.execute();
		if (this.initialRC != null && !this.registrationPlugin.registered()) {
			try {
				this.registrationPlugin.register(this.initialRC);
			} catch (AlreadyRegisteredException e) {
				// Idempotent: another path may have already registered.
			}
		}
		this.scenarioExecute();
	}

	/**
	 * Subclasses implement the actual scenario here (subscribe, publish,
	 * privileged-channel actions, optional accelerated-clock test scenario,
	 * etc.). The default implementation does nothing so that subclasses
	 * with no scenario logic still compile.
	 *
	 * @throws Exception any error from the scenario logic.
	 */
	protected void scenarioExecute() throws Exception
	{
		// no-op default — subclasses override.
	}

	/** URI of this client's {@link ReceivingCI} inbound port. */
	public String getReceptionPortURI()
	{
		try {
			return this.registrationPlugin.getReceptionPortURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Default reception callback wired into the subscription plugin.
	 * Subclasses can override to implement role-specific message handling
	 * (e.g. enqueue, log, forward to another component).
	 */
	public void onReceive(String channel, MessageI message)
	{
		if (message == null) {
			this.traceMessage(
				"AbstractPluginComponent " + this.getReflectionInboundPortURI()
					+ " received empty batch on " + channel + "\n");
			return;
		}
		this.traceMessage(
			"AbstractPluginComponent " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " timestamp=" + message.getTimeStamp() + "\n");
	}
}
