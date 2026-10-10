import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

// Usage: java ImageDiff.java <a.png> <b.png> [<a.png> <b.png> ...]
//        java ImageDiff.java --green <file.png> [...]
// Pairs print DIFF|a|b|width|height|meanAbs|fractionOver16|maxChannelDelta.
// --green prints GREEN|file|bandFraction|restFraction: the share of pure #00FF00 pixels in the left 5% of the
// image and in everything right of 8%.
public final class ImageDiff {
	public static void main(String[] args) throws IOException {
		if (args.length > 0 && args[0].equals("--green")) {
			for (int i = 1; i < args.length; i++) {
				BufferedImage image = ImageIO.read(new File(args[i]));
				int band = (int) (image.getWidth() * 0.05);
				int restFrom = (int) (image.getWidth() * 0.08);
				long bandHits = 0, bandTotal = 0, restHits = 0, restTotal = 0;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						boolean green = (image.getRGB(x, y) & 0xFFFFFF) == 0x00FF00;
						if (x < band) {
							bandTotal++;
							bandHits += green ? 1 : 0;
						} else if (x >= restFrom) {
							restTotal++;
							restHits += green ? 1 : 0;
						}
					}
				}
				System.out.println(String.format(Locale.ROOT, "GREEN|%s|%.6f|%.6f", args[i], bandHits / (double) bandTotal, restHits / (double) restTotal));
			}
			return;
		}
		for (int i = 0; i + 1 < args.length; i += 2) {
			BufferedImage a = ImageIO.read(new File(args[i]));
			BufferedImage b = ImageIO.read(new File(args[i + 1]));
			if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
				System.out.println("DIFF|" + args[i] + "|" + args[i + 1] + "|size-mismatch");
				continue;
			}
			long total = 0;
			long over = 0;
			int max = 0;
			int pixels = a.getWidth() * a.getHeight();
			for (int y = 0; y < a.getHeight(); y++) {
				for (int x = 0; x < a.getWidth(); x++) {
					int pa = a.getRGB(x, y);
					int pb = b.getRGB(x, y);
					int worst = 0;
					for (int shift = 0; shift <= 16; shift += 8) {
						int delta = Math.abs(((pa >> shift) & 0xFF) - ((pb >> shift) & 0xFF));
						total += delta;
						worst = Math.max(worst, delta);
					}
					if (worst > 16) {
						over++;
					}
					max = Math.max(max, worst);
				}
			}
			System.out.println(String.format(Locale.ROOT, "DIFF|%s|%s|%d|%d|%.4f|%.6f|%d", args[i], args[i + 1], a.getWidth(), a.getHeight(),
					total / (pixels * 3.0), over / (double) pixels, max));
		}
	}
}
