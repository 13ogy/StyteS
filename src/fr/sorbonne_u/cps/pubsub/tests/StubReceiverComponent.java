package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Composant stub minimal (TEST-ONLY) qui publie un port d'entrée
 * {@link ReceivingCI} pour permettre au {@link
 * fr.sorbonne_u.cps.pubsub.base.components.Broker} de se connecter à lui
 * pendant les tests d'enregistrement.
 *
 * <p>
 * Ce composant n'a aucune logique métier au-delà de la création/publication du
 * port. Il est utilisé dans {@link BrokerRegistrationTest} à la place de
 * {@link fr.sorbonne_u.cps.pubsub.base.components.PluginClient} pour éviter une
 * pré-condition BCM qui se déclenche sous {@code -ea} lorsqu'un plugin tente
 * d'ajouter une interface offerte déjà déclarée par {@code @OfferedInterfaces}.
 * </p>
 *
 * <p>
 * <strong>Cette classe n'est PAS du code de production</strong> ; elle vit
 * uniquement dans le package {@code tests/}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
public class StubReceiverComponent extends AbstractComponent
{
	private final StubReceivingInboundPort receivingPort;

	/** Parameterless constructor for BCM's {@code createComponent} reflective factory. */
	protected StubReceiverComponent() throws Exception
	{
		super(1, 0);
		this.receivingPort = new StubReceivingInboundPort(this);
		this.receivingPort.publishPort();
	}

	/**
	 * Return the URI of the published {@link ReceivingCI} inbound port.
	 *
	 * @return URI as a String; never null.
	 * @throws Exception if the port URI cannot be retrieved.
	 */
	public String getReceptionPortURI() throws Exception
	{
		return this.receivingPort.getPortURI();
	}
}
