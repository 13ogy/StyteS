package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.messages.AbstractGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.CreateChannelGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.DestroyChannelGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.ModifyAuthorisedUsersGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.ModifyServiceClassGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.PublishGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.RegisterGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.UnregisterGossipMessage;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the gossip message hierarchy
 * ({@link AbstractGossipMessage} and its concrete subclasses).
 *
 * <p>These tests validate the three contracts every gossip message must
 * satisfy, regardless of payload :</p>
 * <ol>
 * <li>Constructor preserves every payload field unchanged
 * (no silent munging).</li>
 * <li>{@link GossipMessageI#copyWithNewEmitterURI(String)} returns an
 * instance of the <em>same concrete subclass</em>, with the new
 * emitter URI, and the original {@code gossipMessageURI} +
 * {@code timestamp} preserved (key invariants for skip-echo +
 * atomic dedup, cf. {@code docs/GOSSIP.md} §3 §4).</li>
 * <li>Each concrete subclass implements
 * {@link EmitterAwareGossipMessageI}, inherited transitively via
 * {@link AbstractGossipMessage}, so the broker can rely on it
 * defensively (no {@code instanceof} chain).</li>
 * </ol>
 *
 * <p>The {@code gossipMessageURI} of every distinct in-flight message is
 * also asserted unique within the test suite — this is the property the
 * broker's atomic dedup depends on.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class GossipMessageTest
{
	private static final String INITIAL_EMITTER = "broker-A";
	private static final String NEW_EMITTER = "broker-B";

	private static String fresh(String tag)
	{
		return "gossip-" + tag + "-" + java.util.UUID.randomUUID();
	}

	// ------------------------------------------------------------------
	// 1. Per-subtype constructor + getter round-trip + copyWithNewEmitterURI
	// ------------------------------------------------------------------

	@Test
	public void registerGossip_constructorAndCopy()
	{
		final String uri = fresh("reg");
		final Instant ts = Instant.now();
		final RegisterGossipMessage m = new RegisterGossipMessage(
				uri, ts, INITIAL_EMITTER, "client-1", RegistrationClass.STANDARD);

		assertEquals("gossipMessageURI preserved", uri, m.gossipMessageURI());
		assertEquals("timestamp preserved", ts, m.timestamp());
		assertEquals("emitterURI preserved", INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("clientReceptionPortURI", "client-1", m.getClientReceptionPortURI());
		assertEquals("registrationClass", RegistrationClass.STANDARD, m.getRegistrationClass());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertEquals("subtype preserved", RegisterGossipMessage.class, copy.getClass());
		final RegisterGossipMessage cm = (RegisterGossipMessage) copy;
		assertEquals("gossipMessageURI preserved across copy", uri, cm.gossipMessageURI());
		assertEquals("timestamp preserved across copy", ts, cm.timestamp());
		assertEquals("emitter is the new emitter", NEW_EMITTER, cm.getEmitterURI());
		assertEquals("payload field 1 preserved", "client-1", cm.getClientReceptionPortURI());
		assertEquals("payload field 2 preserved", RegistrationClass.STANDARD, cm.getRegistrationClass());
	}

	@Test
	public void unregisterGossip_constructorAndCopy()
	{
		final String uri = fresh("unreg");
		final Instant ts = Instant.now();
		final UnregisterGossipMessage m = new UnregisterGossipMessage(
				uri, ts, INITIAL_EMITTER, "client-1");

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("client-1", m.getClientReceptionPortURI());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertEquals(UnregisterGossipMessage.class, copy.getClass());
		final UnregisterGossipMessage cm = (UnregisterGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("client-1", cm.getClientReceptionPortURI());
	}

	@Test
	public void publishGossip_constructorAndCopy()
	{
		final String uri = fresh("pub");
		final Instant ts = Instant.now();
		final MessageI payload = new Message("hello");
		final PublishGossipMessage m = new PublishGossipMessage(
				uri, ts, INITIAL_EMITTER, payload, "channel0", "publisher-port");

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("channel0", m.getChannel());
		assertEquals("publisher-port", m.getPublisherReceptionPortURI());
		assertSame("payload reference preserved", payload, m.getPubMessage());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertEquals(PublishGossipMessage.class, copy.getClass());
		final PublishGossipMessage cm = (PublishGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("channel0", cm.getChannel());
		assertSame(payload, cm.getPubMessage());
	}

	@Test
	public void createChannelGossip_constructorAndCopy()
	{
		final String uri = fresh("cc");
		final Instant ts = Instant.now();
		final CreateChannelGossipMessage m = new CreateChannelGossipMessage(
				uri, ts, INITIAL_EMITTER, "priv-channel-1",
				"owner-port", "^trusted-.*$", RegistrationClass.PREMIUM);

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("priv-channel-1", m.getChannel());
		assertEquals("owner-port", m.getOwnerReceptionPortURI());
		assertEquals("^trusted-.*$", m.getAuthorisedUsers());
		assertEquals(RegistrationClass.PREMIUM, m.getOwnerClass());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertEquals(CreateChannelGossipMessage.class, copy.getClass());
		final CreateChannelGossipMessage cm = (CreateChannelGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("priv-channel-1", cm.getChannel());
		assertEquals("owner-port", cm.getOwnerReceptionPortURI());
		assertEquals("^trusted-.*$", cm.getAuthorisedUsers());
		assertEquals(RegistrationClass.PREMIUM, cm.getOwnerClass());
	}

	@Test
	public void destroyChannelGossip_constructorAndCopy()
	{
		final String uri = fresh("dc");
		final Instant ts = Instant.now();
		final DestroyChannelGossipMessage m = new DestroyChannelGossipMessage(
				uri, ts, INITIAL_EMITTER, "priv-channel-1", "owner-port");

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("priv-channel-1", m.getChannel());
		assertEquals("owner-port", m.getOwnerReceptionPortURI());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertEquals(DestroyChannelGossipMessage.class, copy.getClass());
		final DestroyChannelGossipMessage cm = (DestroyChannelGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("priv-channel-1", cm.getChannel());
		assertEquals("owner-port", cm.getOwnerReceptionPortURI());
	}

	@Test
	public void modifyServiceClassGossip_constructorAndCopy()
	{
		final String uri = fresh("msc");
		final Instant ts = Instant.now();
		final ModifyServiceClassGossipMessage m = new ModifyServiceClassGossipMessage(
				uri, ts, INITIAL_EMITTER, "client-1", RegistrationClass.PREMIUM);

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("client-1", m.getClientReceptionPortURI());
		assertEquals(RegistrationClass.PREMIUM, m.getNewRegistrationClass());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		// Regression: copyWithNewEmitterURI must return the SAME subtype.
		// Pre-merge fix `e1efca9` documented a bug where this returned the
		// wrong subtype: assertEquals locks that fix into the test suite.
		assertEquals(ModifyServiceClassGossipMessage.class, copy.getClass());
		final ModifyServiceClassGossipMessage cm = (ModifyServiceClassGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("client-1", cm.getClientReceptionPortURI());
		assertEquals(RegistrationClass.PREMIUM, cm.getNewRegistrationClass());
	}

	@Test
	public void modifyAuthorisedUsersGossip_constructorAndCopy()
	{
		final String uri = fresh("mau");
		final Instant ts = Instant.now();
		final ModifyAuthorisedUsersGossipMessage m = new ModifyAuthorisedUsersGossipMessage(
				uri, ts, INITIAL_EMITTER, "priv-channel-1",
				"owner-port", "^new-trusted-.*$", RegistrationClass.STANDARD);

		assertEquals(uri, m.gossipMessageURI());
		assertEquals(INITIAL_EMITTER, m.getEmitterURI());
		assertEquals("priv-channel-1", m.getChannel());
		assertEquals("owner-port", m.getOwnerReceptionPortURI());
		assertEquals("^new-trusted-.*$", m.getAuthorisedUsers());

		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		// Regression: see e1efca9 — earlier copies returned the wrong subtype.
		assertEquals(ModifyAuthorisedUsersGossipMessage.class, copy.getClass());
		final ModifyAuthorisedUsersGossipMessage cm = (ModifyAuthorisedUsersGossipMessage) copy;
		assertEquals(uri, cm.gossipMessageURI());
		assertEquals(NEW_EMITTER, cm.getEmitterURI());
		assertEquals("priv-channel-1", cm.getChannel());
		assertEquals("^new-trusted-.*$", cm.getAuthorisedUsers());
	}

	// ------------------------------------------------------------------
	// 2. Type contracts shared by every concrete subclass
	// ------------------------------------------------------------------

	@Test
	public void everyConcreteMessage_isAbstractGossipMessage_andEmitterAware()
	{
		final Instant ts = Instant.now();
		final GossipMessageI[] all = new GossipMessageI[] {
				new RegisterGossipMessage(fresh("a"), ts, INITIAL_EMITTER, "c", RegistrationClass.FREE),
				new UnregisterGossipMessage(fresh("b"), ts, INITIAL_EMITTER, "c"),
				new PublishGossipMessage(fresh("c"), ts, INITIAL_EMITTER, new Message("p"), "ch", "pub"),
				new CreateChannelGossipMessage(fresh("d"), ts, INITIAL_EMITTER, "ch", "owner", "^.*$", RegistrationClass.STANDARD),
				new DestroyChannelGossipMessage(fresh("e"), ts, INITIAL_EMITTER, "ch", "owner"),
				new ModifyServiceClassGossipMessage(fresh("f"), ts, INITIAL_EMITTER, "c", RegistrationClass.PREMIUM),
				new ModifyAuthorisedUsersGossipMessage(fresh("g"), ts, INITIAL_EMITTER, "ch", "owner", "^.*$", RegistrationClass.STANDARD),
		};

		for (GossipMessageI m : all) {
			assertTrue("must be an AbstractGossipMessage: " + m.getClass(),
					m instanceof AbstractGossipMessage);
			assertTrue("must be an EmitterAwareGossipMessageI: " + m.getClass(),
					m instanceof EmitterAwareGossipMessageI);
			assertEquals("getEmitterURI matches via the EmitterAware sub-interface",
					INITIAL_EMITTER,
					((EmitterAwareGossipMessageI) m).getEmitterURI());
			assertNotNull("gossipMessageURI must be non-null", m.gossipMessageURI());
			assertNotNull("timestamp must be non-null", m.timestamp());
		}
	}

	@Test
	public void gossipMessageURIs_areUniquePerInstance()
	{
		final Instant ts = Instant.now();
		final Set<String> uris = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			RegisterGossipMessage m = new RegisterGossipMessage(
					fresh("uniq" + i), ts, INITIAL_EMITTER, "client-" + i, RegistrationClass.FREE);
			assertTrue("gossipMessageURI must be unique across instances: " + m.gossipMessageURI(),
					uris.add(m.gossipMessageURI()));
		}
		assertEquals(100, uris.size());
	}

	@Test
	public void copyWithNewEmitterURI_returnsDifferentInstance()
	{
		final RegisterGossipMessage m = new RegisterGossipMessage(
				fresh("inst"), Instant.now(), INITIAL_EMITTER, "c", RegistrationClass.FREE);
		final GossipMessageI copy = m.copyWithNewEmitterURI(NEW_EMITTER);
		assertNotEquals("copy must be a fresh object (skip-echo correctness)",
				System.identityHashCode(m), System.identityHashCode(copy));
		assertNotEquals("emitterURI was rewritten",
				m.getEmitterURI(), ((EmitterAwareGossipMessageI) copy).getEmitterURI());
	}

	@Test
	public void copyWithNewEmitterURI_acceptsNullEmitter()
	{
		// Defensive : the broker constructs the new emitter URI from
		// getReflectionInboundPortURI(), which is non-null by BCM contract,
		// but the message implementation should not blow up on null.
		final RegisterGossipMessage m = new RegisterGossipMessage(
				fresh("null"), Instant.now(), INITIAL_EMITTER, "c", RegistrationClass.FREE);
		final GossipMessageI copy = m.copyWithNewEmitterURI(null);
		assertNull("copy with null emitter is null",
				((EmitterAwareGossipMessageI) copy).getEmitterURI());
	}
}
