/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2021 ScyllaDB
 *
 * Modified by ScyllaDB
 */
package com.datastax.driver.osgi;

import static com.datastax.driver.osgi.BundleOptions.defaultOptions;
import static com.datastax.driver.osgi.BundleOptions.driverBundle;
import static com.datastax.driver.osgi.BundleOptions.dropwizardMetricsBundle;
import static com.datastax.driver.osgi.BundleOptions.extrasBundle;
import static com.datastax.driver.osgi.BundleOptions.guavaBundle;
import static com.datastax.driver.osgi.BundleOptions.mailboxBundle;
import static com.datastax.driver.osgi.BundleOptions.mappingBundle;
import static com.datastax.driver.osgi.BundleOptions.nettyBundles;
import static org.ops4j.pax.exam.CoreOptions.options;

import com.datastax.driver.osgi.api.MailboxException;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.testng.SkipException;
import org.testng.annotations.Test;

// @IntegrationTestDisabledPaxExamHttpsFailure
// @Listeners({CCMBridgeListener.class, PaxExam.class})
public class MailboxServiceGuava21IT extends MailboxServiceTests {

  @Configuration
  public Option[] guava21Config() {
    MavenArtifactProvisionOption guavaBundle = guavaBundle();
    String javaVersion = System.getProperty("java.version");
    // Only bring in 21.0 if java version >= 1.8.  If this is not done the framework
    // will fail to load for < 1.8 and we plan on skipping the test anyways.
    if (javaVersion.compareTo("1.8") >= 0) {
      guavaBundle = guavaBundle.version("21.0");
    }

    return options(
        defaultOptions(),
        nettyBundles(),
        dropwizardMetricsBundle(),
        guavaBundle,
        driverBundle(),
        extrasBundle(),
        mappingBundle(),
        mailboxBundle());
  }

  /**
   * Exercises a 'mailbox' service provided by an OSGi bundle that depends on the driver with Guava
   * 21 explicitly enforced.
   *
   * @test_category packaging
   * @expected_result Can create, retrieve and delete data using the mailbox service.
   * @jira_ticket JAVA-620
   * @since 2.0.10, 2.1.5
   */
  @Test(groups = "short", enabled = false /* @IntegrationTestDisabledPaxExamHttpsFailure */)
  public void test_guava_21() throws MailboxException {
    String javaVersion = System.getProperty("java.version");
    if (javaVersion.compareTo("1.8") < 0) {
      throw new SkipException("Guava 21 requires Java 1.8");
    }
    checkService();
  }
}
