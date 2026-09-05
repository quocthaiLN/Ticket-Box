-- TicketBox Database Migration V2: Basic Seed Data
-- PostgreSQL DML Script for demo and initial testing

-- =============================================================================
-- 1. USERS & ACCOUNTS SEED
-- Password for all accounts: "Password@123" (BCrypt hash)
-- =============================================================================

INSERT INTO users (id, email, full_name, phone, role, status, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'admin@ticketbox.com', 'System Administrator', '+84900000001', 'ADMIN', 'ACTIVE', NOW(), NOW()),
('22222222-2222-2222-2222-222222222222', 'organizer@ticketbox.com', 'VietID Entertainment', '+84900000002', 'ORGANIZER', 'ACTIVE', NOW(), NOW()),
('33333333-3333-3333-3333-333333333333', 'checker@ticketbox.com', 'Gate Checker 01', '+84900000003', 'CHECKER', 'ACTIVE', NOW(), NOW()),
('44444444-4444-4444-4444-444444444444', 'audience1@ticketbox.com', 'Nguyen Van Audience', '+84901111111', 'AUDIENCE', 'ACTIVE', NOW(), NOW()),
('55555555-5555-5555-5555-555555555555', 'audience2@ticketbox.com', 'Tran Thi Fan', '+84902222222', 'AUDIENCE', 'ACTIVE', NOW(), NOW());

INSERT INTO user_accounts (id, user_id, password_hash, provider, provider_user_id, created_at, updated_at) VALUES
('11111111-1111-1111-1111-11111111111a', '11111111-1111-1111-1111-111111111111', '$2a$10$E2UPv7arXnm9X/O.n0bF3u8/P1807y1zV8aYVf8d.XJ7iXpWpP69W', 'LOCAL', 'admin@ticketbox.com', NOW(), NOW()),
('22222222-2222-2222-2222-22222222222b', '22222222-2222-2222-2222-222222222222', '$2a$10$E2UPv7arXnm9X/O.n0bF3u8/P1807y1zV8aYVf8d.XJ7iXpWpP69W', 'LOCAL', 'organizer@ticketbox.com', NOW(), NOW()),
('33333333-3333-3333-3333-33333333333c', '33333333-3333-3333-3333-333333333333', '$2a$10$E2UPv7arXnm9X/O.n0bF3u8/P1807y1zV8aYVf8d.XJ7iXpWpP69W', 'LOCAL', 'checker@ticketbox.com', NOW(), NOW()),
('44444444-4444-4444-4444-44444444444d', '44444444-4444-4444-4444-444444444444', '$2a$10$E2UPv7arXnm9X/O.n0bF3u8/P1807y1zV8aYVf8d.XJ7iXpWpP69W', 'LOCAL', 'audience1@ticketbox.com', NOW(), NOW()),
('55555555-5555-5555-5555-55555555555e', '55555555-5555-5555-5555-555555555555', '$2a$10$E2UPv7arXnm9X/O.n0bF3u8/P1807y1zV8aYVf8d.XJ7iXpWpP69W', 'LOCAL', 'audience2@ticketbox.com', NOW(), NOW());

-- =============================================================================
-- 2. CONCERTS SEED
-- =============================================================================

INSERT INTO concerts (id, title, slug, venue, description, artist_name, artist_bio, starts_at, ends_at, status, cover_image_url, seat_map_url, created_at, updated_at) VALUES
(
    'a1111111-1111-1111-1111-111111111111',
    'Anh Trai Say Hi Live Concert 2026',
    'anh-trai-say-hi-2026',
    'Sân vận động Quốc gia Mỹ Đình, Hà Nội',
    'Đêm nhạc quy tụ 30 Anh Trai với hàng loạt bản hit bùng nổ sân khấu Mỹ Đình.',
    'Anh Trai Say Hi All-Stars',
    'Đội hình Anh Trai Say Hi gồm 30 nghệ sĩ hàng đầu V-Pop.',
    NOW() + INTERVAL '30 days',
    NOW() + INTERVAL '30 days 4 hours',
    'PUBLISHED',
    'https://storage.ticketbox.vn/concerts/ats-2026-cover.jpg',
    'https://storage.ticketbox.vn/concerts/ats-2026-seatmap.svg',
    NOW(),
    NOW()
),
(
    'a2222222-2222-2222-2222-222222222222',
    'Sơn Tùng M-TP Sky Tour 2026',
    'son-tung-mtp-sky-tour-2026',
    'Nhà thi đấu Phú Thọ, TP. Hồ Chí Minh',
    'Hành trình Sky Tour 2026 mang tới trải nghiệm âm nhạc đỉnh cao cùng Sơn Tùng M-TP.',
    'Sơn Tùng M-TP',
    'Sơn Tùng M-TP là ca sĩ, nhạc sĩ hàng đầu Việt Nam.',
    NOW() + INTERVAL '45 days',
    NOW() + INTERVAL '45 days 3 hours',
    'PUBLISHED',
    'https://storage.ticketbox.vn/concerts/sky-tour-2026-cover.jpg',
    'https://storage.ticketbox.vn/concerts/sky-tour-2026-seatmap.svg',
    NOW(),
    NOW()
);

