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

    @Test void testOverdraftBoundaryForEveryWithdrawalOrigin() {
        for (int origin = 0; origin < 4; origin++) {
            CurrentAccount a = account(100, 1_000);
            List<AccountEvent> owner = new ArrayList<>();
            a.subscribe(AccountEventType.OVERDRAFT, owner::add);
            LongPredicate withdraw = switch (origin) {
                case 0 -> a::cashWithdrawal;
                case 1 -> a::payment;
                case 2 -> a::outgoingTransfer;
                default -> a::outgoingPix;
            };
            assertTrue(withdraw.test(100)); // exactly zero is not negative
            assertTrue(owner.isEmpty(), "zero balance origin " + origin);
            assertTrue(withdraw.test(1)); // from zero (nonnegative) to negative
            assertEquals(1, owner.size(), "overdraft origin " + origin);
            assertEquals(-1, owner.getFirst().resultingBalanceCents());
            assertTrue(withdraw.test(999)); // stays negative, reaches the inclusive limit
            assertFalse(withdraw.test(1)); // beyond the limit
            assertEquals(1, owner.size());
            assertEquals(-1_000, a.balanceCents());
            assertTrue(a.deposit(1_000)); // back to exactly zero
            assertTrue(withdraw.test(1)); // a new crossing alerts again
            assertEquals(2, owner.size());
        }
    }

    @Test void testInvestmentCrossingJumpsAndRecrossing() {
        CurrentAccount a = account(0, 0);
        List<AccountEvent> investment = new ArrayList<>();
        a.subscribe(AccountEventType.INVESTMENT_CROSSING, investment::add);
        assertTrue(a.deposit(6_000_000)); // one transaction jumping past the threshold
        assertEquals(1, investment.size());
        assertEquals(6_000_000, investment.getFirst().resultingBalanceCents());
        assertTrue(a.incomingPix(1)); // already at or above: no crossing
        assertEquals(1, investment.size());
        assertTrue(a.payment(1_000_002)); // falls just below R$50,000 (4_999_999)
        assertTrue(a.incomingTransfer(1)); // crosses again at exactly R$50,000
        assertEquals(2, investment.size());
        assertEquals(5_000_000, investment.get(1).resultingBalanceCents());

        CurrentAccount negative = account(0, 1_000_000);
        List<AccountEvent> fromNegative = new ArrayList<>();
        negative.subscribe(AccountEventType.INVESTMENT_CROSSING, fromNegative::add);
        assertTrue(negative.outgoingPix(1_000_000));
        assertTrue(negative.deposit(5_999_999)); // -R$10,000 to R$49,999.99
        assertTrue(fromNegative.isEmpty());
        assertTrue(negative.deposit(1));
        assertEquals(1, fromNegative.size());
    }

    @Test void testMessagesAndCompletedBalancesAcrossRecipients() {
        CurrentAccount a = account(1_000_000, 2_000_000);
        SmsDelivery ownerSms = new SmsDelivery(), managerSms = new SmsDelivery();
        WhatsAppDelivery securityWhatsApp = new WhatsAppDelivery(), investmentWhatsApp = new WhatsAppDelivery();
        AccountNotificationRouting.attach(a, new AccountEventRecipient(ownerSms),
                new AccountEventRecipient(managerSms), new AccountEventRecipient(securityWhatsApp),
                new AccountEventRecipient(investmentWhatsApp));
        List<String> observed = new ArrayList<>();
        for (AccountEventType type : AccountEventType.values())
            a.subscribe(type, event -> observed.add(event.type() + "=" + a.balanceCents()
                    + "/" + event.resultingBalanceCents()));

        assertTrue(a.outgoingTransfer(1_500_000)); // R$15,000: overdraft and digital security
        assertTrue(a.deposit(1_000_000)); // R$5,000.00, no qualifying event
        assertTrue(a.cashWithdrawal(700_000)); // R$7,000: overdraft and account manager
        assertTrue(a.incomingPix(5_200_000)); // R$50,000.00 reached
        assertEquals(List.of("OVERDRAFT=-500000/-500000", "SECURITY_WITHDRAWAL=-500000/-500000",
                "OVERDRAFT=-200000/-200000", "MANAGER_WITHDRAWAL=-200000/-200000",
                "INVESTMENT_CROSSING=5000000/5000000"), observed);

        assertEquals(List.of(
                "SMS | Event: OVERDRAFT; Customer: Ana Silva; CPF: 123.456.789-00; Branch: 1234; "
                        + "Account: 98765-0; Resulting balance: R$ -5000.00",
                "SMS | Event: OVERDRAFT; Customer: Ana Silva; CPF: 123.456.789-00; Branch: 1234; "
                        + "Account: 98765-0; Resulting balance: R$ -2000.00"), ownerSms.messages());
        assertEquals(List.of("SMS | Event: MANAGER_WITHDRAWAL; Customer: Ana Silva; CPF: 123.456.789-00; "
                + "Branch: 1234; Account: 98765-0; Resulting balance: R$ -2000.00"), managerSms.messages());
        assertEquals(List.of("WhatsApp | Event: SECURITY_WITHDRAWAL; Customer: Ana Silva; CPF: 123.456.789-00; "
                + "Branch: 1234; Account: 98765-0; Resulting balance: R$ -5000.00"), securityWhatsApp.messages());
        assertEquals(List.of("WhatsApp | Event: INVESTMENT_CROSSING; Customer: Ana Silva; CPF: 123.456.789-00; "
                + "Branch: 1234; Account: 98765-0; Resulting balance: R$ 50000.00"), investmentWhatsApp.messages());
    }

    @Test void testRejectedQualifyingTransactionsNotifyNobody() {
        CurrentAccount a = account(1_000_000, 0);
        List<AccountEvent> received = new ArrayList<>();
        for (AccountEventType type : AccountEventType.values()) a.subscribe(type, received::add);
        assertFalse(a.cashWithdrawal(1_000_001)); // would be overdraft and digital security
        assertFalse(a.payment(1_500_000));
        assertEquals(1_000_000, a.balanceCents());
        assertThrows(IllegalArgumentException.class, () -> a.deposit(-5_000_000));
        assertTrue(received.isEmpty());
        assertTrue(a.cashWithdrawal(1_000_000)); // to exactly zero: digital security only
        assertEquals(List.of(AccountEventType.SECURITY_WITHDRAWAL), received.stream().map(AccountEvent::type).toList());
        assertEquals(0, received.getFirst().resultingBalanceCents());
    }
}
