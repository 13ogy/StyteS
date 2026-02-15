package fr.sorbonne_u.cps.pubsub.messages;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;
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
 * Important: conformément au contrat de {@link MessageFilterI#getTimeFilter()},
 * cette implémentation retourne toujours un filtre temporel non nul. En
 * l'absence de contrainte temporelle, elle utilise un filtre joker qui accepte
 * toutes les estampilles.
 * </p>
 *
 * <p>
 * Created on : 2026-02-08
 * </p>
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
		if (propertyFilters == null || propertiesFilters == null) {
			throw new IllegalArgumentException("Filter arrays cannot be null.");
		}

		this.propertyFilters = Arrays.copyOf(propertyFilters, propertyFilters.length);
		this.propertiesFilters =
			Arrays.copyOf(propertiesFilters, propertiesFilters.length);
		this.timeFilter = timeFilter != null ? timeFilter : new AcceptAllTimeFilter();
	}

	/**
	 * Create an empty message filter that accepts every message.
	 *
	 * @return un filtre acceptant tous les messages.
	 */
	public static MessageFilter acceptAll()
	{
		return new MessageFilter(
			new PropertyFilterI[0],
			new PropertiesFilterI[0],
			new AcceptAllTimeFilter());
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

		final Map<String, PropertyI> propsByName = indexProperties(message);

		for (PropertyFilterI pf : this.propertyFilters) {
			if (pf == null) {
				continue;
			}
			PropertyI p = propsByName.get(pf.getName());
			if (p == null) {
				return false;
			}
			if (!pf.match(p)) {
				return false;
			}
		}

		for (PropertiesFilterI mpf : this.propertiesFilters) {
			if (mpf == null) {
				continue;
			}
			if (mpf.getMultiValuesFilter() == null) {
				throw new IllegalStateException("PropertiesFilterI has null MultiValuesFilterI.");
			}
			String[] required = mpf.getMultiValuesFilter().getNames();
			PropertyI[] selected = new PropertyI[required.length];
			for (int i = 0; i < required.length; i++) {
				PropertyI p = propsByName.get(required[i]);
				if (p == null) {
					return false;
				}
				selected[i] = p;
			}
			if (!mpf.match(selected)) {
				return false;
			}
		}

		return this.timeFilter.match(message.getTimeStamp());
	}

	/**
	 * Build an index of properties by name.
	 *
	 * @param message message source.
	 * @return map {nom -> propriété}.
	 */
	protected Map<String, PropertyI> indexProperties(MessageI message)
	{
		Map<String, PropertyI> ret = new HashMap<String, PropertyI>();
		PropertyI[] props = message.getProperties();
		if (props != null) {
			for (PropertyI p : props) {
				if (p != null) {
					ret.put(p.getName(), p);
				}
			}
		}
		return ret;
	}
}
