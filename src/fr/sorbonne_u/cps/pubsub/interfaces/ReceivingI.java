package fr.sorbonne_u.cps.pubsub.interfaces;

// -----------------------------------------------------------------------------
/**
 * The plain Java interface <code>ReceivingI</code> is the in-JVM counterpart
 * of the BCM component interface {@link ReceivingCI}.
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Unlike {@link ReceivingCI}, which is offered/required between components and
 * therefore declares {@code throws RemoteException} on every method, this
 * interface is intended for in-JVM composition: application-side code (e.g.
 * a {@code ClientSubscriptionPlugin} delivery handler) can implement it
 * without dragging RMI into the call chain.
 * </p>
 *
 * <p>
 * This is the same convention already used in the codebase to separate
 * {@link MessageI} / {@link MessageFilterI} (plain Java) from their
 * component-interface peers.
 * </p>
 *
 * <p><strong>Invariants</strong></p>
 *
 * <pre>
 * invariant	{@code true}	// no invariant
 * </pre>
 *
 * <p>Created on : 2026-05-09</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@FunctionalInterface
public interface ReceivingI
{
	/**
	 * Called when a message is delivered on a channel the client is
	 * subscribed to.
	 *
	 * <p><strong>Contract</strong></p>
	 *
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code message != null}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel	name of the channel from which {@code message} is received.
	 * @param message	delivered message.
	 */
	void onReceive(String channel, MessageI message);

	/**
	 * Called when a batch of messages is delivered on a channel the client is
	 * subscribed to. Default implementation iterates and calls
	 * {@link #onReceive(String, MessageI)} on each non-null message.
	 *
	 * <p><strong>Contract</strong></p>
	 *
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel	name of the channel from which {@code messages} are received.
	 * @param messages	delivered messages (may be {@code null}, in which case nothing happens).
	 */
	default void onReceive(String channel, MessageI[] messages)
	{
		if (messages != null) {
			for (MessageI m : messages) {
				if (m != null) {
					onReceive(channel, m);
				}
			}
		}
	}
}
// -----------------------------------------------------------------------------
