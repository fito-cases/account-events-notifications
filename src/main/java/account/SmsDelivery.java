package account;

import java.util.ArrayList;
import java.util.List;

/** Local delivery adapter: captures rendered SMS messages without a provider. */
public final class SmsDelivery implements MessageDelivery {
    private final List<String> messages = new ArrayList<>();

    @Override public void deliver(AccountEvent event) {
        messages.add("SMS | " + AccountEventMessage.render(event));
    }

    public List<String> messages() { return List.copyOf(messages); }
}
