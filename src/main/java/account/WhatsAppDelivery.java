package account;

import java.util.ArrayList;
import java.util.List;

/** Local delivery adapter: captures rendered WhatsApp messages without a provider. */
public final class WhatsAppDelivery implements MessageDelivery {
    private final List<String> messages = new ArrayList<>();

    @Override public void deliver(AccountEvent event) {
        messages.add("WhatsApp | " + AccountEventMessage.render(event));
    }

    public List<String> messages() { return List.copyOf(messages); }
}
