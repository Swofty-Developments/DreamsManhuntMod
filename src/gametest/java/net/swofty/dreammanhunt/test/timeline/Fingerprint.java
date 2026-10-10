package net.swofty.dreammanhunt.test.timeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class Fingerprint {
	private final Map<String, Map<String, String>> subjects = new TreeMap<>();

	public Map<String, String> subject(String key) {
		return this.subjects.computeIfAbsent(key, k -> new LinkedHashMap<>());
	}

	public void put(String subject, String field, Object value) {
		this.subject(subject).put(field, String.valueOf(value));
	}

	public Map<String, Map<String, String>> subjects() {
		return this.subjects;
	}
}
