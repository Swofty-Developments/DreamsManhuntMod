package net.swofty.dreammanhunt.randomroot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jspecify.annotations.Nullable;

public final class SeedStreams {
	public static final Stream CLIENT = new Stream();
	public static final Stream SERVER = new Stream();
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
	private static volatile @Nullable Thread clientThread;
	private static volatile @Nullable Thread serverThread;

	private SeedStreams() {
	}

	public static void setClientThread(Thread thread) {
		clientThread = thread;
	}

	public static void setServerThread(Thread thread) {
		serverThread = thread;
	}

	public static @Nullable Stream current() {
		Thread thread = Thread.currentThread();
		if (thread == clientThread) {
			return CLIENT;
		}
		if (thread == serverThread) {
			return SERVER;
		}
		return null;
	}

	public static @Nullable Long nextSeed() {
		Stream stream = current();
		return stream == null ? null : stream.next();
	}

	public static RandomSource threadLocal(java.util.function.Supplier<RandomSource> original) {
		Long seed = nextSeed();
		return seed == null ? original.get() : RandomSource.createThreadLocalInstance(seed);
	}

	public static void register(RandomSource random) {
		Stream stream = current();
		if (stream != null) {
			stream.register(random);
		}
	}

	public static void beginLoad() {
		Stream stream = current();
		if (stream != null) {
			stream.loading++;
		}
	}

	public static void endLoad() {
		Stream stream = current();
		if (stream != null && stream.loading > 0) {
			stream.loading--;
		}
	}

	public static void beginEntity(UUID uuid) {
		Stream stream = current();
		if (stream != null) {
			stream.pendingUuid = uuid;
			stream.pendingCount = 0;
		}
	}

	public static void endEntity() {
		Stream stream = current();
		if (stream != null) {
			stream.pendingUuid = null;
		}
	}

	public static void captureServer(CompoundTag tag) {
		tag.put("randomroot", SERVER.capture());
	}

	public static void applyServer(CompoundTag tag) {
		SERVER.schedule(tag.getCompoundOrEmpty("randomroot"));
	}

	public static void onServerTickStart(boolean frozen) {
		if (!frozen) {
			SERVER.applyScheduled();
		}
	}

	public static final class Stream {
		private static final int KEPT_SNAPSHOTS = 64;
		private final LegacyRandomSource source = new LegacyRandomSource(new SecureRandom().nextLong());
		private final AtomicLong sourceSeed = readSeed(this.source);
		private final Set<RandomSource> live = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
		private final Map<Long, List<Runnable>> snapshots = Collections.synchronizedMap(new LinkedHashMap<>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, List<Runnable>> eldest) {
				return this.size() > KEPT_SNAPSHOTS;
			}
		});
		private long salt = new SecureRandom().nextLong();
		private int loading;
		private @Nullable UUID pendingUuid;
		private int pendingCount;
		private @Nullable CompoundTag scheduled;

		private Stream() {
		}

		@Nullable Long next() {
			UUID uuid = this.pendingUuid;
			if (uuid != null) {
				long mixed = uuid.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ uuid.getLeastSignificantBits() ^ this.salt;
				return mix(mixed + this.pendingCount++ * 0xBF58476D1CE4E5B9L);
			}
			if (this.loading > 0) {
				return null;
			}
			return this.source.nextLong();
		}

		void register(RandomSource random) {
			if (random != this.source) {
				this.live.add(random);
			}
		}

		public CompoundTag capture() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("seed", this.sourceSeed.get());
			tag.putLong("salt", this.salt);
			long token = new SecureRandom().nextLong();
			tag.putLong("token", token);
			List<RandomSource> generators;
			synchronized (this.live) {
				generators = new ArrayList<>(this.live);
			}
			List<Runnable> restorers = new ArrayList<>();
			for (RandomSource generator : generators) {
				snapshot(generator, restorers, 0);
			}
			this.snapshots.put(token, restorers);
			return tag;
		}

		public void apply(CompoundTag tag) {
			if (tag.isEmpty()) {
				return;
			}
			this.sourceSeed.set(tag.getLongOr("seed", this.sourceSeed.get()));
			this.salt = tag.getLongOr("salt", this.salt);
			List<Runnable> restorers = this.snapshots.get(tag.getLongOr("token", 0L));
			if (restorers != null) {
				for (Runnable restorer : restorers) {
					restorer.run();
				}
			}
		}

		void schedule(CompoundTag tag) {
			this.scheduled = tag;
			this.apply(tag);
		}

		void applyScheduled() {
			CompoundTag tag = this.scheduled;
			if (tag != null) {
				this.scheduled = null;
				this.apply(tag);
			}
		}

		private static long mix(long z) {
			z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
			z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
			return z ^ z >>> 31;
		}

		private static AtomicLong readSeed(LegacyRandomSource source) {
			try {
				Field field = LegacyRandomSource.class.getDeclaredField("seed");
				field.setAccessible(true);
				return (AtomicLong) field.get(source);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static void snapshot(Object owner, List<Runnable> restorers, int depth) {
		for (Field field : fields(owner.getClass())) {
			try {
				Object value = field.get(owner);
				Class<?> type = field.getType();
				if (type.isPrimitive()) {
					if (!Modifier.isFinal(field.getModifiers())) {
						restorers.add(() -> {
							try {
								field.set(owner, value);
							} catch (IllegalAccessException ignored) {
							}
						});
					}
				} else if (value instanceof AtomicLong atomic) {
					long saved = atomic.get();
					restorers.add(() -> atomic.set(saved));
				} else if (value != null && depth < 2 && !(value instanceof RandomSource)
					&& value.getClass().getName().startsWith("net.minecraft.world.level.levelgen.")) {
					snapshot(value, restorers, depth + 1);
				}
			} catch (IllegalAccessException | RuntimeException ignored) {
			}
		}
	}

	private static List<Field> fields(Class<?> type) {
		return FIELDS.computeIfAbsent(type, key -> {
			List<Field> out = new ArrayList<>();
			for (Class<?> current = key; current != null && current != Object.class; current = current.getSuperclass()) {
				for (Field field : current.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					}
					try {
						field.setAccessible(true);
						out.add(field);
					} catch (RuntimeException ignored) {
					}
				}
			}
			return List.copyOf(out);
		});
	}
}
