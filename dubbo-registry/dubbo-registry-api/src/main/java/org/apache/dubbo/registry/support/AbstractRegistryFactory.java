/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AbstractRegistryFactory. (SPI, Singleton, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.RegistryFactory
 */
public abstract class AbstractRegistryFactory implements RegistryFactory {

    // Log output
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRegistryFactory.class);

    // The lock for the acquisition process of the registry
    private static final ReentrantLock LOCK = new ReentrantLock();

    /**
     * Registry 集合
     *
     * key：{@link URL#toServiceString()}
     */
    // Registry Collection Map<RegistryAddress, Registry>
    private static final Map<String, Registry> REGISTRIES = new ConcurrentHashMap<>();

    /**
     * Get all registries
     *
     * @return all registries
     */
    public static Collection<Registry> getRegistries() {
        return Collections.unmodifiableCollection(REGISTRIES.values());
    }

    /**
     * 销毁
     *
     * Close all created registries
     */
    // TODO: 2017/8/30 to move somewhere else better
    public static void destroyAll() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Close all registries " + getRegistries());
        }
        // Lock up the registry shutdown process
        LOCK.lock();
        try {
            for (Registry registry : getRegistries()) {
                try {
                    registry.destroy();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
            REGISTRIES.clear();
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }

    /**
     * 获得注册中心 Registry 对象
     *
     * @param url 注册中心地址，不允许为空
     * @return Registry 对象
     */
    @Override
    public Registry getRegistry(URL url) {
        // 修改 URL
        // + `path`
        url = url.setPath(RegistryService.class.getName())
                // + `parameters.interface`
                .addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
                // - `export`
                .removeParameters(Constants.EXPORT_KEY, Constants.REFER_KEY);
        // 计算 key
        String key = url.toServiceStringWithoutResolving();
        // 获得锁
        // Lock the registry access process to ensure a single instance of the registry
        LOCK.lock();
        try {
            // 从缓存中获得 Registry 对象
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            // 缓存不存在，进行创建 Registry 对象
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            // 添加到缓存
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // 释放锁
            // Release the lock
            LOCK.unlock();
        }
    }

    /**
     * 创建 Registry 对象
     *
     * @param url 注册中心地址
     * @return Registry 对象
     */
    protected abstract Registry createRegistry(URL url);

}
