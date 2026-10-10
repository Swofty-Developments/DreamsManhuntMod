package net.swofty.dreammanhunt.client.state.audio;

public interface ChannelPosition {
	long[] dma$position();

	void dma$seekFrames(int frames);

	void dma$addPlayedFrames(long frames);

	void dma$holdStart();

	boolean dma$takeHeldStart();

	int dma$source();
}