-- =============================================================================
-- 3. SEAT ZONES SEED
-- =============================================================================

INSERT INTO seat_zones (id, concert_id, code, name, description, capacity, svg_path, sort_order, created_at, updated_at) VALUES
('b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 'SVIP', 'Super VIP President', 'Khu sát sân khấu, bao gồm F&B cao cấp', 500, 'path/to/svip.svg', 1, NOW(), NOW()),
('b2222222-2222-2222-2222-222222222222', 'a1111111-1111-1111-1111-111111111111', 'VIP', 'VIP Diamond Floor', 'Khu vực sàn đứng VIP sát sân khấu', 1500, 'path/to/vip.svg', 2, NOW(), NOW()),
('b3333333-3333-3333-3333-333333333333', 'a1111111-1111-1111-1111-111111111111', 'GA', 'General Admission Stand', 'Khu tự do khán đài A & B', 5000, 'path/to/ga.svg', 3, NOW(), NOW()),

('b4444444-4444-4444-4444-444444444444', 'a2222222-2222-2222-2222-222222222222', 'SKY_VIP', 'Sky VIP Zone', 'Khu vực VIP kèm quà tặng chính hãng', 1000, 'path/to/sky_vip.svg', 1, NOW(), NOW()),
('b5555555-5555-5555-5555-555555555555', 'a2222222-2222-2222-2222-222222222222', 'SKY_GA', 'Sky General Admission', 'Khu vực đứng phổ thông', 4000, 'path/to/sky_ga.svg', 2, NOW(), NOW());

-- =============================================================================
-- 4. TICKET TYPES SEED
-- =============================================================================

INSERT INTO ticket_types (id, concert_id, seat_zone_id, name, description, price, currency, total_quantity, held_quantity, sold_quantity, max_per_user, sale_start_at, sale_end_at, status, created_at, updated_at) VALUES
(
    'c1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111111',
    'b1111111-1111-1111-1111-111111111111',
    'Vé SVIP President',
    'Vé SVIP kèm thẻ cứng & quà tặng độc quyền',
    4500000.00, 'VND', 500, 0, 0, 4,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '25 days',
    'ON_SALE', NOW(), NOW()
),
(
    'c2222222-2222-2222-2222-222222222222',
    'a1111111-1111-1111-1111-111111111111',
    'b2222222-2222-2222-2222-222222222222',
    'Vé VIP Floor',
    'Vé sàn VIP đứng',
    2500000.00, 'VND', 1500, 0, 0, 4,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '25 days',
    'ON_SALE', NOW(), NOW()
),
(
    'c3333333-3333-3333-3333-333333333333',
    'a1111111-1111-1111-1111-111111111111',
    'b3333333-3333-3333-3333-333333333333',
    'Vé GA Standing',
    'Vé khán đài tự do',
    1200000.00, 'VND', 5000, 0, 0, 6,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '25 days',
    'ON_SALE', NOW(), NOW()
),
(
    'c4444444-4444-4444-4444-444444444444',
    'a2222222-2222-2222-2222-222222222222',
    'b4444444-4444-4444-4444-444444444444',
    'Sky VIP Zone',
    'Vé VIP Sky Tour 2026',
    3000000.00, 'VND', 1000, 0, 0, 4,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '40 days',
    'ON_SALE', NOW(), NOW()
),
(
    'c5555555-5555-5555-5555-555555555555',
    'a2222222-2222-2222-2222-222222222222',
    'b5555555-5555-5555-5555-555555555555',
    'Sky GA Zone',
    'Vé phổ thông Sky Tour 2026',
    1500000.00, 'VND', 4000, 0, 0, 4,
    NOW() - INTERVAL '1 day', NOW() + INTERVAL '40 days',
    'ON_SALE', NOW(), NOW()
);

-- =============================================================================
-- 5. GUEST LIST SEED
-- =============================================================================

INSERT INTO guest_list (id, concert_id, seat_zone_id, full_name, phone, email, code, status, created_at, updated_at) VALUES
(
    'd1111111-1111-1111-1111-111111111111',
    'a1111111-1111-1111-1111-111111111111',
    'b1111111-1111-1111-1111-111111111111',
    'Nha Báo Tran Van A',
    '+84901234567',
    'press.a@newspaper.vn',
    'GUEST-ATS-001',
    'INVITED',
    NOW(),
    NOW()
),
(
    'd2222222-2222-2222-2222-222222222222',
    'a2222222-2222-2222-2222-222222222222',
    'b4444444-4444-4444-4444-444444444444',
    'Nha Tai Troc Nguyen Thi B',
    '+84909876543',
    'sponsor.b@company.com',
    'GUEST-SKY-001',
    'INVITED',
    NOW(),
    NOW()
);
