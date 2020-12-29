/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContainersGenerator {

  private final int maxFields = 16;
  private final Path templateSrcPath;
  private final Path targetSrcPath;
  private final String typePackagePath = "tech/pegasys/teku/ssz/backing/containers/";
  private final String viewPackagePath = "tech/pegasys/teku/ssz/backing/containers/";
  private final String containerTypeTemplateFile = "ContainerTypeTemplate.java";
  private final String containerViewTemplateFile = "ContainerTemplate.java";

  public ContainersGenerator(Path modulePath) {
    templateSrcPath = modulePath.resolve("src/test/java");
    targetSrcPath = modulePath.resolve("src/main/java");
  }

  public static void main(String[] args) throws IOException {
    final String path;
    if (args.length == 0) {
      path = new File(".", "ssz").getCanonicalFile().getAbsolutePath();
    } else {
      path = args[0];
    }
    System.out.println("Generating in module path: " + path);
    new ContainersGenerator(Path.of(path)).generateAll();
    System.out.println("Done.");
  }

  public void generateAll() {
    for (int i = 1; i <= maxFields; i++) {
      generateContainerClasses(i);
    }
  }

  public void generateContainerClasses(int fieldsCount) {
    String typeClassName = "ContainerType" + fieldsCount;
    String viewClassName = "Container" + fieldsCount;
    Map<String, String> vars =
        Map.ofEntries(
            Map.entry("TypeClassName", typeClassName),
            Map.entry("ViewClassName", viewClassName),
            Map.entry(
                "ViewTypes",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "V" + i + " extends ViewRead")
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewTypeNames",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "V" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "FieldsDeclarations",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "ViewType<V" + i + "> fieldType" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "Fields",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "fieldType" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewParams",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "V" + i + " arg" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "ViewArgs",
                IntStream.range(0, fieldsCount)
                    .mapToObj(i -> "arg" + i)
                    .collect(Collectors.joining(", "))),
            Map.entry(
                "Getters",
                IntStream.range(0, fieldsCount)
                    .mapToObj(
                        i ->
                            (""
                                    + "protected V$ getField$() {\n"
                                    + "    return getAny($);\n"
                                    + "  }")
                                .replace("$", "" + i))
                    .collect(Collectors.joining("\n\n"))));
    generateFromTemplate(
        templateSrcPath.resolve(typePackagePath).resolve(containerTypeTemplateFile),
        targetSrcPath.resolve(typePackagePath).resolve(typeClassName + ".java"),
        vars);

    generateFromTemplate(
        templateSrcPath.resolve(viewPackagePath).resolve(containerViewTemplateFile),
        targetSrcPath.resolve(viewPackagePath).resolve(viewClassName + ".java"),
        vars);
  }

  public void generateFromTemplate(Path templateSrc, Path destSrc, Map<String, String> varToVal) {
    try {
      String src = Files.readString(templateSrc);
      String res = replacePlaceholders(src, varToVal);
      Files.writeString(destSrc, res);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String replacePlaceholders(String src, Map<String, String> varToVal) {
    String res = src;
    for (String var : varToVal.keySet()) {
      res = res.replaceAll("/\\*\\$\\$" + var + "\\*/[^$]+/\\*\\$\\$\\*/", varToVal.get(var));
    }
    if (res.contains("/*$$")) {
      throw new RuntimeException("Non substituted placeholders found: " + res);
    }
    return res;
  }
}
