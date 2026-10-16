package account;

import java.util.Objects;

public final class AccountEventRecipient implements AccountEventListener {
    private final MessageDelivery delivery;

    public AccountEventRecipient(MessageDelivery delivery) {
        this.delivery = Objects.requireNonNull(delivery);
    }

    @Override public void onAccountEvent(AccountEvent event) {
        delivery.deliver(event);
    }
}
