// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.rules.TemporaryFolder;

/** Materializes only listed original test inputs from either classes or a test JAR. */
final class PrintedBeamOriginalResources {
    static Path materialize(String name, TemporaryFolder temporary) throws IOException {
        Path target = temporary.newFolder(name).toPath();
        String prefix = "/source-beams/" + name + "/";
        try (InputStream index =
                PrintedBeamOriginalResources.class.getResourceAsStream(prefix + "FILES.txt")) {
            if (index == null) throw new FileNotFoundException(prefix + "FILES.txt");
            for (String item :
                    new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (item.isBlank()) continue;
                Path file = target.resolve(item).normalize();
                if (!file.startsWith(target) || Path.of(item).isAbsolute())
                    throw new IOException("Invalid original resource path: " + item);
                Files.createDirectories(file.getParent());
                try (InputStream input =
                        PrintedBeamOriginalResources.class.getResourceAsStream(prefix + item)) {
                    if (input == null) throw new FileNotFoundException(prefix + item);
                    Files.copy(input, file);
                }
            }
        }
        return target;
    }
}
