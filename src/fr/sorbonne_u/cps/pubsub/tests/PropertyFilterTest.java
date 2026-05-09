package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PropertyFilter} (CDC §3.2).
 *
 * <p>
 * What is being tested:
 * </p>
 * <ul>
 *   <li>match returns false when property argument is null.</li>
 *   <li>match returns false when the property name does not equal the filter name.</li>
 *   <li>match returns true when name matches AND inner valueFilter accepts the value.</li>
 *   <li>match returns false when name matches but inner valueFilter rejects the value.</li>
 *   <li>Constructor rejects a null name with {@link IllegalArgumentException}.</li>
 *   <li>Constructor rejects an empty name with {@link IllegalArgumentException}.</li>
 *   <li>Constructor rejects a null valueFilter with {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PropertyFilterTest
{
	private static void info(String s)
	{
		System.out.println("[PropertyFilterTest] " + s);
	}

	// -------------------------------------------------------------------------
	// match() behaviour
	// -------------------------------------------------------------------------

	@Test
	public void testMatchReturnsFalseOnNullProperty()
	{
		info("match(null) must return false regardless of value filter.");

		PropertyFilterI pf = new PropertyFilter("type", new AcceptAllValueFilter());
		assertFalse(pf.match(null));
	}

	@Test
	public void testMatchReturnsFalseWhenNameDoesNotMatch()
	{
		info("match returns false when property name differs from filter name.");

		PropertyI p = new Message.Property("speed", (Serializable) "fast");
		PropertyFilterI pf = new PropertyFilter("type", new AcceptAllValueFilter());
		assertFalse(pf.match(p));
	}

	@Test
	public void testMatchReturnsTrueWhenNameMatchesAndValueFilterAccepts()
	{
		info("match returns true when name matches AND valueFilter accepts the value.");

		PropertyI p = new Message.Property("type", (Serializable) "wind");
		PropertyFilterI pf = new PropertyFilter("type", new EqualsValueFilter("wind"));
		assertTrue(pf.match(p));
	}

	@Test
	public void testMatchReturnsFalseWhenNameMatchesButValueFilterRejects()
	{
		info("match returns false when name matches but valueFilter rejects the value.");

		PropertyI p = new Message.Property("type", (Serializable) "rain");
		PropertyFilterI pf = new PropertyFilter("type", new EqualsValueFilter("wind"));
		assertFalse(pf.match(p));
	}

	@Test
	public void testMatchAcceptAllValueFilterAlwaysAcceptsOnNameMatch()
	{
		info("AcceptAllValueFilter + name match → always true.");

		PropertyFilterI pf = new PropertyFilter("speed", new AcceptAllValueFilter());
		PropertyI p1 = new Message.Property("speed", (Serializable) "fast");
		PropertyI p2 = new Message.Property("speed", (Serializable) null);
		assertTrue(pf.match(p1));
		assertTrue(pf.match(p2));
	}

	// -------------------------------------------------------------------------
	// getters
	// -------------------------------------------------------------------------

	@Test
	public void testGetters()
	{
		info("getName() and getValueFilter() return the values passed to constructor.");

		ValueFilterI vf = new EqualsValueFilter("demo");
		PropertyFilter pf = new PropertyFilter("myProp", vf);
		assertEquals("myProp", pf.getName());
		assertSame(vf, pf.getValueFilter());
	}

	// -------------------------------------------------------------------------
	// Constructor validation
	// -------------------------------------------------------------------------

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorRejectsNullName()
	{
		info("Constructor must reject null name with IllegalArgumentException.");
		new PropertyFilter(null, new AcceptAllValueFilter());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorRejectsEmptyName()
	{
		info("Constructor must reject empty name with IllegalArgumentException.");
		new PropertyFilter("", new AcceptAllValueFilter());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorRejectsNullValueFilter()
	{
		info("Constructor must reject null valueFilter with IllegalArgumentException.");
		new PropertyFilter("type", null);
	}
}
