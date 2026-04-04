package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.DistanceWindFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Composant "Éolienne" (CDC §3.4) implémenté comme un client pub/sub basé sur
 * greffons ({@link PluginClient}).
 *
 * <p>
 * Le composant reçoit des observations de vent ({@link WindDataI}) et des
 * alertes ({@link MeteoAlertI}).
 * </p>
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class WindTurbine extends PluginClient
{
	/** Si non null, le composant exécute sa partie du scénario temporisé. */
	private final TestScenario testScenario;

	private final String turbineId;
	private final PositionI position;
	private final double maxDistance;
	private final long recentWindowMillis;
	private final MeteoAlertI.Level threshold;
	private volatile boolean safetyMode;

	private static class TimedWind
	{
		final long ts;
		final WindDataI wind;

		TimedWind(long ts, WindDataI wind)
		{
			this.ts = ts;
			this.wind = wind;
		}
	}

	private final List<TimedWind> windBuffer = new ArrayList<>();

	protected WindTurbine(
		String turbineId,
		PositionI position,
		double maxDistance,
		long recentWindowMillis,
		MeteoAlertI.Level threshold) throws Exception
	{
		this(turbineId, null, turbineId, position, maxDistance, recentWindowMillis, threshold);
	}

	protected WindTurbine(
		String reflectionInboundPortURI,
		TestScenario testScenario,
		String turbineId,
		PositionI position,
		double maxDistance,
		long recentWindowMillis,
		MeteoAlertI.Level threshold) throws Exception
	{
		super(reflectionInboundPortURI, 1, 1);
		if (turbineId == null || turbineId.isEmpty()) {
			throw new IllegalArgumentException("turbineId cannot be null/empty");
		}
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.turbineId = turbineId;
		this.position = position;
		this.maxDistance = maxDistance;
		this.recentWindowMillis = recentWindowMillis;
		this.threshold = threshold;
		this.safetyMode = false;
		this.testScenario = testScenario;
	}

	@Override
	public synchronized void start()
	{
		try {
			super.start();
			this.register(RegistrationClass.FREE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();
		if (this.testScenario != null) {
			this.traceMessage("[TimedDemo] WindTurbine.execute() rip=" + this.getReflectionInboundPortURI() + "\n");
			this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI, this.testScenario.getClockURI());
			this.getClock().waitUntilStart();
			this.executeTestScenario(this.testScenario);
		}

		// Démonstration de la réception avancée (CDC §3.5.3) : on consomme quelques
		// messages directement via le greffon de souscription.
		if (this.testScenario != null) {
			this.traceMessage("[TimedDemo] WindTurbine : consommation via waitForNextMessage(Duration)\n");
			for (int i = 0; i < 4; i++) {
				MessageI m = this.subscriptionPlugin.waitForNextMessage("channel0", Duration.ofSeconds(2));
				if (m != null) {
					this.onReceive("channel0", m);
				}
			}
		}
	}

	public PositionI getPosition()
	{
		return position;
	}

	public void subscribeToWindAndAlerts(String windChannel, String alertChannel) throws Exception
	{
		MessageFilterI windFilter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] {
				new PropertyFilter("type", new EqualsValueFilter("wind")),
				new PropertyFilter("payload", new DistanceWindFilter(this.position, this.maxDistance))
			},
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		MessageFilterI alertFilter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] { new PropertyFilter("type", new EqualsValueFilter("alert")) },
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		this.subscribe(windChannel, windFilter);
		this.subscribe(alertChannel, alertFilter);
		this.traceMessage("WindTurbine[" + turbineId + "] subscribed to " + windChannel + " and " + alertChannel + "\n");
	}

	@Override
	public void onReceive(String channel, MessageI message)
	{
		try {
			Object payload = message.getPayload();
			if (payload instanceof WindDataI) {
				onWind((WindDataI) payload, message.getTimeStamp().toEpochMilli());
			} else if (payload instanceof MeteoAlertI) {
				onAlert((MeteoAlertI) payload);
			} else {
				this.traceMessage(
					"WindTurbine[" + turbineId + "] received unknown payload on " + channel + ": " + payload + "\n");
			}
		} catch (Exception e) {
			this.logMessage("WindTurbine[" + turbineId + "] receive failed: " + e.getMessage() + "\n");
		}
	}

	private void onWind(WindDataI wind, long messageTs)
	{
		long now = Instant.now().toEpochMilli();
		windBuffer.removeIf(tw -> now - tw.ts > recentWindowMillis);

		double d = distance(this.position, wind.getPosition());
		windBuffer.add(new TimedWind(messageTs, wind));
		this.traceMessage("WindTurbine[" + turbineId + "] accept wind d=" + d + ": " + wind + "\n");

		double sumX = 0.0;
		double sumY = 0.0;
		for (TimedWind tw : windBuffer) {
			double dist = distance(this.position, tw.wind.getPosition());
			double w = 1.0 / (dist + 1e-6);
			sumX += w * tw.wind.xComponent();
			sumY += w * tw.wind.yComponent();
		}
		double orientX = -sumX;
		double orientY = -sumY;
		this.traceMessage(
			"WindTurbine[" + turbineId + "] orientation vector = (" + orientX + ", " + orientY + ") safetyMode="
				+ safetyMode + "\n");
	}

	private void onAlert(MeteoAlertI alert)
	{
		boolean concerned = false;
		for (RegionI r : alert.getRegions()) {
			if (r.in(this.position)) {
				concerned = true;
				break;
			}
		}
		if (!concerned) {
			this.traceMessage("WindTurbine[" + turbineId + "] ignore alert (not concerned): " + alert + "\n");
			return;
		}

		MeteoAlertI.Level level = (alert.getLevel() instanceof MeteoAlertI.Level)
			? (MeteoAlertI.Level) alert.getLevel()
			: null;

		this.traceMessage("WindTurbine[" + turbineId + "] received alert: " + alert + "\n");

		if (level == MeteoAlertI.Level.GREEN) {
			safetyMode = false;
			this.traceMessage("WindTurbine[" + turbineId + "] RETURN NORMAL (GREEN)\n");
			return;
		}

		if (level != null && level.ordinal() >= threshold.ordinal()) {
			safetyMode = true;
			this.traceMessage(
				"WindTurbine[" + turbineId + "] ENTER SAFETY MODE (level=" + level + ", threshold=" + threshold + ")\n");
		}
	}

	private static double distance(PositionI a, PositionI b)
	{
		if (a instanceof Position2D && b instanceof Position2D) {
			Position2D pa = (Position2D) a;
			Position2D pb = (Position2D) b;
			double dx = pa.getX() - pb.getX();
			double dy = pa.getY() - pb.getY();
			return Math.sqrt(dx * dx + dy * dy);
		}
		return Double.POSITIVE_INFINITY;
	}
}
