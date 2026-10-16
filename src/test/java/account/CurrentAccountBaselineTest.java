package account;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurrentAccountBaselineTest {
    private CurrentAccount account(long opening, long limit) {
        return new CurrentAccount(new Customer("Ada", "12345678900"), "001", "42", opening, limit);
    }

    @Test void successfulOperationsAndInclusiveNegativeLimit() {
        CurrentAccount a = account(10_000, 10_000);
        assertTrue(a.cashWithdrawal(10_000));
        assertTrue(a.payment(5_000));
        assertTrue(a.outgoingTransfer(3_000));
        assertTrue(a.outgoingPix(2_000));
        assertEquals(-10_000, a.balanceCents());
        assertFalse(a.cashWithdrawal(1));
        assertEquals(-10_000, a.balanceCents());
        assertTrue(a.deposit(3_000));
        assertTrue(a.incomingTransfer(2_000));
        assertTrue(a.incomingPix(5_000));
        assertEquals(0, a.balanceCents());
    }

    @Test void rejectsInvalidAmountsAndOverflowWithoutNotifying() {
        CurrentAccount a = account(Long.MAX_VALUE - 1, 0);
        List<AccountEvent> received = new ArrayList<>();
        for (AccountEventType type : AccountEventType.values()) a.subscribe(type, received::add);
        assertThrows(IllegalArgumentException.class, () -> a.deposit(0));
        assertThrows(IllegalArgumentException.class, () -> a.payment(-1));
        assertFalse(a.incomingPix(2));
        assertFalse(a.outgoingTransfer(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE - 1, a.balanceCents());
        assertTrue(received.isEmpty());
        CurrentAccount limited = account(100, 0);
        limited.subscribe(AccountEventType.OVERDRAFT, received::add);
        assertFalse(limited.payment(101));
        assertEquals(100, limited.balanceCents());
        assertTrue(received.isEmpty());
    }

    @Test void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> account(0, -1));
        assertThrows(IllegalArgumentException.class, () -> account(-1, 0));
    }
}
