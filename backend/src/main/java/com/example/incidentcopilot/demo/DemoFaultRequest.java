package com.example.incidentcopilot.demo;

/**
 * 演示故障注入请求参数。
 * <p>用于 {@link DemoController} 的故障注入接口，允许调用方控制是否自动创建事件。</p>
 *
 * @param autoCreateIncident 是否自动创建事件，默认 false
 */
public record DemoFaultRequest(boolean autoCreateIncident) {
}
