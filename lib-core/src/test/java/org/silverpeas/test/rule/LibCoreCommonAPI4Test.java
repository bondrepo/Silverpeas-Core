package org.silverpeas.test.rule;

import org.junit.runner.Description;
import org.silverpeas.cache.service.CacheServiceProvider;
import org.silverpeas.util.ComponentHelper;

/**
 * @author Yohann Chastagnier
 */
public class LibCoreCommonAPI4Test extends CommonAPI4Test {

  @Override
  protected void beforeEvaluate(final TestContext context) {
    super.beforeEvaluate(context);
    clearCacheData();
  }

  private void clearCacheData() {
    CacheServiceProvider.getRequestCacheService().clear();
    CacheServiceProvider.getThreadCacheService().clear();
  }
}