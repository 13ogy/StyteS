package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.exceptions.UnknownPropertyException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import org.junit.Test;

import java.io.Serializable;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link fr.sorbonne_u.cps.pubsub.messages.Message}.
 *
 * What is being tested:
 * - message carries a payload and a non-null timestamp
 * - properties obey the CDC contract: unique names, existence check, value retrieval
 * - removing properties works and missing properties raise {@link UnknownPropertyException}
 * - copy() preserves timestamp and properties, while allowing payload updates on the copy
 */
public class MessageTest {

	private static void info(String s) {
		System.out.println("[MessageTest] " + s);
	}

	@Test
	public void testPayloadAndTimestampNotNull() {
		info("payload and timestamp are set at construction time.");

		Serializable payload = "hello";
		Message m = new Message(payload);

		assertEquals(payload, m.getPayload());
		assertNotNull(m.getTimeStamp());
		// sanity: timestamp should be <= now
		assertTrue(!m.getTimeStamp().isAfter(Instant.now()));
	}

	@Test
	public void testPutPropertyThenExistsAndGetPropertyValue() throws Exception {
		info("putProperty + propertyExists + getPropertyValue.");

		Message m = new Message("p");
		m.putProperty("type", "wind");

		assertTrue(m.propertyExists("type"));
		assertEquals("wind", m.getPropertyValue("type"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPutPropertyDuplicateNameThrows() {
		info("putProperty must not accept duplicate property names (CDC precondition).");

		Message m = new Message("p");
		m.putProperty("type", "wind");
		m.putProperty("type", "other"); // must throw
	}

	@Test
	public void testRemovePropertyThenDoesNotExist() throws Exception {
		info("removeProperty removes an existing property.");

		Message m = new Message("p");
		m.putProperty("type", "wind");
		assertTrue(m.propertyExists("type"));

		m.removeProperty("type");
		assertFalse(m.propertyExists("type"));
	}

	@Test(expected = UnknownPropertyException.class)
	public void testRemoveMissingPropertyThrows() throws Exception {
		info("removeProperty on missing property must throw UnknownPropertyException.");
		Message m = new Message("p");
		m.removeProperty("missing");
	}

	@Test(expected = UnknownPropertyException.class)
	public void testGetPropertyValueMissingThrows() throws Exception {
		info("getPropertyValue on missing property must throw UnknownPropertyException.");
		Message m = new Message("p");
		m.getPropertyValue("missing");
	}

	@Test
	public void testGetPropertiesReturnsDefensiveCopy() {
		info("getProperties must return an array copy (defensive copy).");

		Message m = new Message("p");
		m.putProperty("a", "1");
		m.putProperty("b", "2");

		MessageI.PropertyI[] props1 = m.getProperties();
		assertEquals(2, props1.length);

		// Mutate the returned array (should not impact the message)
		props1[0] = null;

		MessageI.PropertyI[] props2 = m.getProperties();
		assertEquals(2, props2.length);
		assertNotNull(props2[0]);
		assertNotNull(props2[1]);
	}

	@Test
	public void testCopyPreservesTimestampAndProperties() throws Exception {
		info("copy() preserves timestamp and properties, and keeps same payload reference.");

		Message m = new Message((Serializable) "payload");
		m.putProperty("type", "demo");

		MessageI c = m.copy();

		assertEquals(m.getTimeStamp(), c.getTimeStamp());
		assertEquals(m.getPayload(), c.getPayload());
		assertEquals("demo", c.getPropertyValue("type"));

		// Modifying the copy payload should not modify the original message payload field.
		c.setPayload("other");
		assertEquals("payload", m.getPayload());
		assertEquals("other", c.getPayload());
	}
}
