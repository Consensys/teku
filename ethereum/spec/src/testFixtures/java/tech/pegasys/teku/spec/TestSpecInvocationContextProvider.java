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

package tech.pegasys.teku.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class TestSpecInvocationContextProvider implements TestTemplateInvocationContextProvider {

  @Override
  public boolean supportsTestTemplate(ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext extensionContext) {

    Class<?> clazz = extensionContext.getRequiredTestClass();
    TestSpecContext testSpecContext = clazz.getAnnotation(TestSpecContext.class);

    Set<SpecMilestone> milestones;
    Set<Eth2Network> networks;

    if (testSpecContext.allMilestones()) {
      milestones = Set.of(SpecMilestone.values());
    } else {
      milestones = Set.of(testSpecContext.milestone());
    }

    if (testSpecContext.allNetworks()) {
      networks = Set.of(Eth2Network.values());
    } else {
      networks = Set.of(testSpecContext.network());
    }

    List<TestTemplateInvocationContext> invocations = new ArrayList<>();

    milestones.forEach(
        specMilestone -> {
          networks.forEach(
              eth2Network -> {
                invocations.add(
                    generateContext(
                        new SpecContext(
                            specMilestone, eth2Network, testSpecContext.doNotGenerateSpec())));
              });
        });

    return invocations.stream();
  }

  private TestTemplateInvocationContext generateContext(SpecContext specContext) {
    return new TestTemplateInvocationContext() {
      @Override
      public String getDisplayName(int invocationIndex) {
        return specContext.getDisplayName();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(new SpecContextParameterResolver<>(specContext));
      }
    };
  }

  public static class SpecContext {
    private final String displayName;
    private final Spec spec;
    private final DataStructureUtil dataStructureUtil;

    private final SpecMilestone specMilestone;
    private final Eth2Network network;

    SpecContext(SpecMilestone specMilestone, Eth2Network network, boolean doNotGenerateSpec) {
      if (doNotGenerateSpec) {
        spec = null;
        dataStructureUtil = null;
      } else {
        this.spec = TestSpecFactory.create(specMilestone, network);
        this.dataStructureUtil = new DataStructureUtil(spec);
      }
      this.displayName = specMilestone.name() + ' ' + network.name();

      this.specMilestone = specMilestone;
      this.network = network;
    }

    public String getDisplayName() {
      return displayName;
    }

    public Spec getSpec() {
      return spec;
    }

    public DataStructureUtil getDataStructureUtil() {
      return dataStructureUtil;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public Eth2Network getNetwork() {
      return network;
    }

    public void assumeIsOneOf(SpecMilestone... milestones) {
      Assumptions.assumeTrue(List.of(milestones).contains(specMilestone), "Milestone skipped");
    }

    public void assumeIsOneOf(Eth2Network... networks) {
      Assumptions.assumeTrue(List.of(networks).contains(network), "Network skipped");
    }

    public void assumeIsNotOneOf(SpecMilestone... milestones) {
      Assumptions.assumeFalse(List.of(milestones).contains(specMilestone), "Milestone skipped");
    }

    public void assumeIsNotOneOf(Eth2Network... networks) {
      Assumptions.assumeFalse(List.of(networks).contains(network), "Network skipped");
    }

    public void assumeMilestoneActive(SpecMilestone milestone) {
      Assumptions.assumeTrue(specMilestone.isGreaterThanOrEqualTo(milestone), "Milestone skipped");
    }

    public void assumeMergeActive() {
      assumeMilestoneActive(SpecMilestone.MERGE);
    }

    public void assumeAltairActive() {
      assumeMilestoneActive(SpecMilestone.ALTAIR);
    }
  }

  public static class SpecContextParameterResolver<T> implements ParameterResolver {
    T data;

    public SpecContextParameterResolver(T data) {
      this.data = data;
    }

    @Override
    public boolean supportsParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return parameterContext.getParameter().getType().isInstance(data);
    }

    @Override
    public Object resolveParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return data;
    }
  }
}
