package account;

import java.util.Objects;

/** Composition root: role-to-event routing is configured outside the account. */
public final class AccountNotificationRouting {
    private AccountNotificationRouting() { }

    public static void attach(CurrentAccount account, AccountEventListener owner,
                              AccountEventListener accountManager, AccountEventListener digitalSecurity,
                              AccountEventListener investmentManagement) {
        Objects.requireNonNull(account);
        account.subscribe(AccountEventType.OVERDRAFT, owner);
        account.subscribe(AccountEventType.MANAGER_WITHDRAWAL, accountManager);
        account.subscribe(AccountEventType.SECURITY_WITHDRAWAL, digitalSecurity);
        account.subscribe(AccountEventType.INVESTMENT_CROSSING, investmentManagement);
    }
}
