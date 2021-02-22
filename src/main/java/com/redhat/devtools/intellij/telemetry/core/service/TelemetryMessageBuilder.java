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

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.intellij.telemetry.core.ITelemetryService;
import com.redhat.devtools.intellij.telemetry.core.util.Lazy;
import com.redhat.devtools.intellij.telemetry.core.util.TimeUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.redhat.devtools.intellij.telemetry.core.service.TelemetryService.Type;
import static com.redhat.devtools.intellij.telemetry.core.service.TelemetryService.Type.ACTION;
import static com.redhat.devtools.intellij.telemetry.core.service.TelemetryService.Type.SHUTDOWN;
import static com.redhat.devtools.intellij.telemetry.core.service.TelemetryService.Type.STARTUP;
import static com.redhat.devtools.intellij.telemetry.core.util.AnonymizeUtils.anonymize;
import static com.redhat.devtools.intellij.telemetry.core.util.TimeUtils.toLocalTime;

public class TelemetryMessageBuilder {

    private final ServiceFacade service;

    public TelemetryMessageBuilder(ClassLoader classLoader) {
        this(new ServiceFacade(classLoader));
    }

    TelemetryMessageBuilder(ServiceFacade serviceFacade) {
        this.service = serviceFacade;
    }

    public ActionMessage actionPerformed(String name) {
        return new ActionMessage(ACTION, name, service);
    }

    public static class StartupMessage extends Message<StartupMessage> {

        private final LocalTime time;

        private StartupMessage(ServiceFacade service) {
            this(LocalTime.now(), service);
        }

        private StartupMessage(LocalTime time, ServiceFacade service) {
            super(STARTUP, "startup", service);
            this.time = time;
        }

        public LocalTime getTime() {
            return time;
        }
    }

    public static class ShutdownMessage extends Message<ShutdownMessage> {

        private static final String PROP_SESSION_DURATION = "session_duration";

        private ShutdownMessage(ServiceFacade service) {
            this(toLocalTime(ApplicationManager.getApplication().getStartTime()), service);
        }

        private ShutdownMessage(LocalTime startupTime, ServiceFacade service) {
            this(startupTime, LocalTime.now(), service);
        }

        private ShutdownMessage(LocalTime startupTime, LocalTime shutdownTime, ServiceFacade service) {
            super(SHUTDOWN, "shutdown", service);
            sessionDuration(startupTime, shutdownTime);
        }

        public ShutdownMessage sessionDuration(LocalTime startupTime, LocalTime shutdownTime) {
            return sessionDuration(Duration.between(startupTime, shutdownTime));
        }

        public ShutdownMessage sessionDuration(Duration duration) {
            return property(PROP_SESSION_DURATION, TimeUtils.toString(duration));
        }
    }

    public static class ActionMessage extends Message<ActionMessage> {

        private static final String PROP_DURATION = "duration";
        private static final String PROP_ERROR = "error";
        private static final String PROP_RESULT = "result";

        private LocalTime startTime;

        private ActionMessage(Type type, String name, ServiceFacade service) {
            super(type, name, service);
            started(LocalTime.now());
        }

        public ActionMessage started(LocalTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public ActionMessage finished() {
            return duration(Duration.between(startTime, LocalTime.now()));
        }

        public ActionMessage duration(Duration duration) {
            return property(PROP_DURATION, TimeUtils.toString(duration));
        }

        public ActionMessage success() {
            return success("success");
        }

        public ActionMessage success(String message) {
            return property(PROP_RESULT, message);
        }

        public ActionMessage error(String message) {
            return property(PROP_ERROR, anonymize(message));
        }

        public ActionMessage error(Exception exception) {
            return error(exception.getMessage());
        }
    }

    private abstract static class Message<T extends Message<?>> {

        private final Type type;
        private final Map<String, String> properties = new HashMap<>();
        private final String name;
        private final ServiceFacade service;

        private Message(Type type, String name, ServiceFacade service) {
            this.name = name;
            this.type = type;
            this.service = service;
        }

        public T property(String key, String value) {
            properties.put(key, value);
            return (T) this;
        }

        public void send() {
            service.send(new TelemetryEvent(type, name, properties));
        }

    }

    private static final class ServiceFacade {
        private final ClassLoader classLoader;
        private ITelemetryService service = null;

        private ServiceFacade(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public void send(final TelemetryEvent event) {
            if (service == null) {
                this.service = createService(classLoader);
                sendStartup();
                onShutdown();
            }
            service.send(event);
        }

        private ITelemetryService createService(ClassLoader classLoader) {
            TelemetryServiceFactory factory = ServiceManager.getService(TelemetryServiceFactory.class);
            return factory.create(classLoader);
        }

        private void sendStartup() {
            new StartupMessage(this).send();
        }

        private void onShutdown() {
            onShutdown(ApplicationManager.getApplication().getMessageBus().connect());
        }

        private void onShutdown(MessageBusConnection connection) {
            connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
                @Override
                public void appWillBeClosed(boolean isRestart) {
                    new ShutdownMessage(ServiceFacade.this).send();
                }
            });
        }
    }
}
