INSERT INTO message_outbox (id, type, version, body, created_at, published_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'UserCreated', 'v1', decode('0102', 'hex'), NOW() - INTERVAL '2 days', NULL),
    ('20000000-0000-0000-0000-000000000002', 'SendCreatedUserNotification', 'v1', decode('0304', 'hex'), NOW() - INTERVAL '1 day', NULL),
    ('20000000-0000-0000-0000-000000000003', 'UserCreated', 'v1', decode('0506', 'hex'), NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day');
