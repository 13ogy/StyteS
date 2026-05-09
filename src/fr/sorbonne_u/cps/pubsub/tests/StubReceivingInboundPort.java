package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Port entrant minimal {@link ReceivingCI} (TEST-ONLY) utilisé par
 * {@link StubReceiverComponent} dans les tests d'enregistrement du broker.
 *
 * <p>
 * Le port ne fait rien sur {@code receive()} : il sert uniquement à fournir
 * une URI publiée à laquelle le broker peut se connecter via
 * {@code doPortConnection()} sans avoir à fabriquer un client réel.
 * </p>
 *
 * <p>
 * <strong>Cette classe n'est PAS du code de production</strong> ; elle vit
 * uniquement dans le package {@code tests/}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class StubReceivingInboundPort
	extends AbstractInboundPort
	implements ReceivingCI
{
	private static final long serialVersionUID = 1L;

	/**
	 * Construit le port stub et l'attache au composant propriétaire.
	 *
	 * @param owner composant qui possède ce port (ne doit pas être {@code null}).
	 * @throws Exception si l'enregistrement de l'interface échoue.
	 */
	public StubReceivingInboundPort(ComponentI owner) throws Exception
	{
		super(ReceivingCI.class, owner);
	}

	/** No-op: registration tests do not deliver messages. */
	@Override
	public void receive(String channel, MessageI message) throws Exception
	{
		// stub — intentionally empty
	}

	/** No-op: registration tests do not deliver messages. */
	@Override
	public void receive(String channel, MessageI[] messages) throws Exception
	{
		// stub — intentionally empty
	}
}
