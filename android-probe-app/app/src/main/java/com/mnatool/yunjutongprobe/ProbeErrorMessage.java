package com.mnatool.yunjutongprobe;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

final class ProbeErrorMessage {
    private ProbeErrorMessage() {
    }

    static String from(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof UnknownHostException) {
                return "无法解析服务器地址，请检查服务器地址或网络连接";
            }
            if (cause instanceof SocketTimeoutException) {
                return "连接超时，请检查网络、服务器地址和端口";
            }
            if (cause instanceof ConnectException) {
                return "服务器拒绝连接，请确认服务已启动且端口已放通";
            }
            if (cause instanceof SecurityException) {
                return "MQTT 鉴权失败，请检查用户名、Token、设备密码和 MAC 地址";
            }
            cause = cause.getCause();
        }
        String detail = error == null ? null : error.getMessage();
        if (detail != null && (detail.contains("CONNACK 超时") || detail.contains("SUBACK 超时"))) {
            return "MQTT 响应超时，请检查 Broker、端口和网络连接";
        }
        if (detail != null && (detail.contains("socket closed") || detail.contains("连接已关闭"))) {
            return "网络连接已断开，请检查网络稳定性后重试";
        }
        return detail == null || detail.trim().isEmpty()
                ? "测试运行失败，请检查网络和参数后重试"
                : "测试运行失败：" + detail.trim();
    }
}
