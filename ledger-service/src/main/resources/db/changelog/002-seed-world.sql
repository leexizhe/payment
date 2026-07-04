--liquibase formatted sql

-- Seeds a small world so the load generator works immediately: a treasury
-- (money supply), one merchant, and three funded customer wallets. Wallets
-- are funded through real journal entries, so from the first request every
-- balance equals the sum of its postings — seeding exercises the
-- double-entry invariant, never bypasses it.

--changeset ledger:100-seed-accounts
insert into accounts (name, type, currency, balance_minor, version) values
    ('treasury',      'TREASURY', 'USD', -150000, 0),
    ('acme-merchant', 'MERCHANT', 'USD',       0, 0),
    ('wallet-alice',  'WALLET',   'USD',   50000, 0),
    ('wallet-bob',    'WALLET',   'USD',   50000, 0),
    ('wallet-carol',  'WALLET',   'USD',   50000, 0);
--rollback delete from accounts;

--changeset ledger:101-seed-funding-entries
insert into journal_entries (idempotency_key, description, created_at)
select 'seed:' || w.name, 'initial wallet funding', now()
from accounts w
where w.type = 'WALLET';

insert into postings (entry_id, account_id, amount_minor)
select e.id, (select id from accounts where name = 'treasury'), -50000
from journal_entries e
where e.idempotency_key like 'seed:%';

insert into postings (entry_id, account_id, amount_minor)
select e.id, w.id, 50000
from journal_entries e
join accounts w on e.idempotency_key = 'seed:' || w.name;
--rollback delete from postings; delete from journal_entries;
