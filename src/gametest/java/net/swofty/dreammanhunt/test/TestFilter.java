package net.swofty.dreammanhunt.test;

public final class TestFilter {
	private TestFilter() {
	}

	public static boolean enabled(String name) {
		String selected = System.getProperty("dreammanhunt.test", "all");
		return selected.equals("all") || selected.equals(name);
	}
}
