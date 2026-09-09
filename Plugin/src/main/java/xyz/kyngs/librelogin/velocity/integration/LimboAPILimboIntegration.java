/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.integration;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.integration.LimboIntegration;

public class LimboAPILimboIntegration implements LimboIntegration<RegisteredServer> {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Object limboFactory;
    private final Map<String, Object> limboInstances = new ConcurrentHashMap<>();
    private final AtomicInteger portCounter = new AtomicInteger(30000);

    public LimboAPILimboIntegration(ProxyServer proxyServer, Logger logger, Object limboFactory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.limboFactory = limboFactory;
    }

    @Override
    public RegisteredServer createLimbo(String serverName) {
        try {
            Method createVirtualWorld = limboFactory.getClass().getMethod("createVirtualWorld",
                    Class.forName("net.elytrium.limboapi.api.Dimension"),
                    double.class, double.class, double.class,
                    float.class, float.class);
            Object dimension = getDimensionEnum("net.elytrium.limboapi.api.Dimension", "OVERWORLD");
            Object world = createVirtualWorld.invoke(limboFactory, dimension, 0.0, 64.0, 0.0, 0.0f, 0.0f);

            Method createLimbo = limboFactory.getClass().getMethod("createLimbo",
                    Class.forName("net.elytrium.limboapi.api.chunk.VirtualWorld"));
            Object limbo = createLimbo.invoke(limboFactory, world);

            setLimitMethod(limbo, "setGameMode", String.class, "SURVIVAL");
            setLimitMethod(limbo, "setShouldRespawn", boolean.class, false);
            setLimitMethod(limbo, "setViewDistance", int.class, 10);
            setLimitMethod(limbo, "setShouldRejoin", boolean.class, true);
            setLimitMethod(limbo, "setReducedDebugInfo", boolean.class, true);

            limboInstances.put(serverName, limbo);

            int port = portCounter.getAndAdd(1);
            if (port > 40000) portCounter.set(30000);
            SocketAddress address = new InetSocketAddress("127.0.0.1", port);
            ServerInfo serverInfo = new ServerInfo(serverName, (InetSocketAddress) address);
            RegisteredServer registeredServer = proxyServer.registerServer(serverInfo);

            logger.info("Created LimboAPI limbo server: " + serverName + " on port " + port);
            return registeredServer;
        } catch (Exception e) {
            logger.error("Failed to create LimboAPI limbo: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void spawnPlayer(Player player, String serverName) {
        Object limbo = limboInstances.get(serverName);
        if (limbo == null) {
            logger.warn("Limbo " + serverName + " not found");
            return;
        }

        try {
            Class<?> handlerClass = Class.forName("net.elytrium.limboapi.api.LimboSessionHandler");
            Object handler = Proxy.newProxyInstance(
                    handlerClass.getClassLoader(),
                    new Class<?>[] { handlerClass },
                    (proxy, method, args1) -> {
                        if (method.getName().equals("onDisconnect")) {
                            logger.info("Player " + player.getUsername() + " disconnected from limbo " + serverName);
                            proxyServer.getServer("lobby").ifPresent(server -> {
                                player.createConnectionRequest(server).connect().whenComplete((result, throwable) -> {
                                    if (throwable != null || !result.isSuccessful()) {
                                        player.disconnect(Component.text("Unable to connect to lobby"));
                                    }
                                });
                            });
                        }
                        return null;
                    }
            );

            Method spawnPlayer = limbo.getClass().getMethod("spawnPlayer",
                    Class.forName("com.velocitypowered.api.proxy.Player"),
                    handlerClass);
            spawnPlayer.invoke(limbo, player, handler);
            logger.info("Spawned player " + player.getUsername() + " into LimboAPI limbo " + serverName);
        } catch (Exception e) {
            logger.error("Failed to spawn player into limbo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasLimbo(String serverName) {
        return limboInstances.containsKey(serverName);
    }

    public Map<String, Object> getLimboInstances() {
        return (Map<String, Object>) limboInstances;
    }

    private void setLimitMethod(Object target, String methodName, Class<?> paramType, Object value) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = target.getClass().getMethod(methodName, paramType);
        method.invoke(target, value);
    }

    private Object getDimensionEnum(String className, String name) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = Class.forName(className);
        return clazz.getField(name).get(null);
    }
}
