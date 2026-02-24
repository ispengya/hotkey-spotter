package com.ispengya.hotkey.remoting.protocol;

/**
 * Serializer 定义 remoting 层使用的序列化抽象。
 *
 * <p>具体实现可以基于 JSON、Hessian、Kryo 或自定义二进制协议等，
 * remoting 只依赖该接口，不关心具体实现细节。</p>
 */
public interface Serializer {

    /**
     * 将对象序列化为字节数组。
     *
     * @param value 待序列化的对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object value);

    /**
     * 将字节数组反序列化为指定类型的对象。
     *
     * @param bytes 序列化后的字节数组
     * @param type  目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化得到的对象
     */
    <T> T deserialize(byte[] bytes, Class<T> type);
}
