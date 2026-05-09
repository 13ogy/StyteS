package fr.sorbonne_u.cps.pubsub.gossip.interfaces;

/**
 * Project-local extension of the frozen {@link GossipMessageI} that exposes
 * the immediate gossip emitter URI.
 *
 * <p>The CDC interface {@link GossipMessageI} (Jacques Malenfant) does not
 * declare {@code getEmitterURI()} but every concrete gossip message in this
 * project carries an emitter URI: the reflection inbound port URI of the
 * broker that just put the message on the wire. The broker uses it to skip
 * sending a re-emission back to the immediate sender (soutenance §6.2 /
 * Phase F.2). Rather than relying on reflection or a long instanceof chain,
 * we declare this single sub-interface and let every concrete message
 * implement it.</p>
 *
 * <p>Implementations must already exist (every concrete message has
 * {@code getEmitterURI()}); the only change is the explicit {@code implements}
 * clause that surfaces the method in the type system.</p>
 *
 * @author Bogdan Styn, Setbel Melissa
 */
public interface EmitterAwareGossipMessageI extends GossipMessageI
{
	/**
	 * @return URI of the broker that just emitted this gossip message
	 *         (this broker's {@code reflectionInboundPortURI}, set inside
	 *         {@link GossipMessageI#copyWithNewEmitterURI(String)}).
	 *         May be {@code null} only if the message was constructed
	 *         outside the broker pipeline (test fixtures).
	 */
	String getEmitterURI();
}
