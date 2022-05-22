/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.async;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.test.junit.ContextSelectorType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("async")
@ContextSelectorType(AsyncLoggerContextSelector.class)
public class AsyncWaitStrategyFactoryIncorrectConfigGlobalLoggersTest {

    @BeforeAll
    public static void beforeClass() {
        System.setProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY,
                "AsyncWaitStrategyIncorrectFactoryConfigGlobalLoggerTest.xml");
    }

    @AfterAll
    public static void afterClass() {
        System.clearProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY);
    }


    @Test
    public void testIncorrectConfigWaitStrategyFactory() throws Exception {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        assertTrue(context instanceof AsyncLoggerContext, "context is AsyncLoggerContext");

        AsyncWaitStrategyFactory asyncWaitStrategyFactory = context.getConfiguration().getAsyncWaitStrategyFactory();
        assertNull(asyncWaitStrategyFactory);

        AsyncLogger logger = (AsyncLogger) context.getRootLogger();
        AsyncLoggerDisruptor delegate = logger.getAsyncLoggerDisruptor();
        assertEquals(TimeoutBlockingWaitStrategy.class, delegate.getWaitStrategy().getClass());
        assertTrue(delegate.getWaitStrategy() instanceof TimeoutBlockingWaitStrategy,
                "waitstrategy is TimeoutBlockingWaitStrategy");
    }
}
