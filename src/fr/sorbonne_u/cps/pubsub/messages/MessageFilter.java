package fr.sorbonne_u.cps.pubsub.messages;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllMessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;

/**
 * La classe {@code MessageFilter} implémente {@link MessageFilterI} pour filtrer
 * les messages livrés par le système de publication/souscription.
 *
 * <p>
 * Un message est accepté lorsque :
 * </p>
 * <ul>
 *   <li>tous les {@link PropertyFilterI} configurés acceptent leur propriété
 *   cible (sémantique AND) ;</li>
 *   <li>tous les {@link PropertiesFilterI} configurés acceptent leur ensemble de
 *   propriétés cibles (sémantique AND) ;</li>
 *   <li>le {@link TimeFilterI} configuré accepte l’horodatage du message.</li>
 * </ul>
 *
 * <p>
 * Les propriétés manquantes requises par un filtre ne déclenchent pas
 * d’exception ; elles font simplement retourner {@code false} à
 * {@link #match(MessageI)}.
 * </p>
 *
 * <p>
 * En l'absence de contrainte temporelle, elle utilise un filtre joker qui accepte
 * toutes les estampilles.
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class MessageFilter implements MessageFilterI
{
	private static final long serialVersionUID = 1L;

	/** Property filters applied individually. */
	protected final PropertyFilterI[] propertyFilters;
	/** Properties filters applied on groups of properties. */
	protected final PropertiesFilterI[] propertiesFilters;
	/** Time filter (never null). */
	protected final TimeFilterI timeFilter;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a message filter.
	 *
	 * <p><strong>Contract</strong></p>
	 *
	 * <pre>
	 * pre  {@code propertyFilters != null}
	 * pre  {@code propertiesFilters != null}
	 * post {@code getPropertyFilters() != null}
	 * post {@code getPropertiesFilters() != null}
	 * post {@code getTimeFilter() != null}
	 * </pre>
	 *
	 * @param propertyFilters    filtres unitaires (peut être vide).
	 * @param propertiesFilters  filtres multi-propriétés (peut être vide).
	 * @param timeFilter         filtre temporel ; peut être null (joker appliqué).
	 */
	public MessageFilter(
		PropertyFilterI[] propertyFilters,
		PropertiesFilterI[] propertiesFilters,
		TimeFilterI timeFilter
		)
	{
		Objects.requireNonNull(propertyFilters, "propertyFilters cannot be null");
		Objects.requireNonNull(propertiesFilters, "propertiesFilters cannot be null");

		for (int k = 0; k < propertyFilters.length; k++) {
			Objects.requireNonNull(propertyFilters[k],
				"propertyFilters[" + k + "] cannot be null");
		}
		for (int k = 0; k < propertiesFilters.length; k++) {
			Objects.requireNonNull(propertiesFilters[k],
				"propertiesFilters[" + k + "] cannot be null");
			Objects.requireNonNull(propertiesFilters[k].getMultiValuesFilter(),
				"propertiesFilters[" + k + "].getMultiValuesFilter() cannot be null");
		}

		this.propertyFilters = Arrays.copyOf(propertyFilters, propertyFilters.length);
		this.propertiesFilters = Arrays.copyOf(propertiesFilters, propertiesFilters.length);
		this.timeFilter = timeFilter != null ? timeFilter : new AcceptAllTimeFilter();
	}

	/**
	 * Return a singleton message filter that accepts every message.
	 *
	 * <p>
	 * Optimisation : plutôt que construire à chaque appel une
	 * {@link MessageFilter} with empty arrays — whose {@link #match(MessageI)}
	 * still allocates and iterates — we return a singleton implementing
	 * {@link MessageFilterI} directly with a trivial {@code match} that returns
	 * {@code true}.
	 * </p>
	 *
	 * @return un filtre acceptant tous les messages.
	 */
	public static MessageFilterI acceptAll()
	{
		return AcceptAllMessageFilter.INSTANCE;
	}

	// -------------------------------------------------------------------------
	// Methods
	// -------------------------------------------------------------------------

	@Override
	public PropertyFilterI[] getPropertyFilters()
	{
		return Arrays.copyOf(this.propertyFilters, this.propertyFilters.length);
	}

	@Override
	public PropertiesFilterI[] getPropertiesFilters()
	{
		return Arrays.copyOf(this.propertiesFilters, this.propertiesFilters.length);
	}

	@Override
	public TimeFilterI getTimeFilter()
	{
		return this.timeFilter;
	}

	@Override
	public boolean match(MessageI message)
	{
		if (message == null) {
			return false;
		}

		// Reuse the immutable view exposed by Message rather than rebuild a
		// HashMap auxiliaire (allocations inutiles). On revient à un balayage linéaire
		// scan over getProperties() if the message is some other MessageI impl.
		if (message instanceof Message) {
			final Map<String, PropertyI> byName = ((Message) message).getPropertiesMap();
			for (PropertyFilterI pf : this.propertyFilters) {
				PropertyI p = byName.get(pf.getName());
				if (p == null || !pf.match(p)) {
					return false;
				}
			}
			for (PropertiesFilterI mpf : this.propertiesFilters) {
				String[] required = mpf.getMultiValuesFilter().getNames();
				PropertyI[] selected = new PropertyI[required.length];
				for (int i = 0; i < required.length; i++) {
					PropertyI p = byName.get(required[i]);
					if (p == null) {
						return false;
					}
					selected[i] = p;
				}
				if (!mpf.match(selected)) {
					return false;
				}
			}
		} else {
			final PropertyI[] props = message.getProperties();
			for (PropertyFilterI pf : this.propertyFilters) {
				PropertyI p = findByName(props, pf.getName());
				if (p == null || !pf.match(p)) {
					return false;
				}
			}
			for (PropertiesFilterI mpf : this.propertiesFilters) {
				String[] required = mpf.getMultiValuesFilter().getNames();
				PropertyI[] selected = new PropertyI[required.length];
				for (int i = 0; i < required.length; i++) {
					PropertyI p = findByName(props, required[i]);
					if (p == null) {
						return false;
					}
					selected[i] = p;
				}
				if (!mpf.match(selected)) {
					return false;
				}
			}
		}

		return this.timeFilter.match(message.getTimeStamp());
	}

	private static PropertyI findByName(PropertyI[] props, String name)
	{
		for (PropertyI p : props) {
			if (name.equals(p.getName())) {
				return p;
			}
		}
		return null;
	}
}
