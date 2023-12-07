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

import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.intellij.telemetry.core.IService;
import com.redhat.devtools.intellij.telemetry.core.configuration.TelemetryConfiguration;
import com.redhat.devtools.intellij.telemetry.core.service.segment.SegmentBroker;
import com.redhat.devtools.intellij.telemetry.ui.TelemetryNotifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.VerificationModeFactory;

import java.util.List;

import static com.redhat.devtools.intellij.telemetry.core.service.Event.Type.USER;
import static com.redhat.devtools.intellij.telemetry.core.service.Fakes.telemetryConfiguration;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TelemetryServiceTest {

    private SegmentBroker broker;
    private MessageBusConnection bus;
    private IService service;
    private Event event;
    private TelemetryNotifications notifications;

    @BeforeEach
    void before() {
        this.broker = createSegmentBroker();
        this.bus = createMessageBusConnection();
        this.notifications = createTelemetryNotifications();
        TelemetryConfiguration configuration = telemetryConfiguration(true, true);
        this.service = new TelemetryService(configuration, broker, bus, notifications);
        this.event = new Event(null, "Testing Telemetry", null);
    }

    @Test
    void send_should_send_if_is_enabled() {
        // given
        // when
        service.send(event);
        // then
        verify(broker, atLeastOnce()).send(any(Event.class));
    }

    @Test
    void send_should_NOT_send_if_is_NOT_configured() {
        // given
        TelemetryConfiguration configuration = telemetryConfiguration(false, false);
        TelemetryService service = new TelemetryService(configuration, broker, bus, notifications);
        // when
        service.send(event);
        // then
        verify(broker, never()).send(any(Event.class));
    }

    @Test
    void send_should_send_all_events_once_it_gets_enabled() {
        // given
        TelemetryConfiguration configuration = telemetryConfiguration(false, false);
        TelemetryService service = new TelemetryService(configuration, broker, bus, notifications);
        // when config is disabled
        service.send(event);
        service.send(event);
        // then
        verify(broker, never()).send(any(Event.class));
        // when config gets enabled
        doReturn(true)
                .when(configuration).isEnabled();
        service.send(event);
        // then
        verify(broker, VerificationModeFactory.atLeast(3)).send(any(Event.class));
    }

    @Test
    void send_should_send_userinfo() {
        // given
        ArgumentCaptor<Event> eventArgument = ArgumentCaptor.forClass(Event.class);
        // when
        service.send(event);
        // then
        verify(broker, atLeastOnce()).send(eventArgument.capture());
        List<Event> allArguments = eventArgument.getAllValues();
        assertThat(allArguments.size()).isGreaterThanOrEqualTo(2);
        assertThat(allArguments.get(0).getType()).isEqualTo(USER);
    }

    @Test
    void send_should_query_user_consent_once() {
        // given
        TelemetryConfiguration configuration = telemetryConfiguration(true, false);
        TelemetryService service = new TelemetryService(configuration, broker, bus, notifications);
        // when
        service.send(event);
        service.send(event);
        service.send(event);
        // then
        verify(notifications).queryUserConsent();
    }

    @Test
    void send_should_NOT_query_user_consent_if_configured() {
        // given
        // when
        service.send(event);
        // then
        verify(notifications, never()).queryUserConsent();
    }

    private SegmentBroker createSegmentBroker() {
        return mock(SegmentBroker.class);
    }

    private MessageBusConnection createMessageBusConnection() {
        return mock(MessageBusConnection.class);
    }

    private TelemetryNotifications createTelemetryNotifications() {
        return mock(TelemetryNotifications.class);
    }

}
