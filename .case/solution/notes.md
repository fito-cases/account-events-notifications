# Intended approach (evaluator only)

Keep the account's transaction validation and negative-limit rule. Use an Observer-style publisher and registered recipient contracts for account events, carrying immutable event details or access to the completed account state. Publish only after a successful state change. Model currency exactly (for example, decimal currency values or integer minor units). Distinguish transaction amount thresholds from balance crossing thresholds; route overdraft, withdrawal and incoming-balance events to the specified teams. Put message rendering and SMS/WhatsApp sending outside the account, behind recipient/delivery abstractions. Do not make a rejected operation emit an event.

# Rubric evidence

- **observer-collaboration:** inspect registration, one-to-many delivery and ability to vary recipient combinations.
- **sender-independence:** inspect account imports and control flow; SMS and WhatsApp implementations must not become concrete dependencies of the account.
- **event-recipients:** check overdraft crossing, withdrawal amounts at R$5,000 and R$10,000, incoming crossing at R$50,000, all transaction origins and non-events.
- **notification-context:** check event name, customer name and CPF, branch/account numbers and resulting balance in actual produced messages.
- **completed-state:** check that the committed balance is visible to recipients and rejected transactions do not notify.

# After starter source work

Replace the planned known-state checks with verified observations and name the real source and test evidence in `artifacts.yaml`. Refine these notes against the completed exemplar and its tests.
