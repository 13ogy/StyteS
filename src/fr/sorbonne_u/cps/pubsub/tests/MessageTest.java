package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.messages.Message;
import org.junit.Test;

import java.io.Serializable;
import java.time.Instant;

import static org.junit.Assert.*;

public class MessageTest {

	@Test
	public void testPayloadAndTimestampNotNull() {
		Serializable payload = "hello";
		Message m = new Message(payload);

		assertEquals(payload, m.getPayload());
		assertNotNull(m.getTimeStamp());
		// sanity: timestamp should be <= now
		assertTrue(!m.getTimeStamp().isAfter(Instant.now()));
	}

	@Test
	public void testPutAndGetPropertyValue() throws Exception {
		Message m = new Message("p");

		m.putProperty("type", "wind");

		assertTrue(m.propertyExists("type"));
		assertEquals("wind", m.getPropertyValue("type"));
	}

	@Test(expected = fr.sorbonne_u.cps.pubsub.exceptions.UnknownPropertyException.class)
	public void testGetPropertyValueMissingThrows() throws Exception {
		Message m = new Message("p");
		m.getPropertyValue("missing");
	}
}
