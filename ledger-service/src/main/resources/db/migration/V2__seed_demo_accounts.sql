INSERT INTO ledger.accounts (account_id, balance, currency, version, created_at, updated_at)
VALUES
    ('acct-mumbai-salary-104392', 250000.00, 'INR', 0, now(), now()),
    ('acct-kirana-settlement-2048', 100000.00, 'INR', 0, now(), now()),
    ('acct-pune-retail-662810', 150000.00, 'INR', 0, now(), now()),
    ('acct-jaipur-marketplace-7781', 50000.00, 'INR', 0, now(), now()),
    ('acct-bengaluru-vip-739201', 500000.00, 'INR', 0, now(), now()),
    ('acct-navi-mumbai-mule-wallet-8842', 10000.00, 'INR', 0, now(), now())
ON CONFLICT (account_id) DO NOTHING;
