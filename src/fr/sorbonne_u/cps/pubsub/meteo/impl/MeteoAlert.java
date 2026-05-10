package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Simple meteo alert implementation for CDC §3.4.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class MeteoAlert implements MeteoAlertI {
	private static final long serialVersionUID = 1L;

	private final AlertTypeI alertType;
	private final LevelI level;
	private final RegionI[] regions;
	private final Instant startTime;
	private final Duration duration;

	/**
	 * Crée une alerte météo.
	 *
	 * @param alertType type d'aléa (non {@code null}).
	 * @param level niveau de sévérité (non {@code null}).
	 * @param regions régions concernées (non {@code null}, non vide).
	 * @param startTime instant de début de validité (non {@code null}).
	 * @param duration durée de validité (non {@code null}).
	 * @throws IllegalArgumentException si l'une des préconditions est violée.
	 */
	public MeteoAlert(
			AlertTypeI alertType,
			LevelI level,
			RegionI[] regions,
			Instant startTime,
			Duration duration) {
		if (alertType == null) {
			throw new IllegalArgumentException("alertType cannot be null");
		}
		if (level == null) {
			throw new IllegalArgumentException("level cannot be null");
		}
		if (regions == null || regions.length == 0) {
			throw new IllegalArgumentException("regions cannot be null/empty");
		}
		if (startTime == null) {
			throw new IllegalArgumentException("startTime cannot be null");
		}
		if (duration == null) {
			throw new IllegalArgumentException("duration cannot be null");
		}
		this.alertType = alertType;
		this.level = level;
		this.regions = regions;
		this.startTime = startTime;
		this.duration = duration;
	}

	/**
	 * @return le type d'aléa associé à l'alerte.
	 */
	@Override
	public AlertTypeI getAlertType() {
		return alertType;
	}

	/**
	 * @return le niveau de sévérité de l'alerte.
	 */
	@Override
	public LevelI getLevel() {
		return level;
	}

	/**
	 * @return les régions géographiques concernées (référence interne, non copiée).
	 */
	@Override
	public RegionI[] getRegions() {
		return regions;
	}

	/**
	 * @return l'instant de début de validité de l'alerte.
	 */
	@Override
	public Instant getStartTime() {
		return startTime;
	}

	/**
	 * @return la durée de validité de l'alerte.
	 */
	@Override
	public Duration getDuration() {
		return duration;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof MeteoAlert)) return false;
		MeteoAlert meteoAlert = (MeteoAlert) o;
		return Objects.equals(alertType, meteoAlert.alertType)
				&& Objects.equals(level, meteoAlert.level)
				&& Arrays.equals(regions, meteoAlert.regions)
				&& Objects.equals(startTime, meteoAlert.startTime)
				&& Objects.equals(duration, meteoAlert.duration);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(alertType, level, startTime, duration);
		result = 31 * result + Arrays.hashCode(regions);
		return result;
	}

	@Override
	public String toString() {
		return "MeteoAlert{"
				+ "alertType="
				+ alertType
				+ ", level="
				+ level
				+ ", regions="
				+ Arrays.toString(regions)
				+ ", startTime="
				+ startTime
				+ ", duration="
				+ duration
				+ '}';
	}
}
