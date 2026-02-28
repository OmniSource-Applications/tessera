package live.omnisource.tessera.web;

import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.stream.StreamSubscription;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/streams")
public class StreamsController {

    private final StreamBroker broker;

    public StreamsController(StreamBroker broker) {
        this.broker = broker;
    }

    @GetMapping
    public String index(Model model) {
        List<StreamSubscription> subs = broker.activeSubscriptions();

        // Group by protocol for the UI
        long sseCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.SSE).count();
        long wsCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.WEBSOCKET).count();
        long restCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.REST_POLL).count();
        long totalDelivered = subs.stream().mapToLong(StreamSubscription::deliveredCount).sum();

        model.addAttribute("title", "Streams");
        model.addAttribute("description", "Live streaming connections");
        model.addAttribute("view", "streams/index");
        model.addAttribute("subscriptions", subs);
        model.addAttribute("stats", Map.of(
                "total", subs.size(),
                "sse", sseCount,
                "websocket", wsCount,
                "restPoll", restCount,
                "totalDelivered", totalDelivered
        ));

        return "layout/page";
    }
}