package com.ispengya.hotkey.cli.cache;

/**
 * 热 key 值模型，包装热 key 标记和业务值。
 *
 * <p>Worker 推送热 key 时，创建 ValueModel，value 为 {@link #MAGIC_VALUE}。
 * 业务 set 时，更新 value 为真实值。</p>
 *
 * @author ispengya
 */
public class ValueModel {

    /**
     * 魔法值，用于标记热 key。
     * 业务 get 时，如果返回此值，应视为 null（未设置真实值）。
     */
    public static final Object MAGIC_VALUE = new Object();

    /**
     * 业务值
     */
    private Object value;

    /**
     * 创建时间（毫秒）
     */
    private long createTime;

    /**
     * 过期时长（毫秒）
     */
    private long duration;

    public ValueModel() {
    }

    public ValueModel(Object value, long durationMillis) {
        this.value = value;
        this.createTime = System.currentTimeMillis();
        this.duration = durationMillis;
    }

    /**
     * 创建热 key 默认值模型。
     *
     * @param durationSeconds 过期时长（秒）
     * @return ValueModel 实例
     */
    public static ValueModel defaultValue(int durationSeconds) {
        ValueModel model = new ValueModel();
        model.setValue(MAGIC_VALUE);
        model.setCreateTime(System.currentTimeMillis());
        model.setDuration(durationSeconds * 1000L);
        return model;
    }

    /**
     * 判断是否已过期。
     *
     * @return true 如果已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > createTime + duration;
    }

    /**
     * 判断是否临近过期（<2秒）。
     *
     * @return true 如果临近过期
     */
    public boolean isNearExpire() {
        long ttl = (createTime + duration) - System.currentTimeMillis();
        return ttl > 0 && ttl < 2000;
    }

    /**
     * 判断是否是热 key 标记（未 set 真实值）。
     *
     * @return true 如果是标记值
     */
    public boolean isMagicValue() {
        return value == MAGIC_VALUE;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
