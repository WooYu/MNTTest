package com.mnatool.yunjutongprobe;

import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;

public class ProbeErrorMessageTest {
    @Test
    public void explainsDnsFailure() {
        assertEquals("无法解析服务器地址，请检查服务器地址或网络连接",
                ProbeErrorMessage.from(new UnknownHostException("bad.host")));
    }

    @Test
    public void explainsTimeoutAndRefusal() {
        assertEquals("连接超时，请检查网络、服务器地址和端口",
                ProbeErrorMessage.from(new SocketTimeoutException()));
        assertEquals("服务器拒绝连接，请确认服务已启动且端口已放通",
                ProbeErrorMessage.from(new ConnectException("Connection refused")));
    }

    @Test
    public void explainsMqttAuthenticationFailure() {
        assertEquals("MQTT 鉴权失败，请检查用户名、Token、设备密码和 MAC 地址",
                ProbeErrorMessage.from(new SecurityException("MQTT CONNACK 5")));
    }

    @Test
    public void givesFallbackWithUsefulDetail() {
        assertEquals("测试运行失败：连接被远端关闭",
                ProbeErrorMessage.from(new IllegalStateException("连接被远端关闭")));
    }

    @Test
    public void recognizesMqttTimeoutAndNestedNetworkCause() {
        assertEquals("MQTT 响应超时，请检查 Broker、端口和网络连接",
                ProbeErrorMessage.from(new IllegalStateException("等待 CONNACK 超时")));
        assertEquals("无法解析服务器地址，请检查服务器地址或网络连接",
                ProbeErrorMessage.from(new IllegalStateException("Token 获取失败",
                        new UnknownHostException("gateway"))));
    }

    @Test
    public void explainsUnexpectedConnectionClosure() {
        assertEquals("网络连接已断开，请检查网络稳定性后重试",
                ProbeErrorMessage.from(new IllegalStateException("MQTT socket closed")));
    }
}
