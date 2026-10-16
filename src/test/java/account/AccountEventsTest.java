package account;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import static org.junit.jupiter.api.Assertions.*;

class AccountEventsTest {
    private CurrentAccount account(long balance, long limit) {
        return new CurrentAccount(new Customer("Ana Silva", "123.456.789-00"), "1234", "98765-0", balance, limit);
    }

    @Test void testRecipientThresholdBoundaries() {
        CurrentAccount a = account(1, 2_000_000);
        List<AccountEvent> owner = new ArrayList<>(), manager = new ArrayList<>();
        List<AccountEvent> security = new ArrayList<>(), investment = new ArrayList<>();
        AccountNotificationRouting.attach(a, owner::add, manager::add, security::add, investment::add);
        assertTrue(a.cashWithdrawal(2));
        assertEquals(1, owner.size(), "Expected registered account-event notification");
        assertEquals(AccountEventType.OVERDRAFT, owner.getFirst().type());
        assertEquals(-1, owner.getFirst().resultingBalanceCents());
        assertTrue(manager.isEmpty() && security.isEmpty() && investment.isEmpty());
        assertTrue(a.payment(1)); // already negative: no new overdraft
        assertEquals(1, owner.size());
        assertTrue(a.deposit(1_000_000)); // back to positive, below investment threshold
        assertEquals(999_998, a.balanceCents());
        assertTrue(a.outgoingTransfer(499_999));
        assertTrue(manager.isEmpty());
        assertTrue(a.outgoingPix(500_000)); // exact R$5,000, crosses zero
        assertEquals(2, owner.size());
        assertEquals(1, manager.size());
        assertEquals(AccountEventType.MANAGER_WITHDRAWAL, manager.getFirst().type());
        assertTrue(security.isEmpty());

        for (int origin = 0; origin < 4; origin++) {
            CurrentAccount band = account(4_000_000, 0);
            List<AccountEvent> lower = new ArrayList<>(), upper = new ArrayList<>(), unrelated = new ArrayList<>();
            band.subscribe(AccountEventType.MANAGER_WITHDRAWAL, lower::add);
            band.subscribe(AccountEventType.SECURITY_WITHDRAWAL, upper::add);
            band.subscribe(AccountEventType.INVESTMENT_CROSSING, unrelated::add);
            LongPredicate withdraw = switch (origin) {
                case 0 -> band::cashWithdrawal;
                case 1 -> band::payment;
                case 2 -> band::outgoingTransfer;
                default -> band::outgoingPix;
            };
            assertTrue(withdraw.test(499_999));
            assertTrue(withdraw.test(500_000));
            assertTrue(withdraw.test(999_999));
            assertTrue(withdraw.test(1_000_000));
            assertEquals(2, lower.size(), "lower band origin " + origin);
            assertEquals(1, upper.size(), "upper band origin " + origin);
            assertTrue(unrelated.isEmpty());
        }

        for (int origin = 0; origin < 3; origin++) {
            CurrentAccount incoming = account(4_999_998, 0);
            List<AccountEvent> crossing = new ArrayList<>(), unrelated = new ArrayList<>();
            incoming.subscribe(AccountEventType.INVESTMENT_CROSSING, crossing::add);
            incoming.subscribe(AccountEventType.SECURITY_WITHDRAWAL, unrelated::add);
            LongPredicate credit = switch (origin) {
                case 0 -> incoming::deposit;
                case 1 -> incoming::incomingTransfer;
                default -> incoming::incomingPix;
            };
            assertTrue(credit.test(1)); // below R$50,000
            assertTrue(crossing.isEmpty());
            assertTrue(credit.test(1)); // exactly R$50,000
            assertEquals(1, crossing.size());
            assertEquals(5_000_000, crossing.getFirst().resultingBalanceCents());
            assertTrue(credit.test(1)); // above without a second crossing
            assertEquals(1, crossing.size());
            assertTrue(unrelated.isEmpty());
        }

        CurrentAccount multi = account(500_000, 0);
        List<AccountEvent> first = new ArrayList<>(), second = new ArrayList<>();
        AccountEventListener removed = first::add;
        multi.subscribe(AccountEventType.MANAGER_WITHDRAWAL, removed);
        multi.subscribe(AccountEventType.MANAGER_WITHDRAWAL, second::add);
        assertTrue(multi.payment(500_000));
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        multi.unsubscribe(AccountEventType.MANAGER_WITHDRAWAL, removed);
        assertTrue(multi.deposit(500_000));
        assertTrue(multi.cashWithdrawal(500_000));
        assertEquals(1, first.size());
        assertEquals(2, second.size());
    }

    @Test void testNotificationContentAndCompletedBalance() {
        CurrentAccount a = account(4_999_999, 2_000_000);
        SmsDelivery sms = new SmsDelivery();
        WhatsAppDelivery whatsApp = new WhatsAppDelivery();
        List<Long> observedBalances = new ArrayList<>();
        List<AccountEvent> observedEvents = new ArrayList<>();
        a.subscribe(AccountEventType.INVESTMENT_CROSSING, event -> {
            observedBalances.add(a.balanceCents());
            observedEvents.add(event);
        });
        a.subscribe(AccountEventType.INVESTMENT_CROSSING, new AccountEventRecipient(sms));
        a.subscribe(AccountEventType.INVESTMENT_CROSSING, new AccountEventRecipient(whatsApp));
        assertTrue(a.incomingTransfer(1));
        assertEquals(1, sms.messages().size(), "Expected completed-balance notification");
        assertEquals(List.of(5_000_000L), observedBalances);
        assertEquals(a.balanceCents(), observedEvents.getFirst().resultingBalanceCents());
        for (String message : List.of(sms.messages().getFirst(), whatsApp.messages().getFirst())) {
            assertTrue(message.contains("INVESTMENT_CROSSING"));
            assertTrue(message.contains("Ana Silva"));
            assertTrue(message.contains("123.456.789-00"));
            assertTrue(message.contains("1234"));
            assertTrue(message.contains("98765-0"));
            assertTrue(message.contains("R$ 50000.00"));
        }
        assertTrue(sms.messages().getFirst().startsWith("SMS |"));
        assertTrue(whatsApp.messages().getFirst().startsWith("WhatsApp |"));
        assertFalse(a.outgoingPix(7_000_001)); // rejection cannot deliver
        assertEquals(1, sms.messages().size());
        assertEquals(1, whatsApp.messages().size());
    }
}
