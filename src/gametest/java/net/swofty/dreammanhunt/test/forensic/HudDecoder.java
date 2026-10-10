package net.swofty.dreammanhunt.test.forensic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class HudDecoder {
	private static final int SPRITE = 9;
	private static final int[] HEART_SHIFTS = {-1, 0, 1, 2};
	private static final int[] FOOD_SHIFTS = {-2, -1, 0, 1, 2};
	private static final double MIN_SCORE = 0.85;
	private static final double MIN_MARGIN = 0.15;
	private static final int TOLERANCE = 12;

	private final List<Template> heartTemplates;
	private final List<Template> foodTemplates;

	private record Template(String name, int halves, int[] argb, int[] linear, boolean[] mask) {
	}

	public record Geometry(int screenWidth, int screenHeight, int scale, int originX, int originY) {
		public int guiWidth() {
			return (int) Math.ceil((double) screenWidth / scale);
		}

		public int guiHeight() {
			return (int) Math.ceil((double) screenHeight / scale);
		}

		public int xLeft() {
			return guiWidth() / 2 - 91;
		}

		public int xRight() {
			return guiWidth() / 2 + 91;
		}

		public int yLineBase() {
			return guiHeight() - 39;
		}

		public static Geometry hudCrop(int screenWidth, int screenHeight, int scale) {
			Geometry full = new Geometry(screenWidth, screenHeight, scale, 0, 0);
			return new Geometry(screenWidth, screenHeight, scale, (full.xLeft() - 4) * scale, (full.yLineBase() - 6) * scale);
		}

		public int cropWidth() {
			return (182 + 8) * scale;
		}

		public int cropHeight() {
			return (SPRITE + 12) * scale;
		}
	}

	public record Slot(int shift, String template, int halves, double score, double margin) {
	}

	public record Frame(Slot[] hearts, Slot[] food) {
		public int[] heartShifts() {
			int[] out = new int[hearts.length];
			for (int i = 0; i < hearts.length; i++) {
				out[i] = hearts[i].shift();
			}
			return out;
		}

		public int[] foodShifts() {
			int[] out = new int[food.length];
			for (int i = 0; i < food.length; i++) {
				out[i] = food[i].shift();
			}
			return out;
		}

		public int healthHalves() {
			int sum = 0;
			for (Slot slot : hearts) {
				sum += slot.halves();
			}
			return sum;
		}

		public int foodLevel() {
			int sum = 0;
			for (Slot slot : food) {
				sum += slot.halves();
			}
			return sum;
		}

		public boolean blinking() {
			for (Slot slot : hearts) {
				if (slot.template().contains("blinking")) {
					return true;
				}
			}
			return false;
		}

		public double minScore() {
			double min = 1.0;
			for (Slot slot : hearts) {
				min = Math.min(min, slot.score());
			}
			for (Slot slot : food) {
				min = Math.min(min, slot.score());
			}
			return min;
		}

		public double minMargin() {
			double min = 1.0;
			for (Slot slot : hearts) {
				min = Math.min(min, slot.margin());
			}
			for (Slot slot : food) {
				min = Math.min(min, slot.margin());
			}
			return min;
		}

		public boolean confident() {
			return minScore() >= MIN_SCORE && minMargin() >= MIN_MARGIN;
		}

		public boolean heartsShake() {
			return healthHalves() <= 4;
		}

		public boolean foodJolt() {
			for (Slot slot : food) {
				if (slot.shift() != 0) {
					return true;
				}
			}
			return false;
		}

		public boolean shiftsInVanillaRange() {
			for (Slot slot : hearts) {
				if (slot.shift() < 0 || slot.shift() > 1 || (!heartsShake() && slot.shift() != 0)) {
					return false;
				}
			}
			for (Slot slot : food) {
				if (slot.shift() < -1 || slot.shift() > 1) {
					return false;
				}
			}
			return true;
		}

		public JoltModel.Conditions conditions() {
			return new JoltModel.Conditions(hearts.length, heartsShake(), foodLevel());
		}
	}

	private HudDecoder(List<Template> heartTemplates, List<Template> foodTemplates) {
		this.heartTemplates = heartTemplates;
		this.foodTemplates = foodTemplates;
	}

	public static HudDecoder load(ResourceManager resources) {
		Map<String, int[]> sprites = new HashMap<>();
		for (String name : List.of("heart/container", "heart/container_blinking", "heart/full", "heart/half", "heart/full_blinking",
				"heart/half_blinking", "food_empty", "food_half", "food_full", "food_empty_hunger", "food_half_hunger", "food_full_hunger")) {
			sprites.put(name, read(resources, name));
		}
		List<Template> hearts = new ArrayList<>();
		for (String container : List.of("heart/container", "heart/container_blinking")) {
			hearts.add(compose(container, 0, sprites, container));
			for (String ghost : List.of("", "heart/full_blinking", "heart/half_blinking")) {
				for (String current : List.of("", "heart/full", "heart/half")) {
					if (ghost.isEmpty() && current.isEmpty()) {
						continue;
					}
					int halves = current.equals("heart/full") ? 2 : current.equals("heart/half") ? 1 : 0;
					List<String> layers = new ArrayList<>(List.of(container));
					if (!ghost.isEmpty()) {
						layers.add(ghost);
					}
					if (!current.isEmpty()) {
						layers.add(current);
					}
					hearts.add(compose(String.join("+", layers), halves, sprites, layers.toArray(String[]::new)));
				}
			}
		}
		List<Template> food = new ArrayList<>();
		for (String suffix : List.of("", "_hunger")) {
			food.add(compose("food_empty" + suffix, 0, sprites, "food_empty" + suffix));
			food.add(compose("food_half" + suffix, 1, sprites, "food_empty" + suffix, "food_half" + suffix));
			food.add(compose("food_full" + suffix, 2, sprites, "food_empty" + suffix, "food_full" + suffix));
		}
		return new HudDecoder(hearts, food);
	}

	private static int[] read(ResourceManager resources, String name) {
		Identifier id = Identifier.withDefaultNamespace("textures/gui/sprites/hud/" + name + ".png");
		try (InputStream in = resources.open(id)) {
			BufferedImage image = ImageIO.read(in);
			if (image.getWidth() != SPRITE || image.getHeight() != SPRITE) {
				throw new AssertionError(id + " is " + image.getWidth() + "x" + image.getHeight());
			}
			return image.getRGB(0, 0, SPRITE, SPRITE, null, 0, SPRITE);
		} catch (IOException e) {
			throw new AssertionError("cannot read sprite " + id, e);
		}
	}

	private static Template compose(String name, int halves, Map<String, int[]> sprites, String... layers) {
		int[] argb = new int[SPRITE * SPRITE];
		for (String layer : layers) {
			int[] src = sprites.get(layer);
			for (int i = 0; i < argb.length; i++) {
				int alpha = src[i] >>> 24;
				if (alpha == 255) {
					argb[i] = src[i];
				} else if (alpha != 0) {
					argb[i] = 0;
				}
			}
		}
		boolean[] mask = new boolean[argb.length];
		int[] linear = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			mask[i] = argb[i] >>> 24 == 255;
			linear[i] = toLinear(argb[i]);
		}
		return new Template(name, halves, argb, linear, mask);
	}

	private static int toLinear(int argb) {
		int out = argb & 0xFF000000;
		for (int shift = 0; shift <= 16; shift += 8) {
			double c = ((argb >> shift) & 255) / 255.0;
			double l = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
			out |= (int) Math.round(l * 255.0) << shift;
		}
		return out;
	}

	public Frame decode(BufferedImage image, Geometry geometry) {
		Slot[] hearts = new Slot[10];
		for (int column = 0; column < 10; column++) {
			hearts[column] = match(image, geometry, heartTemplates, geometry.xLeft() + column * 8, geometry.yLineBase(), HEART_SHIFTS);
		}
		Slot[] food = new Slot[JoltModel.FOOD_ICONS];
		for (int i = 0; i < JoltModel.FOOD_ICONS; i++) {
			food[i] = match(image, geometry, foodTemplates, geometry.xRight() - i * 8 - 9, geometry.yLineBase(), FOOD_SHIFTS);
		}
		return new Frame(hearts, food);
	}

	private static Slot match(BufferedImage image, Geometry geometry, List<Template> templates, int guiX, int guiY, int[] shifts) {
		double[] bestPerShift = new double[shifts.length];
		Template[] bestTemplate = new Template[shifts.length];
		for (int k = 0; k < shifts.length; k++) {
			for (Template template : templates) {
				double score = score(image, geometry, template, guiX, guiY + shifts[k]);
				if (score > bestPerShift[k]) {
					bestPerShift[k] = score;
					bestTemplate[k] = template;
				}
			}
		}
		int best = 0;
		for (int k = 1; k < shifts.length; k++) {
			if (bestPerShift[k] > bestPerShift[best]) {
				best = k;
			}
		}
		double runnerUp = 0.0;
		for (int k = 0; k < shifts.length; k++) {
			if (k != best) {
				runnerUp = Math.max(runnerUp, bestPerShift[k]);
			}
		}
		Template template = bestTemplate[best];
		return new Slot(shifts[best], template == null ? "none" : template.name(), template == null ? 0 : template.halves(),
				bestPerShift[best], bestPerShift[best] - runnerUp);
	}

	private static double score(BufferedImage image, Geometry geometry, Template template, int guiX, int guiY) {
		int scale = geometry.scale();
		int total = 0;
		int matched = 0;
		for (int sy = 0; sy < SPRITE; sy++) {
			for (int sx = 0; sx < SPRITE; sx++) {
				int index = sy * SPRITE + sx;
				if (!template.mask()[index]) {
					continue;
				}
				int expected = template.argb()[index];
				int expectedLinear = template.linear()[index];
				for (int v = 0; v < scale; v++) {
					for (int u = 0; u < scale; u++) {
						total++;
						int px = (guiX + sx) * scale + u - geometry.originX();
						int py = (guiY + sy) * scale + v - geometry.originY();
						if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
							continue;
						}
						int actual = image.getRGB(px, py);
						if (close(actual, expected) || close(actual, expectedLinear)) {
							matched++;
						}
					}
				}
			}
		}
		return total == 0 ? 0.0 : matched / (double) total;
	}

	private static boolean close(int a, int b) {
		return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) <= TOLERANCE
				&& Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) <= TOLERANCE
				&& Math.abs((a & 255) - (b & 255)) <= TOLERANCE;
	}
}
