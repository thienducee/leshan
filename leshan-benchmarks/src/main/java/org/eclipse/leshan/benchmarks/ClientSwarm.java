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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.LwM2mId;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Device;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.DmServerInfo;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.response.ExecuteResponse;

public class ClientSwarm {

    private static final int NBCLIENT = 1000;

    private static final AtomicInteger nb_success = new AtomicInteger();
    private static final AtomicInteger nb_timeout = new AtomicInteger();
    private static final AtomicInteger nb_failure = new AtomicInteger();
    private static final AtomicInteger nb_internal = new AtomicInteger();
    private static CopyOnWriteArrayList<Long> success_times = new CopyOnWriteArrayList<>(); // in ms
    private static CopyOnWriteArrayList<Long> timeout_times = new CopyOnWriteArrayList<>(); // in ms
    private static CopyOnWriteArrayList<Long> failure_times = new CopyOnWriteArrayList<>(); // in ms
    private static CopyOnWriteArrayList<Long> internal_times = new CopyOnWriteArrayList<>(); // in ms

    static {
        // disable java.util.logging
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getGlobal();
        globalLogger.setLevel(java.util.logging.Level.OFF);
        Handler[] handlers = globalLogger.getHandlers();
        for (Handler handler : handlers) {
            globalLogger.removeHandler(handler);
        }
    }

    static Runnable runClient() {
        return new Runnable() {
            long start;

            @Override
            public void run() {
                try {
                    regAndDeg();
                } catch (Exception e) {
                    long end = System.currentTimeMillis();
                    nb_internal.getAndIncrement();
                    internal_times.add(end - start);
                    e.printStackTrace();
                }
            }

            private void regAndDeg() {
                // generate random identity
                String endpoint = UUID.randomUUID().toString();

                // Create objects Enabler
                ObjectsInitializer initializer = new ObjectsInitializer();
                initializer.setInstancesForObject(LwM2mId.SECURITY, Security.noSec("coap://localhost:5683", 12345));
                initializer.setInstancesForObject(LwM2mId.SERVER, new Server(12345, 36000, BindingMode.U, false));
                initializer.setInstancesForObject(LwM2mId.DEVICE,
                        new Device("Eclipse Leshan", "IT - TEST - 123", "12345", "U") {
                            @Override
                            public ExecuteResponse execute(int resourceid, String params) {
                                if (resourceid == 4) {
                                    return ExecuteResponse.success();
                                } else {
                                    return super.execute(resourceid, params);
                                }
                            }
                        });
                List<LwM2mObjectEnabler> objects = initializer.createMandatory();
                objects.add(initializer.create(2));

                // Build Client
                LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
                builder.setObjects(objects);
                builder.setNetworkConfig(NetworkConfig.getStandard());
                LeshanClient client = builder.build();

                // measurement stuff
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                client.addObserver(new LwM2mClientObserverAdapter() {
                    @Override
                    public void onRegistrationSuccess(DmServerInfo server, String registrationID) {
                        long end = System.currentTimeMillis();
                        nb_success.incrementAndGet();
                        success_times.add(end - start);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onRegistrationFailure(DmServerInfo server, ResponseCode responseCode,
                            String errorMessage) {
                        long end = System.currentTimeMillis();
                        nb_failure.incrementAndGet();
                        failure_times.add(end - start);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onRegistrationTimeout(DmServerInfo server) {
                        long end = System.currentTimeMillis();
                        nb_timeout.incrementAndGet();
                        timeout_times.add(end - start);
                        countDownLatch.countDown();
                    }
                });
                start = System.currentTimeMillis();
                client.start();
                try {
                    countDownLatch.await();
                    client.destroy(true);
                } catch (InterruptedException e) {
                    long end = System.currentTimeMillis();
                    nb_timeout.incrementAndGet();
                    timeout_times.add(end - start);
                    e.printStackTrace();
                }
            }
        };
    }

    public static void main(String[] args) {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(NBCLIENT);

        for (int i = 0; i < NBCLIENT; i++) {
            pool.submit(runClient());
        }

        try {
            pool.shutdown();
            pool.awaitTermination(60, TimeUnit.SECONDS);
            System.out.println("********************************************");
            printResult("success", nb_success.get(), success_times);
            printResult("failure", nb_failure.get(), failure_times);
            printResult("timeout", nb_timeout.get(), timeout_times);
            printResult("success", nb_internal.get(), internal_times);
            System.out.println("********************************************");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void printResult(String name, int nb, Collection<Long> times) {
        System.out.print(String.format("nb %s        : %d", name, nb));
        if (!times.isEmpty())
            System.out.print(String.format(" (max %dms, min %dms, avg %dms)", Collections.max(times),
                    Collections.min(times), average(times)));
        System.out.println();
    }

    private static long average(Collection<Long> c) {
        long sum = 0;
        for (Long s : c) {
            sum += s;
        }
        return sum / c.size();
    }
}
