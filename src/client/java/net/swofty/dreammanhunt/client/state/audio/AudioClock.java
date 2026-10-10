package net.swofty.dreammanhunt.client.state.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTSourceLatency;
import org.jspecify.annotations.Nullable;

public final class AudioClock {
	private static final int AL_SAMPLE_OFFSET_CLOCK_SOFT = 0x1202;
	private static volatile Boolean sourceLatency;

	private AudioClock() {
	}

	public static long @Nullable [] sampleOffsetClock(int source) {
		Boolean present = sourceLatency;
		if (present == null) {
			present = AL10.alIsExtensionPresent("AL_SOFT_source_latency");
			sourceLatency = present;
		}
		if (!present) {
			return null;
		}
		AL10.alGetError();
		long[] values = new long[2];
		SOFTSourceLatency.alGetSourcei64vSOFT(source, AL_SAMPLE_OFFSET_CLOCK_SOFT, values);
		if (AL10.alGetError() != AL10.AL_NO_ERROR) {
			sourceLatency = false;
			return null;
		}
		return values;
	}

}
