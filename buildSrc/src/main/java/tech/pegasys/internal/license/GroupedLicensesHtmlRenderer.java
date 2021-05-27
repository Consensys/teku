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
package tech.pegasys.internal.license;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.github.jk1.license.License;
import com.github.jk1.license.LicenseReportExtension;
import com.github.jk1.license.ManifestData;
import com.github.jk1.license.ModuleData;
import com.github.jk1.license.PomData;
import com.github.jk1.license.ProjectData;
import com.github.jk1.license.render.ReportRenderer;
import com.github.jk1.license.util.Files;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.internal.Pair;

/**
 * Generate HTML report grouped by licenses. Allows to specify a license override file for "Unknown"
 * license. Allows to specify a multiple license selection file for dependencies which have multiple
 * licenses available.
 */
public class GroupedLicensesHtmlRenderer implements ReportRenderer {
  private static final String OVERRIDE_PROJECT_URL = "projectUrl";
  private static final String OVERRIDE_LICENSE = "license";
  private static final String OVERRIDE_LICENSE_URL = "licenseUrl";
  private String fileName;
  private String projectName;
  private String projectVersion;
  private File output;
  private int counter;
  private int licenseCounter;
  private final Map<String, Map<String, String>> unknownOverridesLicenses;
  private final Map<String, String> multiLicenses;

  public GroupedLicensesHtmlRenderer(final Map<String, Object> params) {
    this(
        (String) params.getOrDefault("fileName", "index.html"),
        (String) params.getOrDefault("projectName", null),
        (File) params.getOrDefault("licenseOverrideFile", null),
        (File) params.getOrDefault("multiLicensesFile", null));
  }

  public GroupedLicensesHtmlRenderer(
      final String fileName,
      final String projectName,
      final File licenseOverrideFile,
      final File multiLicensesFile) {
    this.fileName = fileName;
    this.projectName = projectName;
    if (licenseOverrideFile != null && licenseOverrideFile.exists()) {
      unknownOverridesLicenses = parseUnknownOverrides(licenseOverrideFile);
    } else {
      unknownOverridesLicenses = Collections.emptyMap();
    }

    if (multiLicensesFile != null && multiLicensesFile.exists()) {
      multiLicenses = parseMultiLicensesFile(multiLicensesFile);
    } else {
      multiLicenses = Collections.emptyMap();
    }
  }

  @Input
  public String getFileNameCache() {
    return this.fileName;
  }

  @Override
  public void render(final ProjectData data) {
    Project project = data.getProject();
    projectName = projectName == null ? project.getName() : projectName;
    projectVersion =
        !"unspecified".equalsIgnoreCase(project.getVersion().toString())
            ? project.getVersion().toString()
            : "";
    fileName = fileName == null ? "index.html" : fileName;
    LicenseReportExtension config =
        project.getExtensions().findByType(LicenseReportExtension.class);
    if (config == null) {
      throw new GradleException("LicenseReportExtension is not available");
    }
    output = new File(config.outputDir, fileName);

    final Map<ModuleData, Set<Pair<String, String>>> moduleLicenses = buildModuleLicenses(data);
    final Map<String, List<ModuleData>> licenseGroup = buildLicenseGroup(moduleLicenses);

    writeReport(licenseGroup, moduleLicenses);
  }

