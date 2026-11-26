package io.github.rayhuan.rpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the exported services: name -> instance. */
public final class ServiceRegistry {

    private final Map<String, Object> services = new ConcurrentHashMap<>();

    public ServiceRegistry export(String name, Object instance) {
        if (services.putIfAbsent(name, instance) != null) {
            throw new IllegalStateException("service already exported: " + name);
        }
        return this;
    }

    public Object lookup(String name) {
        return services.get(name);
    }

    public int size() {
        return services.size();
    }
}
