import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

// Usage: java ImageDiff.java <a.png> <b.png> [<a.png> <b.png> ...]
// Prints one line per pair: DIFF|a|b|width|height|meanAbs|fractionOver16|maxChannelDelta
public final class ImageDiff {
	public static void main(String[] args) throws IOException {
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
