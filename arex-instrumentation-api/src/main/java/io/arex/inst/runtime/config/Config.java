package io.arex.inst.runtime.config;

import io.arex.inst.runtime.context.RecordLimiter;
import io.arex.inst.runtime.model.DynamicClassEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Config {

    private static Config INSTANCE = null;
    private final Map<String, DynamicClassEntity> dynamicEntityMap = new HashMap<>();

    static void update(boolean enableDebug, String serviceName, List<DynamicClassEntity> entities,
        Map<String, String> properties, Set<String> excludeServiceOperations,
        int dubboStreamReplayThreshold, int recordRate) {
        INSTANCE = new Config(enableDebug, serviceName, entities, properties,
            excludeServiceOperations,
            dubboStreamReplayThreshold, recordRate);
    }

    public static Config get() {
        return INSTANCE;
    }

    private final boolean enableDebug;
    private final String serviceName;
    private final List<DynamicClassEntity> entities;
    private Map<String, String> properties;
    private Set<String> excludeServiceOperations;
    private final int dubboStreamReplayThreshold;
    private int recordRate;
    private String recordVersion;

    Config(boolean enableDebug, String serviceName, List<DynamicClassEntity> entities,
        Map<String, String> properties,
        Set<String> excludeServiceOperations, int dubboStreamReplayThreshold, int recordRate) {
        this.enableDebug = enableDebug;
        this.serviceName = serviceName;
        this.entities = entities;
        this.properties = properties;
        this.excludeServiceOperations = excludeServiceOperations;
        this.dubboStreamReplayThreshold = dubboStreamReplayThreshold;
        this.recordRate = recordRate;
        this.recordVersion = properties.get("arex.agent.version");
        buildDynamicEntityMap();
    }

    private void buildDynamicEntityMap() {
        if (entities == null) {
            return;
        }
        for (DynamicClassEntity entity : entities) {
            dynamicEntityMap.putIfAbsent(entity.getSignature(), entity);
        }
    }

    public String getRecordVersion() {
        return recordVersion;
    }

    public DynamicClassEntity getDynamicEntity(String methodSignature) {
        return dynamicEntityMap.get(methodSignature);
    }

    public Map<String, DynamicClassEntity> getDynamicEntityMap() {
        return dynamicEntityMap;
    }

    public boolean isEnableDebug() {
        return this.enableDebug;
    }

    public List<DynamicClassEntity> dynamicClassEntities() {
        return this.entities;
    }

    public Set<String> excludeServiceOperations() {
        return this.excludeServiceOperations;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getString(String name) {
        return getRawProperty(name, null);
    }

    public String getString(String name, String defaultValue) {
        return getRawProperty(name, defaultValue);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        return safeGetTypedProperty(name, Boolean::parseBoolean, defaultValue);
    }

    public int getInt(String name, int defaultValue) {
        return safeGetTypedProperty(name, Integer::parseInt, defaultValue);
    }

    public long getLong(String name, long defaultValue) {
        return safeGetTypedProperty(name, Long::parseLong, defaultValue);
    }

    public double getDouble(String name, double defaultValue) {
        return safeGetTypedProperty(name, Double::parseDouble, defaultValue);
    }

    private <T> T safeGetTypedProperty(String name, Function<String, T> parser, T defaultValue) {
        try {
            T value = getTypedProperty(name, parser);
            return value == null ? defaultValue : value;
        } catch (RuntimeException t) {
            return defaultValue;
        }
    }

    private <T> T getTypedProperty(String name, Function<String, T> parser) {
        String value = getRawProperty(name, null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return parser.apply(value);
    }

    private String getRawProperty(String name, String defaultValue) {
        return this.properties.getOrDefault(name, defaultValue);
    }

    public int getDubboStreamReplayThreshold() {
        return dubboStreamReplayThreshold;
    }

    public int getRecordRate() {
        return recordRate;
    }

    /**
     * Conditions for determining invalid recording configuration(debug mode don't judge):<br/>
     * 1. rate <= 0 <br/>
     * 2. not in working time <br/>
     * 3. exceed rate limit <br/>
     * 4. local IP match target IP <br/>
     *
     * @return true: invalid, false: valid
     */
    public boolean invalidRecord(String path) {
        if (isEnableDebug()) {
            return false;
        }
        if (getRecordRate() <= 0) {
            return true;
        }
        if (!getBoolean("arex.during.work", false)) {
            return true;
        }
        if (!getBoolean("arex.ip.validate", false)) {
            return true;
        }

        return !RecordLimiter.acquire(path);
    }
}
