package net.swofty.dreammanhunt.server.engine;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class SnapshotFiles {
	static final Set<String> WORLD_EXCLUSIONS = Set.of("session.lock", "dreammanhunt");

	private SnapshotFiles() {
	}

	static void copyTree(Path from, Path to, Set<String> topLevelExclusions) throws IOException {
		Files.createDirectories(to);
		try (Stream<Path> children = Files.list(from)) {
			for (Path child : children.toList()) {
				if (topLevelExclusions.contains(child.getFileName().toString())) {
					continue;
				}
				copyRecursively(child, to.resolve(child.getFileName().toString()));
			}
		}
	}

	private static void copyRecursively(Path source, Path target) throws IOException {
		if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			return;
		}
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().endsWith(".tmp")) {
					return FileVisitResult.CONTINUE;
				}
				Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	static void clearTree(Path root, Set<String> topLevelExclusions) throws IOException {
		try (Stream<Path> children = Files.list(root)) {
			List<Path> list = children.toList();
			for (Path child : list) {
				if (!topLevelExclusions.contains(child.getFileName().toString())) {
					deleteRecursively(child);
				}
			}
		}
	}

	static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.delete(path);
			return;
		}
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
