package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AfterOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BeforeOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BetweenTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.ComparableValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertiesFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import org.junit.Test;

import java.io.Serializable;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Unit tests for the filter subsystem (CDC §3.2).
 *
 * What is being tested:
 * - Value filters:
 *   - EqualsValueFilter: strict equality
 *   - ComparableValueFilter: comparisons on Comparable values (e.g., GE, LE, BETWEEN)
 *   - AcceptAllValueFilter: always matches
 * - PropertyFilter: applies a ValueFilter on a MessageI.PropertyI (not on a whole Message)
 * - PropertiesFilter: applies a MultiValuesFilterI on a set of properties (cross-constraints)
 * - Time filters: accept-all and time interval inclusion
 * - MessageFilter: global match on a full message (properties + time)
 */
public class MessageFilterTest {

	private static void info(String s) {
		System.out.println("[MessageFilterTest] " + s);
	}

	@Test
	public void testEqualsValueFilter() {
		info("EqualsValueFilter matches only if value.equals(expected).");

		MessageFilterI.ValueFilterI f = new EqualsValueFilter("demo");
		assertTrue(f.match("demo"));
		assertFalse(f.match("other"));
		assertFalse(f.match(null));
	}

	@Test
	public void testComparableValueFilterNumbersAsComparable() {
		info("ComparableValueFilter compares Comparable values (we use Integers here).");

		MessageFilterI.ValueFilterI ge10 = ComparableValueFilter.greaterOrEqual(10);
		assertTrue(ge10.match(10));
		assertTrue(ge10.match(11));
		assertFalse(ge10.match(9));

		MessageFilterI.ValueFilterI le10 = ComparableValueFilter.lowerOrEqual(10);
		assertTrue(le10.match(10));
		assertTrue(le10.match(9));
		assertFalse(le10.match(11));

		MessageFilterI.ValueFilterI between = ComparableValueFilter.betweenInclusive(10, 20);
		assertTrue(between.match(10));
		assertTrue(between.match(15));
		assertTrue(between.match(20));
		assertFalse(between.match(9));
		assertFalse(between.match(21));
	}

	@Test
	public void testAcceptAllValueFilter() {
		info("AcceptAllValueFilter always matches (including null).");

		MessageFilterI.ValueFilterI f = new AcceptAllValueFilter();
		assertTrue(f.match("anything"));
		assertTrue(f.match(null));
	}

	@Test
	public void testPropertyFilterPositiveAndNegative() {
		info("PropertyFilter.match applies to a single PropertyI.");

		MessageI.PropertyI p1 = new Message.Property("type", (Serializable) "demo");
		MessageI.PropertyI p2 = new Message.Property("type", (Serializable) "other");
		MessageI.PropertyI p3 = new Message.Property("otherName", (Serializable) "demo");

		MessageFilterI.PropertyFilterI pf =
			new PropertyFilter("type", new EqualsValueFilter("demo"));

		assertTrue(pf.match(p1));
		assertFalse(pf.match(p2));
		assertFalse(pf.match(p3));
	}

	@Test
	public void testPropertiesFilterCrossConstraintWithCustomMultiValuesFilter() {
		info("PropertiesFilter applies a MultiValuesFilterI over several properties (cross-constraint).");

		// Cross-constraint example: require (type == wind) AND (stationId starts with WS)
		MessageFilterI.MultiValuesFilterI mv = new fr.sorbonne_u.cps.pubsub.messages.filters.MultiValuesFilter("type", "stationId") {
			@Override
			protected boolean matchValues(Serializable... values) {
				String type = (String) values[0];
				String stationId = (String) values[1];
				return "wind".equals(type) && stationId != null && stationId.startsWith("WS");
			}
		};

		MessageFilterI.PropertiesFilterI pf = new PropertiesFilter(mv);

		MessageI.PropertyI typeOk = new Message.Property("type", (Serializable) "wind");
		MessageI.PropertyI stationOk = new Message.Property("stationId", (Serializable) "WS1");
		MessageI.PropertyI stationKo = new Message.Property("stationId", (Serializable) "XX");

		assertTrue(pf.match(typeOk, stationOk));
		assertFalse(pf.match(typeOk, stationKo));
	}

	@Test
	public void testTimeFilters() {
		info("Time filters check inclusion in a time interval.");

		Instant now = Instant.now();
		Instant before = now.minusSeconds(10);
		Instant after = now.plusSeconds(10);

		MessageFilterI.TimeFilterI acceptAll = new AcceptAllTimeFilter();
		assertTrue(acceptAll.match(now));

		MessageFilterI.TimeFilterI between = new BetweenTimeFilter(before, after);
		assertTrue(between.match(now));
		assertFalse(between.match(before.minusSeconds(1)));
		assertFalse(between.match(after.plusSeconds(1)));

		MessageFilterI.TimeFilterI afterOrAt = new AfterOrAtTimeFilter(now);
		assertTrue(afterOrAt.match(now));
		assertTrue(afterOrAt.match(after));
		assertFalse(afterOrAt.match(before));

		MessageFilterI.TimeFilterI beforeOrAt = new BeforeOrAtTimeFilter(now);
		assertTrue(beforeOrAt.match(now));
		assertTrue(beforeOrAt.match(before));
		assertFalse(beforeOrAt.match(after));
	}

	@Test
	public void testMessageFilterGlobalMatch() throws Exception {
		info("MessageFilter global match on a full Message: propertyFilters + time filter.");

		Message m = new Message("hello");
		m.putProperty("type", "demo");

		MessageFilterI.PropertyFilterI pf =
			new PropertyFilter("type", new EqualsValueFilter("demo"));

		MessageFilterI filter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] { pf },
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		assertTrue(filter.match(m));

		// Negative case: different property value
		Message m2 = new Message("hello");
		m2.putProperty("type", "other");
		assertFalse(filter.match(m2));
	}
}
