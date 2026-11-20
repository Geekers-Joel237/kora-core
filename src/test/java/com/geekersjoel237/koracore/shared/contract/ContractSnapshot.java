package com.geekersjoel237.koracore.shared.contract;

import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.fail;

public final class ContractSnapshot {

    public static final Path ROOT = Path.of("src", "test", "resources", "contracts");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ContractSnapshot() {
    }

    public static void assertMatches(String name, MvcTestResult result) {
        approve(ROOT.resolve(name + ".json"), observed(result));
    }

    public static void approve(Path expectedFile, JsonNode actual) {
        Path receivedFile = expectedFile.resolveSibling(
                expectedFile.getFileName().toString().replaceFirst("\\.json$", ".received.json"));

        if (Files.exists(expectedFile) && read(expectedFile).equals(actual)) {
            delete(receivedFile);
            return;
        }
        write(receivedFile, actual);
        fail(Files.exists(expectedFile)
                ? "The boundary changed: compare " + receivedFile + " with " + expectedFile
                  + ", and replace the expected file only if the change is intended"
                : "No contract recorded yet: review " + receivedFile
                  + " and rename it to " + expectedFile.getFileName() + " to accept it");
    }

    public static JsonNode body(MvcTestResult result) {
        byte[] content = result.getResponse().getContentAsByteArray();
        if (content.length == 0)
            return JSON.nullNode();
        String text = new String(content, StandardCharsets.UTF_8);
        try {
            return JSON.readTree(text);
        } catch (RuntimeException notJson) {
            return JSON.getNodeFactory().stringNode(text);
        }
    }

    public static JsonNode read(Path file) {
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode observed(MvcTestResult result) {
        var request = result.getRequest();
        var response = result.getResponse();
        ObjectNode node = JSON.createObjectNode();
        node.put("operation", request.getMethod() + " " + request.getRequestURI());
        node.put("status", response.getStatus());
        node.put("contentType", response.getContentType());
        node.set("body", body(result));
        return node;
    }

    private static void write(Path file, JsonNode node) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
