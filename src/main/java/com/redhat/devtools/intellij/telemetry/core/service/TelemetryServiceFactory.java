/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.telemetry.core.service;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.redhat.devtools.intellij.telemetry.core.IMessageBroker;
import com.redhat.devtools.intellij.telemetry.core.configuration.TelemetryConfiguration;
import com.redhat.devtools.intellij.telemetry.core.service.segment.SegmentBroker;
import com.redhat.devtools.intellij.telemetry.core.service.segment.SegmentConfiguration;

public class TelemetryServiceFactory {

    private final TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.INSTANCE;
    private final Environment.Builder builder = new Environment.Builder().application(
            ApplicationNamesInfo.getInstance(),
            ApplicationInfo.getInstance());

    public TelemetryService create(ClassLoader classLoader) {
        Environment environment = builder.plugin(classLoader).build();
        IMessageBroker broker = createSegmentBroker(classLoader, environment);
        return new TelemetryService(telemetryConfiguration, broker);
    }

    private IMessageBroker createSegmentBroker(ClassLoader classLoader, Environment environment) {
        SegmentConfiguration brokerConfiguration = new SegmentConfiguration(classLoader);
        return new SegmentBroker(
                telemetryConfiguration.isDebug(),
                AnonymousId.INSTANCE.get(),
                environment,
                brokerConfiguration);
    }

}