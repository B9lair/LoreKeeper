package com.xiaoyu.aiaigent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.ClientMcpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 带连接级 JWT 的 SSE 传输实现（步骤 3.1 自研）。
 *
 * M6 的 {@code HttpClientSseClientTransport}（mcp SDK 0.7.0）不支持自定义请求头，
 * 故参照其实现，在 SSE 订阅与消息 POST 的请求头中注入 {@code Authorization: Bearer <JWT>}，
 * 使 database MCP 服务端能据此解析 workId 做强制隔离（方案 2.7 / 3.1）。
 */
public class BearerSseClientTransport implements ClientMcpTransport {

    private static final Logger logger = LoggerFactory.getLogger(BearerSseClientTransport.class);

    /** SSE 事件类型：JSON-RPC 消息 */
    private static final String MESSAGE_EVENT_TYPE = "message";

    /** SSE 事件类型：消息端点发现 */
    private static final String ENDPOINT_EVENT_TYPE = "endpoint";

    /** 默认 SSE 端点路径 */
    private static final String SSE_ENDPOINT = "/sse";

    private static final Pattern EVENT_DATA_PATTERN = Pattern.compile("^data:(.+)$", Pattern.MULTILINE);
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^id:(.+)$", Pattern.MULTILINE);
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^event:(.+)$", Pattern.MULTILINE);

    /** MCP server 基础 URI，如 http://localhost:8125 */
    private final String baseUri;

    /** 连接级 JWT（含 userId + workId） */
    private final String token;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    /** 协调消息端点发现 */
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    /** 已发现的消息端点 URL */
    private final AtomicReference<String> messageEndpoint = new AtomicReference<>();

    /** SSE 连接 future */
    private final AtomicReference<CompletableFuture<Void>> connectionFuture = new AtomicReference<>();

    private volatile boolean isClosing = false;

    public BearerSseClientTransport(String baseUri, String token) {
        this(baseUri, token, new ObjectMapper());
    }

    public BearerSseClientTransport(String baseUri, String token, ObjectMapper objectMapper) {
        this.baseUri = baseUri;
        this.token = token;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        connectionFuture.set(future);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri + SSE_ENDPOINT))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Authorization", "Bearer " + token) // 连接级 JWT
                .GET()
                .build();

        // SSE 逐行解析（参照 FlowSseClient 实现，空行触发事件）
        StringBuilder eventBuilder = new StringBuilder();
        AtomicReference<String> currentEventId = new AtomicReference<>();
        AtomicReference<String> currentEventType = new AtomicReference<>("message");

        Flow.Subscriber<String> lineSubscriber = new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String line) {
                if (line.isEmpty()) {
                    if (eventBuilder.length() > 0) {
                        dispatchEvent(currentEventId.get(), currentEventType.get(),
                                eventBuilder.toString().trim(), handler, future);
                        eventBuilder.setLength(0);
                    }
                }
                else if (line.startsWith("data:")) {
                    var matcher = EVENT_DATA_PATTERN.matcher(line);
                    if (matcher.find()) {
                        eventBuilder.append(matcher.group(1).trim()).append("\n");
                    }
                }
                else if (line.startsWith("id:")) {
                    var matcher = EVENT_ID_PATTERN.matcher(line);
                    if (matcher.find()) {
                        currentEventId.set(matcher.group(1).trim());
                    }
                }
                else if (line.startsWith("event:")) {
                    var matcher = EVENT_TYPE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        currentEventType.set(matcher.group(1).trim());
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (!isClosing) {
                    logger.error("SSE 连接错误", throwable);
                    future.completeExceptionally(throwable);
                }
            }

            @Override
            public void onComplete() {
                if (eventBuilder.length() > 0) {
                    dispatchEvent(currentEventId.get(), currentEventType.get(),
                            eventBuilder.toString().trim(), handler, future);
                }
            }
        };

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.fromLineSubscriber(lineSubscriber))
                .thenAccept(response -> {
                    int status = response.statusCode();
                    if (status != 200 && status != 201 && status != 202 && status != 206) {
                        throw new RuntimeException("SSE 连接失败，状态码: " + status);
                    }
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });

        return Mono.fromFuture(future);
    }

    private void dispatchEvent(String eventId, String eventType, String data,
                               Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler,
                               CompletableFuture<Void> future) {
        if (isClosing) {
            return;
        }
        try {
            if (ENDPOINT_EVENT_TYPE.equals(eventType)) {
                messageEndpoint.set(data);
                closeLatch.countDown();
                future.complete(null);
            }
            else if (MESSAGE_EVENT_TYPE.equals(eventType)) {
                JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, data);
                handler.apply(Mono.just(message)).subscribe();
            }
            else {
                logger.warn("收到未识别的 SSE 事件类型: {}", eventType);
            }
        }
        catch (IOException e) {
            logger.error("处理 SSE 事件失败", e);
            future.completeExceptionally(e);
        }
    }

    @Override
    public Mono<Void> sendMessage(JSONRPCMessage message) {
        if (isClosing) {
            return Mono.empty();
        }
        try {
            if (!closeLatch.await(10, TimeUnit.SECONDS)) {
                return Mono.error(new McpError("等待消息端点超时"));
            }
        }
        catch (InterruptedException e) {
            return Mono.error(new McpError("等待消息端点中断"));
        }

        String endpoint = messageEndpoint.get();
        if (endpoint == null) {
            return Mono.error(new McpError("无可用消息端点"));
        }

        try {
            String jsonText = this.objectMapper.writeValueAsString(message);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUri + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token) // 连接级 JWT
                    .POST(HttpRequest.BodyPublishers.ofString(jsonText))
                    .build();

            return Mono.fromFuture(
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenCompose(response -> {
                        int status = response.statusCode();
                        if (status != 200 && status != 201 && status != 202 && status != 206) {
                            logger.error("发送 MCP 消息失败，状态码: {}", status);
                            return CompletableFuture.failedFuture(
                                    new RuntimeException("发送 MCP 消息失败，状态码: " + status));
                        }
                        return CompletableFuture.completedFuture(null);
                    }));
        }
        catch (IOException e) {
            if (!isClosing) {
                return Mono.error(new RuntimeException("序列化消息失败", e));
            }
            return Mono.empty();
        }
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            isClosing = true;
            CompletableFuture<Void> future = connectionFuture.get();
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return this.objectMapper.convertValue(data, typeRef);
    }
}
