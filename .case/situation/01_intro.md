# Ticket: Account activity notification request

**From:** Retail banking operations  
**To:** Account platform team

Our current account model must support account-activity alerts without taking responsibility for sending messages. Please make it possible for the relevant teams to receive qualifying account events, while keeping SMS and WhatsApp as separate delivery options.

- Alert the account owner when a successful transaction moves the balance from nonnegative to negative.
- For each successful withdrawal of at least R$5,000 but less than R$10,000, alert the account manager; at R$10,000 or more, alert digital security. These are per-transaction amounts, not daily totals. Withdrawals may be cash withdrawals, payments, transfers or Pix.
- Alert investment management when a successful incoming transaction crosses from below R$50,000 to at least R$50,000. Incoming funds may be deposits, transfers or Pix.

Each notification must identify the event, the customer's name and CPF, branch and account numbers, and the resulting balance. Preserve the existing negative-limit rule: rejected transactions must not produce alerts. Currency comparisons must respect the stated boundaries. Use English class and method names.

Please demonstrate the behavior and boundary cases with tests. We should be able to add or change recipients without putting concrete message senders inside `CurrentAccount`.
