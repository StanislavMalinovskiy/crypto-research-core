package io.cryptoresearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RepositoryConventionsTest {

	private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)[^)]*\\)");
	private static final List<String> EXPORT_MARKERS = List.of("app.notion.com", "Exported from Notion", "Экспортировано из Notion");
	private static final List<String> PREVIEW_API_MARKERS = List.of("StructuredTaskScope", "ScopedValue");

	private final Path repositoryRoot = Path.of("").toAbsolutePath().normalize();

	@Test
	void markdownHasNoExportMarkersOrBrokenRelativeLinks() throws IOException {
		var violations = new ArrayList<String>();

		for (var markdown : markdownFiles()) {
			var content = Files.readString(markdown);
			checkExportMarkers(markdown, content, violations);
			checkRelativeLinks(markdown, content, violations);
		}

		assertThat(violations).isEmpty();
	}

	@Test
	void publicModuleApisDoNotExposePreviewTypes() throws IOException {
		var violations = new ArrayList<String>();

		try (var paths = Files.walk(repositoryRoot.resolve("src/main/java/io/cryptoresearch"))) {
			paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> path.toString().contains("\\api\\"))
					.forEach(path -> checkPreviewMarkers(path, violations));
		}

		assertThat(violations).isEmpty();
	}

	private List<Path> markdownFiles() throws IOException {
		try (Stream<Path> paths = Files.walk(repositoryRoot)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".md"))
					.filter(path -> !path.startsWith(repositoryRoot.resolve("target")))
					.toList();
		}
	}

	private void checkExportMarkers(Path path, String content, List<String> violations) {
		EXPORT_MARKERS.stream()
				.filter(content::contains)
				.map(marker -> relative(path) + " contains export marker: " + marker)
				.forEach(violations::add);
	}

	private void checkRelativeLinks(Path markdown, String content, List<String> violations) {
		var matcher = MARKDOWN_LINK.matcher(content);

		while (matcher.find()) {
			var target = matcher.group(1).replace("<", "").replace(">", "");
			if (isRelativeFileLink(target) && !resolves(markdown, target)) {
				violations.add(relative(markdown) + " has broken link: " + target);
			}
		}
	}

	private boolean isRelativeFileLink(String target) {
		return !target.startsWith("#") && !URI.create(target).isAbsolute();
	}

	private boolean resolves(Path markdown, String target) {
		var withoutFragment = target.split("[#?]", 2)[0];
		var decoded = URLDecoder.decode(withoutFragment, StandardCharsets.UTF_8);
		return Files.exists(markdown.getParent().resolve(decoded).normalize());
	}

	private void checkPreviewMarkers(Path path, List<String> violations) {
		try {
			var content = Files.readString(path);
			PREVIEW_API_MARKERS.stream()
					.filter(content::contains)
					.map(marker -> relative(path) + " exposes preview API marker: " + marker)
					.forEach(violations::add);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect " + path, exception);
		}
	}

	private String relative(Path path) {
		return repositoryRoot.relativize(path).toString();
	}
}
