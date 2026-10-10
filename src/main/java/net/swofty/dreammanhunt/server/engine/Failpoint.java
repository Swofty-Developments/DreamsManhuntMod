package net.swofty.dreammanhunt.server.engine;

final class Failpoint {
	private static final String PROPERTY = "dreammanhunt.engine.failRestoreAt";

	private Failpoint() {
	}

	static void check(String stage) {
		if (stage.equals(System.getProperty(PROPERTY))) {
			System.clearProperty(PROPERTY);
			throw new IllegalStateException("Injected savestate restore failure at " + stage);
		}
	}
}
