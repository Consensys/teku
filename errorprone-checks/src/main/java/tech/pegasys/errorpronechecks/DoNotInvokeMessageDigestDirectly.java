/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.errorpronechecks;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers.MethodNameMatcher;
import com.sun.source.tree.MethodInvocationTree;
import java.security.MessageDigest;

@AutoService(BugChecker.class)
@BugPattern(
    name = "DoNotInvokeMessageDigestDirectly",
    summary = "Do not invoke MessageDigest.getInstance directly.",
    severity = WARNING)
public class DoNotInvokeMessageDigestDirectly extends BugChecker
    implements MethodInvocationTreeMatcher {

  private static final MethodNameMatcher GET_INSTANCE_MATCHER =
      Matchers.staticMethod().onClass(MessageDigest.class.getName()).named("getInstance");

  @Override
  public Description matchMethodInvocation(
      final MethodInvocationTree tree, final VisitorState state) {
    if (GET_INSTANCE_MATCHER.matches(tree, state)) {
      return describeMatch(tree);
    }
    return Description.NO_MATCH;
  }
}
