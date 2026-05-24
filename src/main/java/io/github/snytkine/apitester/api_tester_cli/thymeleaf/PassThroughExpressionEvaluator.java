/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.thymeleaf;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.OGNLVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

/**
 * A Thymeleaf variable expression evaluator that gracefully handles evaluation failures by
 * returning the original expression string instead of throwing an exception.
 *
 * <p>By default, Thymeleaf's OGNL evaluator throws a {@link
 * org.thymeleaf.exceptions.TemplateInputException} when an expression cannot be resolved — for
 * example, when a required variable is absent from the template context. This evaluator wraps the
 * default {@link OGNLVariableExpressionEvaluator} and catches any such failure, substituting the
 * literal expression string (e.g. {@code ${cli.missingKey}}) in place of the resolved value.
 *
 * <p>This is particularly useful when processing YAML templates where some variables may be
 * optional or not yet known at processing time. Rather than aborting template rendering, the
 * unresolved placeholders are preserved in the output for later inspection or processing.
 *
 * <p>Register this evaluator by setting it on a {@link org.thymeleaf.standard.StandardDialect}
 * before adding the dialect to the {@link org.thymeleaf.TemplateEngine}:
 *
 * <pre>{@code
 * StandardDialect dialect = new StandardDialect();
 * dialect.setVariableExpressionEvaluator(new PassThroughExpressionEvaluator());
 * templateEngine.setDialect(dialect);
 * }</pre>
 */
public class PassThroughExpressionEvaluator implements IStandardVariableExpressionEvaluator {

  private static final OGNLVariableExpressionEvaluator DELEGATE =
      new OGNLVariableExpressionEvaluator(true);

  @Override
  public Object evaluate(
      IExpressionContext context,
      IStandardVariableExpression expression,
      StandardExpressionExecutionContext expContext) {
    try {
      return DELEGATE.evaluate(context, expression, expContext);
    } catch (Exception e) {
      return "${" + expression.getExpression() + "}";
    }
  }
}
