package fr.sorbonne_u.cps.pubsub.tests;

import static org.junit.Assert.*;

import fr.sorbonne_u.cps.pubsub.application.meteo.MeteoAlertMessageFactory;
import fr.sorbonne_u.cps.pubsub.application.meteo.MeteoFilters;
import fr.sorbonne_u.cps.pubsub.application.meteo.MeteoProperties;
import fr.sorbonne_u.cps.pubsub.application.meteo.WindMessageFactory;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.CircularRegion;
import fr.sorbonne_u.cps.pubsub.meteo.impl.MeteoAlert;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;
import fr.sorbonne_u.cps.pubsub.meteo.impl.WindData;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

/**
 * Unit tests for the factory classes and filter helpers: {@link WindMessageFactory}, {@link
 * MeteoAlertMessageFactory}, {@link MeteoFilters}, and {@link MeteoProperties} (CDC §3.4).
 *
 * <p>What is being tested:
 *
 * <ul>
 *   <li>{@code WindMessageFactory.build} sets the expected property keys and values.
 *   <li>{@code MeteoAlertMessageFactory.build} sets the expected property keys and values.
 *   <li>{@code MeteoFilters.windWithinDistance} matches a wind message inside the radius and
 *       rejects one beyond it.
 *   <li>{@code MeteoFilters.anyAlert} accepts any alert message and rejects wind messages.
 *   <li>{@code MeteoFilters.anyWind} accepts any wind message and rejects alert messages.
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class MeteoMessageFactoryTest {
	private static void info(String s) {
		System.out.println("[MeteoMessageFactoryTest] " + s);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static WindDataI makeWind(double x, double y, double px, double py) {
		return new WindData(new Position2D(px, py), x, y);
	}

	private static MeteoAlertI makeAlert() {
		return new MeteoAlert(
				MeteoAlertI.AlertType.STORM,
				MeteoAlertI.Level.ORANGE,
				new fr.sorbonne_u.cps.pubsub.meteo.RegionI[] {
					new CircularRegion(new Position2D(0, 0), 100)
				},
				Instant.now(),
				Duration.ofHours(2));
	}

	// -------------------------------------------------------------------------
	// WindMessageFactory
	// -------------------------------------------------------------------------

	/** {@link WindMessageFactory#build} positionne TYPE, STATION_ID, FORCE, X, Y, PAYLOAD. */
	@Test
	public void testWindMessageFactoryProperties() throws Exception {
		info("WindMessageFactory.build sets TYPE, STATION_ID, FORCE, X, Y, PAYLOAD.");

		WindDataI wind = makeWind(3.0, 4.0, 10.0, 20.0);
		MessageI m = WindMessageFactory.build("WS-1", wind);

		assertNotNull(m);
		// TYPE property
		assertTrue(m.propertyExists(MeteoProperties.TYPE));
		assertEquals(MeteoProperties.TYPE_WIND, m.getPropertyValue(MeteoProperties.TYPE));
		// STATION_ID
		assertTrue(m.propertyExists(MeteoProperties.STATION_ID));
		assertEquals("WS-1", m.getPropertyValue(MeteoProperties.STATION_ID));
		// FORCE
		assertTrue(m.propertyExists(MeteoProperties.FORCE));
		assertEquals(Double.toString(wind.force()), m.getPropertyValue(MeteoProperties.FORCE));
		// X component
		assertTrue(m.propertyExists(MeteoProperties.X));
		assertEquals(Double.toString(3.0), m.getPropertyValue(MeteoProperties.X));
		// Y component
		assertTrue(m.propertyExists(MeteoProperties.Y));
		assertEquals(Double.toString(4.0), m.getPropertyValue(MeteoProperties.Y));
		// PAYLOAD
		assertTrue(m.propertyExists(MeteoProperties.PAYLOAD));
		assertSame(wind, m.getPropertyValue(MeteoProperties.PAYLOAD));
	}

	/** {@link WindMessageFactory#build} stocke le {@link WindDataI} comme payload du message. */
	@Test
	public void testWindMessageFactoryPayload() {
		info("WindMessageFactory.build stores the WindDataI as the message payload.");

		WindDataI wind = makeWind(1.0, 0.0, 0.0, 0.0);
		MessageI m = WindMessageFactory.build("WS-2", wind);

		assertSame(wind, m.getPayload());
	}

	/** {@link WindMessageFactory#build} rejette un {@code stationId} null. */
	@Test(expected = IllegalArgumentException.class)
	public void testWindMessageFactoryNullStationIdThrows() {
		info("WindMessageFactory.build rejects null stationId.");
		WindMessageFactory.build(null, makeWind(1.0, 0.0, 0.0, 0.0));
	}

	/** {@link WindMessageFactory#build} rejette un {@code wind} null. */
	@Test(expected = IllegalArgumentException.class)
	public void testWindMessageFactoryNullWindThrows() {
		info("WindMessageFactory.build rejects null wind.");
		WindMessageFactory.build("WS-3", null);
	}

	// -------------------------------------------------------------------------
	// MeteoAlertMessageFactory
	// -------------------------------------------------------------------------

	/**
	 * {@link MeteoAlertMessageFactory#build} positionne TYPE, OFFICE_ID, LEVEL, ALERT_TYPE,
	 * PAYLOAD.
	 */
	@Test
	public void testAlertMessageFactoryProperties() throws Exception {
		info("MeteoAlertMessageFactory.build sets TYPE, OFFICE_ID, LEVEL, ALERT_TYPE, PAYLOAD.");

		MeteoAlertI alert = makeAlert();
		MessageI m = MeteoAlertMessageFactory.build("WO-1", alert);

		assertNotNull(m);
		// TYPE
		assertTrue(m.propertyExists(MeteoProperties.TYPE));
		assertEquals(MeteoProperties.TYPE_ALERT, m.getPropertyValue(MeteoProperties.TYPE));
		// OFFICE_ID
		assertTrue(m.propertyExists(MeteoProperties.OFFICE_ID));
		assertEquals("WO-1", m.getPropertyValue(MeteoProperties.OFFICE_ID));
		// LEVEL
		assertTrue(m.propertyExists(MeteoProperties.LEVEL));
		assertEquals(
				MeteoAlertI.Level.ORANGE.toString(), m.getPropertyValue(MeteoProperties.LEVEL));
		// ALERT_TYPE
		assertTrue(m.propertyExists(MeteoProperties.ALERT_TYPE));
		assertEquals(
				MeteoAlertI.AlertType.STORM.toString(),
				m.getPropertyValue(MeteoProperties.ALERT_TYPE));
		// PAYLOAD
		assertTrue(m.propertyExists(MeteoProperties.PAYLOAD));
		assertSame(alert, m.getPropertyValue(MeteoProperties.PAYLOAD));
	}

	/** {@link MeteoAlertMessageFactory#build} rejette un {@code officeId} null. */
	@Test(expected = IllegalArgumentException.class)
	public void testAlertMessageFactoryNullOfficeIdThrows() {
		info("MeteoAlertMessageFactory.build rejects null officeId.");
		MeteoAlertMessageFactory.build(null, makeAlert());
	}

	/** {@link MeteoAlertMessageFactory#build} rejette un {@code alert} null. */
	@Test(expected = IllegalArgumentException.class)
	public void testAlertMessageFactoryNullAlertThrows() {
		info("MeteoAlertMessageFactory.build rejects null alert.");
		MeteoAlertMessageFactory.build("WO-2", null);
	}

	// -------------------------------------------------------------------------
	// MeteoFilters.windWithinDistance
	// -------------------------------------------------------------------------

	/** {@link MeteoFilters#windWithinDistance} accepte un message vent dans le rayon donné. */
	@Test
	public void testWindWithinDistanceMatchesInsideRadius() {
		info("MeteoFilters.windWithinDistance: accepts wind message inside the radius.");

		Position2D ref = new Position2D(0.0, 0.0);
		double maxDist = 10.0;
		MessageFilterI filter = MeteoFilters.windWithinDistance(ref, maxDist);

		// Wind at distance 5 — inside
		WindDataI windInside = makeWind(3.0, 4.0, 0.0, 0.0); // position at (0,0), force=5
		MessageI msg = WindMessageFactory.build("WS-A", windInside);
		assertTrue(filter.match(msg));
	}

	/** {@link MeteoFilters#windWithinDistance} rejette un message vent au-delà du rayon. */
	@Test
	public void testWindWithinDistanceRejectsBeyondRadius() {
		info("MeteoFilters.windWithinDistance: rejects wind message beyond the radius.");

		Position2D ref = new Position2D(0.0, 0.0);
		double maxDist = 10.0;
		MessageFilterI filter = MeteoFilters.windWithinDistance(ref, maxDist);

		// Wind at position (20, 0) — distance = 20 > 10
		WindDataI windOutside = makeWind(1.0, 0.0, 20.0, 0.0);
		MessageI msg = WindMessageFactory.build("WS-B", windOutside);
		assertFalse(filter.match(msg));
	}

	/** {@link MeteoFilters#windWithinDistance} rejette un message d'alerte (mauvais type). */
	@Test
	public void testWindWithinDistanceRejectsAlertMessage() {
		info("MeteoFilters.windWithinDistance: rejects alert messages (wrong type).");

		Position2D ref = new Position2D(0.0, 0.0);
		MessageFilterI filter = MeteoFilters.windWithinDistance(ref, 100.0);
		MessageI alertMsg = MeteoAlertMessageFactory.build("WO-X", makeAlert());
		assertFalse(filter.match(alertMsg));
	}

	// -------------------------------------------------------------------------
	// MeteoFilters.anyAlert
	// -------------------------------------------------------------------------

	/** {@link MeteoFilters#anyAlert} accepte tout message d'alerte. */
	@Test
	public void testAnyAlertMatchesAlertMessage() {
		info("MeteoFilters.anyAlert: accepts any alert message.");

		MessageFilterI filter = MeteoFilters.anyAlert();
		MessageI alertMsg = MeteoAlertMessageFactory.build("WO-1", makeAlert());
		assertTrue(filter.match(alertMsg));
	}

	/** {@link MeteoFilters#anyAlert} rejette les messages vent. */
	@Test
	public void testAnyAlertRejectsWindMessage() {
		info("MeteoFilters.anyAlert: rejects wind messages.");

		MessageFilterI filter = MeteoFilters.anyAlert();
		MessageI windMsg = WindMessageFactory.build("WS-1", makeWind(1.0, 0.0, 0.0, 0.0));
		assertFalse(filter.match(windMsg));
	}

	/** {@link MeteoFilters#anyAlert} accepte les alertes de tous les {@code AlertType}. */
	@Test
	public void testAnyAlertMatchesDifferentAlertTypes() {
		info("MeteoFilters.anyAlert: accepts alerts of any AlertType.");

		MessageFilterI filter = MeteoFilters.anyAlert();

		MeteoAlertI storm =
				new MeteoAlert(
						MeteoAlertI.AlertType.STORM,
						MeteoAlertI.Level.RED,
						new fr.sorbonne_u.cps.pubsub.meteo.RegionI[] {
							new CircularRegion(new Position2D(0, 0), 50)
						},
						Instant.now(),
						Duration.ofHours(1));

		MeteoAlertI flood =
				new MeteoAlert(
						MeteoAlertI.AlertType.FLOODING,
						MeteoAlertI.Level.GREEN,
						new fr.sorbonne_u.cps.pubsub.meteo.RegionI[] {
							new CircularRegion(new Position2D(0, 0), 50)
						},
						Instant.now(),
						Duration.ofHours(1));

		assertTrue(filter.match(MeteoAlertMessageFactory.build("WO-A", storm)));
		assertTrue(filter.match(MeteoAlertMessageFactory.build("WO-B", flood)));
	}

	// -------------------------------------------------------------------------
	// MeteoFilters.anyWind
	// -------------------------------------------------------------------------

	/** {@link MeteoFilters#anyWind} accepte tout message vent. */
	@Test
	public void testAnyWindMatchesWindMessage() {
		info("MeteoFilters.anyWind: accepts any wind message.");

		MessageFilterI filter = MeteoFilters.anyWind();
		MessageI windMsg = WindMessageFactory.build("WS-1", makeWind(1.0, 0.0, 0.0, 0.0));
		assertTrue(filter.match(windMsg));
	}

	/** {@link MeteoFilters#anyWind} rejette les messages d'alerte. */
	@Test
	public void testAnyWindRejectsAlertMessage() {
		info("MeteoFilters.anyWind: rejects alert messages.");

		MessageFilterI filter = MeteoFilters.anyWind();
		MessageI alertMsg = MeteoAlertMessageFactory.build("WO-1", makeAlert());
		assertFalse(filter.match(alertMsg));
	}
}