  private Map<ModuleData, Set<Pair<String, String>>> buildModuleLicenses(final ProjectData data) {
    Map<ModuleData, Set<Pair<String, String>>> moduleLicences = new TreeMap<>();
    // go through all dependencies
    data.getAllDependencies()
        .forEach(
            moduleData -> {
              // check manifest and/or pom
              for (ManifestData manifestData : moduleData.getManifests()) {
                if (manifestData.getLicense() != null) {
                  final String licenseName = getLicenseName(manifestData.getLicense(), moduleData);
                  final String licenseUrl;
                  if (Files.maybeLicenseUrl(manifestData.getLicenseUrl())) {
                    licenseUrl =
                        hrefLink(manifestData.getLicenseUrl(), manifestData.getLicenseUrl());
                  } else if (manifestData.isHasPackagedLicense()) {
                    licenseUrl = hrefLink(manifestData.getUrl(), manifestData.getUrl());
                  } else {
                    licenseUrl = "<code>(Not Packaged)</code>";
                  }

                  moduleLicences
                      .computeIfAbsent(moduleData, k -> new HashSet<>())
                      .add(Pair.of(licenseName, licenseUrl));
                }
              }

              for (PomData pomData : moduleData.getPoms()) {
                // pom can contain multiple licenses
                for (License license : pomData.getLicenses()) {
                  final String licenseName = getLicenseName(license.getName(), moduleData);
                  final String licenseUrl;

                  if (license.getUrl() != null) {
                    licenseUrl =
                        Files.maybeLicenseUrl(license.getUrl())
                            ? hrefLink(license.getUrl(), license.getUrl())
                            : license.getUrl();
                  } else {
                    licenseUrl = "<code>(Not Available)</code>";
                  }

                  moduleLicences
                      .computeIfAbsent(moduleData, k -> new HashSet<>())
                      .add(Pair.of(licenseName, licenseUrl));
                }
              }

              // in case no license obtained from manifest or POM
              if (!moduleLicences.containsKey(moduleData)) {
                final Set<Pair<String, String>> unknownLicenses = new HashSet<>();
                // see if we have bundled license:
                if (!moduleData.getLicenseFiles().isEmpty()) {
                  final Set<Pair<String, String>> bundled =
                      moduleData.getLicenseFiles().stream()
                          .flatMap(licenseFileData -> licenseFileData.getFileDetails().stream())
                          .map(
                              licenseFileDetails -> {
                                final String licenseName =
                                    getLicenseName(
                                        licenseFileDetails.getLicense() == null
                                            ? "Bundled"
                                            : licenseFileDetails.getLicense(),
                                        moduleData);
                                final String licenseUrl;
                                if (licenseFileDetails.getLicenseUrl() == null) {
                                  licenseUrl =
                                      hrefLink(
                                          licenseFileDetails.getFile(),
                                          licenseFileDetails.getFile());
                                } else {
                                  licenseUrl = licenseFileDetails.getLicenseUrl();
                                }
                                return Pair.of(licenseName, licenseUrl);
                              })
                          .collect(Collectors.toSet());

                  unknownLicenses.addAll(bundled);
                } else {
                  unknownLicenses.add(
                      Pair.of(getLicenseName("Unknown", moduleData), "Link Not Available"));
                }

                moduleLicences
                    .computeIfAbsent(moduleData, k -> new HashSet<>())
                    .addAll(unknownLicenses);
              }
            });

    // select which license to use - if supplied
    final StringBuilder multipleLicenseError = new StringBuilder();
    moduleLicences.forEach(
        (module, licensePairs) -> {
          if (licensePairs.size() > 1) {
            final String licenseToUse = multiLicenses.get(module.getGroup());
            if (licenseToUse != null) {
              licensePairs.removeIf(pair -> !Objects.equals(pair.left, licenseToUse));
            }
          }

          if (licensePairs.size() > 1) {
            final Set<String> licenses =
                licensePairs.stream().map(p -> p.left).collect(Collectors.toSet());
            multipleLicenseError.append(
                String.format(
                    "Module %s has multiple licenses: %s. Please update multi-license file to specify which license to use.%n",
                    getModuleKey(module), licenses));
          }
        });

    if (multipleLicenseError.length() > 0) {
      throw new GradleException(multipleLicenseError.toString());
    }
    return moduleLicences;
  }

  protected Map<String, List<ModuleData>> buildLicenseGroup(
      final Map<ModuleData, Set<Pair<String, String>>> moduleLicenses) {
    Map<String, List<ModuleData>> licenseGroup = new TreeMap<>();
    moduleLicenses.forEach(
        (module, licenses) ->
            licenses.forEach(
                license ->
                    licenseGroup
                        .computeIfAbsent(license.left, k -> new ArrayList<>())
                        .add(module)));
    return licenseGroup;
  }

