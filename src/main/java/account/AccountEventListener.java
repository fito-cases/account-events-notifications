package account;

@FunctionalInterface
public interface AccountEventListener {
    void onAccountEvent(AccountEvent event);
}
