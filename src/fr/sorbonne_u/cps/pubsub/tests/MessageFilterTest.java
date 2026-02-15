package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import org.junit.Test;

import static org.junit.Assert.*;

public class MessageFilterTest {

	@Test
	public void testEqualsValueFilter() {
		MessageFilterI.ValueFilterI f = new EqualsValueFilter("demo");
		assertTrue(f.match("demo"));
		assertFalse(f.match("other"));
	}

	@Test
	public void testAcceptAllValueFilter() {
		MessageFilterI.ValueFilterI f = new AcceptAllValueFilter();
		assertTrue(f.match("anything"));
		assertTrue(f.match(null));
	}

	@Test
	public void testPropertyFilterAndMessageFilterMatch() throws Exception {
		Message m = new Message("hello");
		m.putProperty("type", "demo");

		MessageFilterI.PropertyFilterI pf =
			new PropertyFilter("type", new EqualsValueFilter("demo"));

		MessageFilterI filter = new MessageFilter(
			new MessageFilterI.PropertyFilterI[] { pf },
			new MessageFilterI.PropertiesFilterI[0],
			new AcceptAllTimeFilter());

		assertTrue(filter.match(m));

		// Message.putProperty forbids overwriting existing names (CDC contract),
		// so create a new message for the negative case.
		Message m2 = new Message("hello");
		m2.putProperty("type", "other");
		assertFalse(filter.match(m2));
	}
}
