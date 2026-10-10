package net.swofty.dreammanhunt.client.state.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.jspecify.annotations.Nullable;

final class SeekingAudioStream implements AudioStream {
	private static final int SKIP_CHUNK_BYTES = 1 << 18;
	private final AudioStream delegate;
	private @Nullable ByteBuffer leftover;

	private SeekingAudioStream(AudioStream delegate, @Nullable ByteBuffer leftover) {
		this.delegate = delegate;
		this.leftover = leftover;
	}

	static AudioStream skip(AudioStream stream, long frames) throws IOException {
		if (frames <= 0L) {
			return stream;
		}
		long remaining = frames * stream.getFormat().getFrameSize();
		ByteBuffer leftover = null;
		while (remaining > 0L) {
			ByteBuffer chunk = stream.read((int) Math.min(remaining, SKIP_CHUNK_BYTES));
			if (!chunk.hasRemaining()) {
				break;
			}
			if (chunk.remaining() > remaining) {
				chunk.position(chunk.position() + (int) remaining);
				leftover = chunk.slice();
				remaining = 0L;
			} else {
				remaining -= chunk.remaining();
			}
		}
		return new SeekingAudioStream(stream, leftover);
	}

	@Override
	public AudioFormat getFormat() {
		return this.delegate.getFormat();
	}

	@Override
	public ByteBuffer read(int expectedSize) throws IOException {
		ByteBuffer pending = this.leftover;
		if (pending != null) {
			this.leftover = null;
			if (pending.hasRemaining()) {
				return pending;
			}
		}
		return this.delegate.read(expectedSize);
	}

	@Override
	public void close() throws IOException {
		this.delegate.close();
	}
}
