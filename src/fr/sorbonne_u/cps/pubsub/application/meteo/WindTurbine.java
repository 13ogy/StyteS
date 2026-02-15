package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CDC §3.4 wind turbine component.
 *
 * Receives wind data (WindDataI) and meteo alerts (MeteoAlertI) and:
 * - for winds: keeps recent + nearby observations and computes an orientation vector
 *   as a weighted vector sum by distance, then takes the negation.
 * - for alerts: enters/leaves safety mode depending on alert level threshold.
 */
public class WindTurbine extends AbstractComponent
{
	private final Client psClient;
	private final String turbineId;
	private final PositionI position;

	// Parameters for CDC §3.4.1
	private final double maxDistance;
	private final long recentWindowMillis;

	// Parameters for CDC §3.4.2
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
		super(1, 0);
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

		this.psClient = new Client(1, 0) {
			@Override
			public void receive(String channel, MessageI message)
			{
				WindTurbine.this.onReceive(channel, message);
			}
		};
	}

	@Override
	public synchronized void start()
	{
		try {
			super.start();
			this.psClient.register(RegistrationClass.FREE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public PositionI getPosition()
	{
		return position;
	}

	public void subscribeToWindAndAlerts(String windChannel, String alertChannel) throws Exception
	{
		// Ensure the internal pub/sub client is registered (ports connected) before subscribing.
		// In this CVM demo, we may call subscribe immediately after deploy(), before the component life-cycle
		// reaches start(). Registering here makes the demo deterministic.
		this.psClient.register(RegistrationClass.FREE);
		MessageFilterI windFilter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] { new PropertyFilter("type", new EqualsValueFilter("wind")) },
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		MessageFilterI alertFilter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] { new PropertyFilter("type", new EqualsValueFilter("alert")) },
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		psClient.subscribe(windChannel, windFilter);
		psClient.subscribe(alertChannel, alertFilter);
		System.out.println("WindTurbine[" + turbineId + "] subscribed to " + windChannel + " and " + alertChannel);
	}

	private void onReceive(String channel, MessageI message)
	{
		try {
			Object payload = message.getPayload();
			if (payload instanceof WindDataI) {
				onWind((WindDataI) payload, message.getTimeStamp().toEpochMilli());
			} else if (payload instanceof MeteoAlertI) {
				onAlert((MeteoAlertI) payload);
			} else {
				System.out.println("WindTurbine[" + turbineId + "] received unknown payload on " + channel + ": " + payload);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void onWind(WindDataI wind, long messageTs)
	{
		long now = Instant.now().toEpochMilli();
		// keep only recent
		windBuffer.removeIf(tw -> now - tw.ts > recentWindowMillis);

		// distance filtering
		double d = distance(this.position, wind.getPosition());
		if (d > maxDistance) {
			System.out.println("WindTurbine[" + turbineId + "] ignore wind (too far d=" + d + "): " + wind);
			return;
		}

		windBuffer.add(new TimedWind(messageTs, wind));
		System.out.println("WindTurbine[" + turbineId + "] accept wind d=" + d + ": " + wind);

		// compute orientation vector
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
		System.out.println(
			"WindTurbine[" + turbineId + "] orientation vector = (" + orientX + ", " + orientY + ") safetyMode=" + safetyMode);
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
			System.out.println("WindTurbine[" + turbineId + "] ignore alert (not concerned): " + alert);
			return;
		}

		MeteoAlertI.Level level = (alert.getLevel() instanceof MeteoAlertI.Level) ? (MeteoAlertI.Level) alert.getLevel() : null;

		System.out.println("WindTurbine[" + turbineId + "] received alert: " + alert);

		// GREEN means end of alert
		if (level == MeteoAlertI.Level.GREEN) {
			safetyMode = false;
			System.out.println("WindTurbine[" + turbineId + "] RETURN NORMAL (GREEN)");
			return;
		}

		if (level != null && level.ordinal() >= threshold.ordinal()) {
			safetyMode = true;
			System.out.println("WindTurbine[" + turbineId + "] ENTER SAFETY MODE (level=" + level + ", threshold=" + threshold + ")");
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
		// fallback: cannot compute
		return Double.POSITIVE_INFINITY;
	}
}
