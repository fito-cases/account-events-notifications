package account;

import java.math.BigDecimal;

final class AccountEventMessage {
    private AccountEventMessage() { }

    static String render(AccountEvent event) {
        return "Event: " + event.type() + "; Customer: " + event.customer().name()
                + "; CPF: " + event.customer().cpf() + "; Branch: " + event.branch()
                + "; Account: " + event.accountNumber() + "; Resulting balance: R$ "
                + BigDecimal.valueOf(event.resultingBalanceCents(), 2).toPlainString();
    }
}
