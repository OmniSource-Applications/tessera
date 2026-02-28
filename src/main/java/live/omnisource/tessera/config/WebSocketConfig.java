package live.omnisource.tessera.config;

import live.omnisource.tessera.stream.adapter.websocket.WebSocketStreamHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketStreamHandler streamHandler;

    public WebSocketConfig(WebSocketStreamHandler streamHandler) {
        this.streamHandler = streamHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamHandler, "/ws/stream")
                .setAllowedOriginPatterns("*");
    }
}
