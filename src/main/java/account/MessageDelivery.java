package account;

public interface MessageDelivery {
    void deliver(AccountEvent event);
}
