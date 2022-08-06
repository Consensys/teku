/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.restapi;

import io.javalin.plugin.rendering.JavalinRenderer;
import io.javalin.plugin.rendering.template.JavalinThymeleaf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/** ThymeLeaf templates configuration for Javalin */
public class ThymeleafConfigurator {
  public static void enableThymeleafTemplates(final String templatePath) {
    JavalinRenderer.register(JavalinThymeleaf.INSTANCE);
    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.addTemplateResolver(templateResolver(TemplateMode.HTML, templatePath, ".html"));
    JavalinThymeleaf.configure(templateEngine);
  }

  private static ITemplateResolver templateResolver(
      TemplateMode templateMode, String prefix, String suffix) {
    ClassLoaderTemplateResolver templateResolver =
        new ClassLoaderTemplateResolver(Thread.currentThread().getContextClassLoader());
    templateResolver.setTemplateMode(templateMode);
    templateResolver.setPrefix(prefix);
    templateResolver.setSuffix(suffix);
    templateResolver.setCharacterEncoding("UTF-8");
    return templateResolver;
  }
}
