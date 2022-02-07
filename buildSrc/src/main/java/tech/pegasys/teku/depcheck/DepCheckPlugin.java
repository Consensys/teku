/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.depcheck;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.provider.SetProperty;
import org.gradle.tooling.BuildException;

public class DepCheckPlugin implements Plugin<Project> {

  @Override
  public void apply(final Project project) {
    project.getExtensions().create("dependencyRules", DependencyRules.class);
    project.task("checkModuleDependencies").doLast(task -> checkDependencies(project));
  }

  private void checkDependencies(final Project project) {
    final Set<String> illegalDependencies =
        project.getExtensions().getByType(DependencyRules.class).getRules().stream()
            .filter(rule -> isApplicable(rule, project))
            .flatMap(rule -> illegalDependencies(rule, project))
            .map(dependency -> dependency.getDependencyProject().getPath())
            .collect(toSet());
    if (!illegalDependencies.isEmpty()) {
      throw new BuildException(
          String.format(
              "Found illegal dependencies in %s\n%s",
              project.getDisplayName(), String.join("\n", illegalDependencies)),
          null);
    }
  }

  private Stream<ProjectDependency> illegalDependencies(final Rule rule, final Project project) {
    return project.getConfigurations().stream()
        .flatMap(config -> config.getAllDependencies().stream())
        .filter(dep -> dep instanceof ProjectDependency)
        .map(dep -> (ProjectDependency) dep)
        .filter(dep -> isIllegal(rule, dep));
  }

  private boolean isIllegal(final Rule rule, final ProjectDependency dependency) {
    return rule.getAllowed().get().stream()
        .noneMatch(allowed -> dependency.getDependencyProject().getPath().startsWith(allowed));
  }

  private boolean isApplicable(final Rule rule, final Project project) {
    return project.getPath().startsWith(rule.getName());
  }

  public interface DependencyRules {
    NamedDomainObjectContainer<Rule> getRules();
  }

  public interface Rule {
    String getName();

    SetProperty<String> getAllowed();
  }
}
