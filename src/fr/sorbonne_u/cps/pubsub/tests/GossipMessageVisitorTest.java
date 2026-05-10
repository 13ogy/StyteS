package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.messages.AbstractGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.CreateChannelGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.DestroyChannelGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.ModifyAuthorisedUsersGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.ModifyServiceClassGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.PublishGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.RegisterGossipMessage;
import fr.sorbonne_u.cps.pubsub.gossip.messages.UnregisterGossipMessage;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for the {@link GossipMessageVisitor} double-dispatch
 * mechanism (Visitor design pattern adopted in commit {@code 17435bb}
 * to replace the {@code instanceof} chain in {@code Broker.update(...)}
 * — pour bénéficier du dispatch dynamique sans {@code instanceof}).
 *
 * <p>Each test posts one concrete gossip message to a recording visitor
 * and asserts that the dispatch resolves to the type-correct
 * {@code visit(...)} overload, with the exact same instance passed
 * through (no defensive copy).</p>
 *
 * <p>This is the property {@code Broker.update(...)} relies on every
 * time it does {@code ((AbstractGossipMessage) msg).accept(handler)}
 * — without it, mutations would dispatch to the wrong handler method
 * and the federation would silently corrupt local broker state.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class GossipMessageVisitorTest
{
	private RecordingVisitor visitor;
	private Instant ts;

	@Before
	public void setUp()
	{
		this.visitor = new RecordingVisitor();
		this.ts = Instant.now();
	}

	@Test
	public void register_dispatchesToRegisterVisit()
	{
		final RegisterGossipMessage m = new RegisterGossipMessage(
				"g-1", ts, "broker-A", "client-1", RegistrationClass.FREE);
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals("exactly one visit call", 1, visitor.totalCalls());
		assertEquals("dispatched to visit(RegisterGossipMessage)",
				"register", visitor.lastVisitKind);
		assertSame("same message instance forwarded",
				m, visitor.lastVisited);
	}

	@Test
	public void unregister_dispatchesToUnregisterVisit()
	{
		final UnregisterGossipMessage m = new UnregisterGossipMessage(
				"g-2", ts, "broker-A", "client-1");
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("unregister", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void publish_dispatchesToPublishVisit()
	{
		final PublishGossipMessage m = new PublishGossipMessage(
				"g-3", ts, "broker-A", new Message("hello"),
				"channel0", "publisher-port");
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("publish", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void createChannel_dispatchesToCreateChannelVisit()
	{
		final CreateChannelGossipMessage m = new CreateChannelGossipMessage(
				"g-4", ts, "broker-A", "priv-1", "owner",
				"^.*$", RegistrationClass.STANDARD);
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("createChannel", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void destroyChannel_dispatchesToDestroyChannelVisit()
	{
		final DestroyChannelGossipMessage m = new DestroyChannelGossipMessage(
				"g-5", ts, "broker-A", "priv-1", "owner");
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("destroyChannel", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void modifyServiceClass_dispatchesToModifyServiceClassVisit()
	{
		final ModifyServiceClassGossipMessage m = new ModifyServiceClassGossipMessage(
				"g-6", ts, "broker-A", "client-1", RegistrationClass.PREMIUM);
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("modifyServiceClass", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void modifyAuthorisedUsers_dispatchesToModifyAuthorisedUsersVisit()
	{
		final ModifyAuthorisedUsersGossipMessage m = new ModifyAuthorisedUsersGossipMessage(
				"g-7", ts, "broker-A", "priv-1", "owner",
				"^new-.*$", RegistrationClass.PREMIUM);
		((AbstractGossipMessage) m).accept(visitor);
		assertEquals(1, visitor.totalCalls());
		assertEquals("modifyAuthorisedUsers", visitor.lastVisitKind);
		assertSame(m, visitor.lastVisited);
	}

	@Test
	public void successiveVisits_dispatchIndependently()
	{
		final RegisterGossipMessage m1 = new RegisterGossipMessage(
				"g-A", ts, "broker-A", "client-1", RegistrationClass.FREE);
		final UnregisterGossipMessage m2 = new UnregisterGossipMessage(
				"g-B", ts, "broker-A", "client-1");

		((AbstractGossipMessage) m1).accept(visitor);
		assertEquals("register", visitor.lastVisitKind);
		((AbstractGossipMessage) m2).accept(visitor);
		assertEquals("unregister", visitor.lastVisitKind);

		assertEquals("two dispatches recorded", 2, visitor.totalCalls());
	}

	// ------------------------------------------------------------------
	// Recording visitor (test-only)
	// ------------------------------------------------------------------

	/**
	 * Visitor that records the kind of the last call + the argument
	 * instance — lets each test assert dispatch correctness without
	 * mocking-framework dependency.
	 */
	private static final class RecordingVisitor implements GossipMessageVisitor
	{
		String lastVisitKind = null;
		Object lastVisited = null;
		private int callCount = 0;

		int totalCalls() { return this.callCount; }

		@Override public void visit(RegisterGossipMessage m)
		{ this.lastVisitKind = "register"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(PublishGossipMessage m)
		{ this.lastVisitKind = "publish"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(CreateChannelGossipMessage m)
		{ this.lastVisitKind = "createChannel"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(DestroyChannelGossipMessage m)
		{ this.lastVisitKind = "destroyChannel"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(ModifyServiceClassGossipMessage m)
		{ this.lastVisitKind = "modifyServiceClass"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(ModifyAuthorisedUsersGossipMessage m)
		{ this.lastVisitKind = "modifyAuthorisedUsers"; this.lastVisited = m; this.callCount++; }
		@Override public void visit(UnregisterGossipMessage m)
		{ this.lastVisitKind = "unregister"; this.lastVisited = m; this.callCount++; }
	}
}
