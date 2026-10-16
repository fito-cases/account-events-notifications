package account;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Monetary values are integer cents; the negative-limit magnitude is an inclusive bound. */
public final class CurrentAccount {
    private static final long MANAGER_THRESHOLD = 500_000;
    private static final long SECURITY_THRESHOLD = 1_000_000;
    private static final long INVESTMENT_THRESHOLD = 5_000_000;

    private final Customer customer;
    private final String branch;
    private final String accountNumber;
    private final long negativeLimitCents;
    private final Map<AccountEventType, List<AccountEventListener>> subscribers =
            new EnumMap<>(AccountEventType.class);
    private long balanceCents;

    public CurrentAccount(Customer customer, String branch, String accountNumber,
                          long openingBalanceCents, long negativeLimitCents) {
        this.customer = Objects.requireNonNull(customer);
        this.branch = Objects.requireNonNull(branch);
        this.accountNumber = Objects.requireNonNull(accountNumber);
        if (branch.isBlank() || accountNumber.isBlank() || openingBalanceCents < 0
                || negativeLimitCents < 0) throw new IllegalArgumentException("Invalid account configuration");
        this.balanceCents = openingBalanceCents;
        this.negativeLimitCents = negativeLimitCents;
    }

    public long balanceCents() { return balanceCents; }

    public void subscribe(AccountEventType type, AccountEventListener listener) {
        subscribers.computeIfAbsent(Objects.requireNonNull(type), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(listener));
    }

    public void unsubscribe(AccountEventType type, AccountEventListener listener) {
        List<AccountEventListener> listeners = subscribers.get(Objects.requireNonNull(type));
        if (listeners != null) listeners.remove(listener);
    }

    public boolean cashWithdrawal(long cents) { return withdraw(cents); }
    public boolean payment(long cents) { return withdraw(cents); }
    public boolean outgoingTransfer(long cents) { return withdraw(cents); }
    public boolean outgoingPix(long cents) { return withdraw(cents); }
    public boolean deposit(long cents) { return receive(cents); }
    public boolean incomingTransfer(long cents) { return receive(cents); }
    public boolean incomingPix(long cents) { return receive(cents); }

    private boolean withdraw(long cents) {
        requirePositive(cents);
        long next;
        try { next = Math.subtractExact(balanceCents, cents); }
        catch (ArithmeticException overflow) { return false; }
        if (next < -negativeLimitCents) return false;
        long previous = balanceCents;
        balanceCents = next;
        if (previous >= 0 && next < 0) publish(AccountEventType.OVERDRAFT);
        if (cents >= SECURITY_THRESHOLD) publish(AccountEventType.SECURITY_WITHDRAWAL);
        else if (cents >= MANAGER_THRESHOLD) publish(AccountEventType.MANAGER_WITHDRAWAL);
        return true;
    }

    private boolean receive(long cents) {
        requirePositive(cents);
        long next;
        try { next = Math.addExact(balanceCents, cents); }
        catch (ArithmeticException overflow) { return false; }
        long previous = balanceCents;
        balanceCents = next;
        if (previous < INVESTMENT_THRESHOLD && next >= INVESTMENT_THRESHOLD)
            publish(AccountEventType.INVESTMENT_CROSSING);
        return true;
    }

    private static void requirePositive(long cents) {
        if (cents <= 0) throw new IllegalArgumentException("Amount must be positive cents");
    }

    private void publish(AccountEventType type) {
        AccountEvent event = new AccountEvent(type, customer, branch, accountNumber, balanceCents);
        for (AccountEventListener listener : List.copyOf(subscribers.getOrDefault(type, List.of())))
            listener.onAccountEvent(event);
    }
}
