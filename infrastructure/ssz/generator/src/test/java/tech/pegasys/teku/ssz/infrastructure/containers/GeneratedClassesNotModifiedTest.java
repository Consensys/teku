/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ssz.infrastructure.containers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.ssz.ContainersGenerator;

public class GeneratedClassesNotModifiedTest {

  private static final String PROJECT_PROPERTY_NAME = "ssz.project.source.path";
  private static final Pattern oneLineCommentPattern =
      Pattern.compile("^[\\s]*//.*?$", Pattern.DOTALL | Pattern.MULTILINE);
  private static final Pattern multilineLineCommentPattern =
      Pattern.compile("/\\*(.|\\n)+?\\*/", Pattern.DOTALL | Pattern.MULTILINE);
  private static final Pattern spacesPattern =
      Pattern.compile("[\\n\\r\\s]+", Pattern.DOTALL | Pattern.MULTILINE);

  /**
   * Checks that files generated with {@link ContainersGenerator} are not accidentally changed
   * manually
   *
   * <p>Set system property 'ssz.project.source.path' to ssz project root path to run the test
   * manually
   */
  @Test
  @EnabledIfSystemProperty(named = PROJECT_PROPERTY_NAME, matches = ".*")
  void checkGeneratesSszClassesWasNotModified(@TempDir Path tmpDir) throws IOException {
    Path sszProjectSource = Path.of(System.getProperty(PROJECT_PROPERTY_NAME));
    Path committedSrcRoot = sszProjectSource.resolve(Path.of("src", "main", "java"));
    Path templatesSrcRoot = sszProjectSource.resolve(Path.of("generator", "src", "main", "java"));
    new ContainersGenerator(templatesSrcRoot, tmpDir).generateAll();

    assertThat(tmpDir).isDirectoryRecursivelyContaining("glob:**.java");

    List<Path> allFiles;
    try (Stream<Path> pathStream =
        Files.walk(tmpDir).filter(Files::isRegularFile).map(tmpDir::relativize)) {
      allFiles = pathStream.collect(Collectors.toList());
    }

    assertThat(allFiles).allMatch(p -> p.getFileName().toString().endsWith(".java"));

    for (Path srcFile : allFiles) {
      String committedSrc = Files.readString(committedSrcRoot.resolve(srcFile));
      String generatedSrc = Files.readString(tmpDir.resolve(srcFile));
      compareJavaSrc(committedSrc, generatedSrc);
    }
  }

  private void compareJavaSrc(String committedSrc, String generatedSrc) {
    String canonicalCommittedSrc = comparableSrc(committedSrc);
    String canonicalGeneratedSrc = comparableSrc(generatedSrc);
    if (!canonicalCommittedSrc.equals(canonicalGeneratedSrc)) {
      Assertions.fail(
          "Java sources don't match:\n"
              + "=============================================\n"
              + committedSrc
              + "=============================================\n"
              + generatedSrc);
    }
  }

  private String comparableSrc(String src) {
    return removeSpaces(removeComments(src));
  }

  private String removeSpaces(String src) {
    return spacesPattern.matcher(src).replaceAll("");
  }

  private String removeComments(String src) {
    String s1 = multilineLineCommentPattern.matcher(src).replaceAll("");
    return oneLineCommentPattern.matcher(s1).replaceAll("");
  }
}
