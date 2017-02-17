/*******************************************************************************
 * Copyright (c) 2017 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.benchmarks;

import java.util.Collection;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationListener;
import org.eclipse.leshan.server.registration.RegistrationUpdate;

import com.sun.org.apache.xerces.internal.impl.xs.identity.Selector.Matcher;

public class TestBootstrapServer {
    static AtomicInteger nbUpdate = new AtomicInteger();
    static AtomicInteger nbDereg = new AtomicInteger();
    static AtomicInteger nbReg = new AtomicInteger();
    static AtomicBoolean count = new AtomicBoolean(false);
    static LeshanServer server;

    private static void startServer() {
        LeshanServerBuilder builder = new LeshanServerBuilder();
        NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.set(Keys.PROTOCOL_STAGE_THREAD_COUNT, 10);
        NetworkConfig.setStandard(networkConfig);

        server = builder.build();

        server.getRegistrationService().addListener(new RegistrationListener() {

            @Override
            public void updated(RegistrationUpdate update, Registration updatedRegistration,
                    Registration previousRegistration) {
                if (count.get())
                    nbUpdate.incrementAndGet();
            }

            @Override
            public void unregistered(Registration registration, Collection<Observation> observations, boolean expired) {
                if (count.get())
                    nbDereg.incrementAndGet();

            }

            @Override
            public void registered(Registration registration) {
                if (count.get())
                    nbReg.incrementAndGet();
            }
        });
        server.start();
    }

    private static void stopServer() {
        int i = 0;
        for (Iterator iterator = server.getRegistrationService().getAllRegistrations(); iterator.hasNext();) {
            iterator.next();
            i++;
        }
        System.out.println("nb reg restante:" + i);
        server.destroy();
    }

    static Logger logger;
    static Logger cflog;
    static {
        Logger globalLogger = Logger.getLogger("");
        globalLogger.setLevel(java.util.logging.Level.OFF);
        for (Handler handler : globalLogger.getHandlers()) {
            handler.setLevel(java.util.logging.Level.ALL);
        }

        cflog = Logger.getLogger("org.eclipse.californium");
        cflog.setLevel(java.util.logging.Level.INFO);
        for (Handler handler : cflog.getHandlers()) {
            handler.setLevel(java.util.logging.Level.ALL);
        }

        logger = Logger.getLogger(Matcher.class.getCanonicalName());
        logger.setLevel(java.util.logging.Level.FINER);
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(java.util.logging.Level.ALL);
        }

        // logger.setUseParentHandlers(true);
        // logger.setFilter(new Filter() {
        // @Override
        // public boolean isLoggable(LogRecord record) {
        // return record.getMessage().startsWith("Tracking");
        // }
        // });
        // try {
        // logger.addHandler(new FileHandler("log.log"));
        // } catch (SecurityException e) {
        // e.printStackTrace();
        // } catch (IOException e) {
        // e.printStackTrace();
        // }

    }

    public static void main(String[] args) {
        count.set(true);
        System.out.println("********************************************");
        System.out.println("Start counting ... ");
        startServer();
        // Change the location through the Console
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNext()) {
                try {
                    String command = scanner.next();
                    if (command.equalsIgnoreCase("start")) {
                        nbReg.set(0);
                        nbUpdate.set(0);
                        nbDereg.set(0);
                        count.set(true);
                        System.out.println("********************************************");
                        System.out.println("Start counting ... ");
                        startServer();
                    } else if (command.equalsIgnoreCase("stop")) {
                        System.out.println("nb update :" + nbUpdate);
                        System.out.println("nb reg :" + nbReg);
                        System.out.println("nb dereg : " + nbDereg);
                        nbReg.set(0);
                        nbUpdate.set(0);
                        nbDereg.set(0);
                        count.set(false);
                        Thread.sleep(500);
                        stopServer();
                        System.out.println("********************************************");

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
