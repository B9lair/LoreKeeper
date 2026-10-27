package com.xiaoyu.aiaigent.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * 全局异常兜底。
 *
 * 背景：SSE 是异步写，客户端在 Agent 处理中途断开（刷新/关闭/前端 abort）后，
 * SseEmitter.send() 底层在请求线程 flush 时会抛 IOException，逃逸到 DispatcherServlet
 * 打出 ERROR 噪音日志（BaseAgent.safeSend 只能拦住调用线程那一份）。
 * 这类断连是预期行为，在此静默处理，避免掩盖真正的错误。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Tomcat 对客户端主动断开封装的异常（Broken pipe / Connection reset 等）。
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e) {
        log.debug("客户端断开连接，忽略：{}", e.getMessage());
    }

    /**
     * 兜底：容器层直接抛出的 java.io.IOException（如 Windows 下
     * "你的主机中的软件中止了一个已建立的连接" / "Broken pipe"）。
     * 断连类静默，其余按真实 IO 错误记录。
     */
    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException e) {
        if (isClientDisconnect(e)) {
            log.debug("客户端断开连接，忽略：{}", e.getMessage());
        } else {
            log.error("IO 异常", e);
        }
    }

    private boolean isClientDisconnect(IOException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("中止了一个已建立的连接")
                || msg.contains("远程主机强迫关闭了一个现有的连接")
                || msg.contains("Broken pipe")
                || msg.contains("Connection reset");
    }
}