  private String getLicenseName(final String licenseName, final ModuleData moduleData) {
    final String moduleKey = getModuleKey(moduleData);
    if (unknownOverridesLicenses.containsKey(moduleKey)) {
      final Map<String, String> overriddenLicenses = unknownOverridesLicenses.get(moduleKey);
      return overriddenLicenses.getOrDefault(OVERRIDE_LICENSE, licenseName);
    }
    return licenseName;
  }

  private String getModuleKey(final ModuleData moduleData) {
    return String.format(
        "%s:%s:%s", moduleData.getGroup(), moduleData.getName(), moduleData.getVersion());
  }

  private Map<String, Map<String, String>> parseUnknownOverrides(
      final File unknownOverridesFileName) {
    final Map<String, Map<String, String>> unknownOverridesLicenses = new TreeMap<>();
    try {
      final List<String> lines =
          java.nio.file.Files.readAllLines(
              unknownOverridesFileName.toPath(), StandardCharsets.UTF_8);
      lines.forEach(
          line -> {
            String[] columns = line.split("\\|");
            if (columns.length > 0) {
              final String groupName = columns[0];
              final Map<String, String> overrideMap = new HashMap<>();
              overrideMap.put(OVERRIDE_PROJECT_URL, columns.length > 1 ? columns[1] : null);
              overrideMap.put(OVERRIDE_LICENSE, columns.length > 2 ? columns[2] : null);
              overrideMap.put(OVERRIDE_LICENSE_URL, columns.length > 3 ? columns[3] : null);

              unknownOverridesLicenses.put(groupName, overrideMap);
            }
          });
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return unknownOverridesLicenses;
  }

  private Map<String, String> parseMultiLicensesFile(final File file) {
    final Map<String, String> multipleLicenseMap = new TreeMap<>();
    try {
      final List<String> lines =
          java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
      lines.forEach(
          line -> {
            String[] columns = line.split("\\|");
            if (columns.length > 0) {
              final String groupName = columns[0];
              final String licenseName = columns.length > 1 ? columns[1] : null;
              multipleLicenseMap.put(groupName, licenseName);
            }
          });
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return multipleLicenseMap;
  }

  private void writeReport(
      final Map<String, List<ModuleData>> licenseGroup,
      final Map<ModuleData, Set<Pair<String, String>>> moduleLicenses) {
    final StringBuilder html = new StringBuilder();
    html.append("<!doctype html>\n");
    html.append("<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n");
    html.append(
        String.format("<title>Dependency License Report For %s</title></head>\n", projectName));
    html.append("<body style=\"font-family: sans-serif\">\n");
    html.append(
        String.format(
            "<h1>Dependency License Report for %s %s</h1>%n", projectName, projectVersion));
    html.append(
        "<table style=\"width:100%;text-align:left;border-collapse:separate;background-color:LightGrey;\" border=\"0\">\n");
    html.append("<tbody>\n");

    licenseGroup.forEach(
        (license, moduleList) -> {
          html.append(
              String.format(
                  "<tr><td style=\"width: 98.0679%%; padding: 10px;\"><strong>%d.&nbsp;%s</strong><td></tr>",
                  ++licenseCounter, license));

          moduleList.forEach(
              module ->
                  html.append(
                      String.format(
                          "<tr><td style=\"width: 98.0679%%;border: 10px solid LightGrey; background-color:white;padding: 10px;\">%s<td></tr>",
                          buildModuleDependencyHtml(module, moduleLicenses))));
        });

    html.append("</tbody></table>\n");
    html.append(
        String.format(
            "<hr /><p>This report was generated at <em>%s</em>",
            DateTimeFormatter.ISO_INSTANT.format(Instant.now())));
    html.append("</body></html>");

    try {
      java.nio.file.Files.writeString(output.toPath(), html, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String buildModuleDependencyHtml(
      final ModuleData module, Map<ModuleData, Set<Pair<String, String>>> moduleLicenses) {
    final StringBuilder html = new StringBuilder();
    html.append(
        String.format(
            "<p><strong>&nbsp;%d.&nbsp;Group:</strong>&nbsp;%s&nbsp;<strong>Name: </strong>%s <strong>Version:</strong> %s</strong></p>",
            ++counter, module.getGroup(), module.getName(), module.getVersion()));

    if (!moduleLicenses.containsKey(module) || moduleLicenses.get(module).isEmpty()) {
      html.append("<p><strong>No License information available</strong></p>");
      return html.toString();
    }

    html.append(getProjectUrl(module));
    html.append(getLicenseUrl(module, moduleLicenses));
    html.append(getBundledFiles(module));

    return html.toString();
  }

  private String getProjectUrl(final ModuleData module) {
    // first check if we have the projectURL in override file
    if (unknownOverridesLicenses.containsKey(getModuleKey(module))) {
      final Map<String, String> licenseOverride =
          unknownOverridesLicenses.get(getModuleKey(module));
      final String projectUrl = licenseOverride.get(OVERRIDE_PROJECT_URL);
      if (projectUrl != null && !projectUrl.trim().isEmpty()) {
        return String.format(
            "<p><strong>Project URL: </strong><br /><code><a href=\"%1$s\">%1$s</a></code></p>%n",
            projectUrl);
      }
    }

    // project url from manifest file
    final Optional<String> manifestProjectUrl =
        module.getManifests().stream().findFirst().map(ManifestData::getUrl);
    if (manifestProjectUrl.isPresent() && !manifestProjectUrl.get().trim().isEmpty()) {
      return String.format(
          "<p><strong>Project URL: </strong><code><br /><a href=\"%1$s\">%1$s</a></code></p>%n",
          manifestProjectUrl.get());
    }

    // project url from POM file
    final Optional<String> pomProjectUrl =
        module.getPoms().stream().findFirst().map(PomData::getProjectUrl);
    if (pomProjectUrl.isPresent() && !pomProjectUrl.get().trim().isEmpty()) {
      return String.format(
          "<p><strong>Project URL: </strong><code><br /><a href=\"%1$s\">%1$s</a></code></p>%n",
          pomProjectUrl.get());
    }
    return String.format("<p><strong>Project URL: </strong><br /><code>Not Available</code></p>%n");
  }

  private String getLicenseUrl(
      final ModuleData module, final Map<ModuleData, Set<Pair<String, String>>> moduleLicenses) {
    // first check if we have module in override file
    if (unknownOverridesLicenses.containsKey(getModuleKey(module))) {
      final Map<String, String> licenseOverride =
          unknownOverridesLicenses.get(getModuleKey(module));
      final String license = licenseOverride.get(OVERRIDE_LICENSE);
      final String licenseUrl = licenseOverride.get(OVERRIDE_LICENSE_URL);
      if (license != null && !license.trim().isEmpty()) {
        return String.format(
            "<p><strong>License:</strong><br /><code>%s - <a href=\"%2$s\">%2$s</a></code></p>%n",
            license, licenseUrl);
      }
    }

    final Set<Pair<String, String>> pairs = moduleLicenses.get(module);
    return pairs.stream()
        .map(
            pair ->
                String.format(
                    "<p><strong>License:</strong><br /><code>%s - %s</code></p>%n",
                    pair.left, pair.right))
        .collect(Collectors.joining("<br>"));
  }

  private String getBundledFiles(final ModuleData module) {
    if (!module.getLicenseFiles().isEmpty()) {
      final String links =
          module.getLicenseFiles().stream()
              .flatMap(licenseFileData -> licenseFileData.getFileDetails().stream())
              .map(
                  licenseFileDetails ->
                      hrefLink(licenseFileDetails.getFile(), licenseFileDetails.getFile()))
              .distinct()
              .collect(Collectors.joining("<br>"));
      return String.format("<p><strong>Bundled Licenses:</strong><br />%s</p>%n", links);
    }
    return String.format(
        "<p><strong>Bundled Licenses:</strong><br /><code>%s</code></p>%n", "Not Available");
  }

  private String hrefLink(final String url, final String text) {
    return String.format("<code><a href=\"%s\">%s</a></code>", url, text);
  }
}
