package account;

public record AccountEvent(AccountEventType type, Customer customer, String branch,
                           String accountNumber, long resultingBalanceCents) { }
