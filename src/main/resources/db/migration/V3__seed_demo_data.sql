-- Demo catalog so the storefront isn't empty. Idempotent on slug/sku.
INSERT INTO categories (name, slug, description) VALUES
    ('Electronics', 'electronics', 'Gadgets and devices'),
    ('Books', 'books', 'Paperbacks and hardcovers')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO products (sku, name, description, price, stock_quantity, is_active, category_id, image_url) VALUES
    ('ELEC-001', 'Wireless Headphones', 'Over-ear Bluetooth headphones with active noise cancellation.',
        129.99, 25, TRUE, (SELECT id FROM categories WHERE slug = 'electronics'),
        'https://placehold.co/400x300?text=Headphones'),
    ('ELEC-002', 'Mechanical Keyboard', 'RGB backlit mechanical keyboard with tactile blue switches.',
        79.50, 40, TRUE, (SELECT id FROM categories WHERE slug = 'electronics'),
        'https://placehold.co/400x300?text=Keyboard'),
    ('ELEC-003', 'USB-C Hub', '7-in-1 USB-C hub with HDMI, Ethernet and SD card reader.',
        34.00, 60, TRUE, (SELECT id FROM categories WHERE slug = 'electronics'),
        'https://placehold.co/400x300?text=USB-C+Hub'),
    ('BOOK-001', 'Clean Code', 'A Handbook of Agile Software Craftsmanship.',
        32.99, 15, TRUE, (SELECT id FROM categories WHERE slug = 'books'),
        'https://placehold.co/400x300?text=Clean+Code'),
    ('BOOK-002', 'The Pragmatic Programmer', 'Your journey to mastery, 20th Anniversary Edition.',
        39.95, 20, TRUE, (SELECT id FROM categories WHERE slug = 'books'),
        'https://placehold.co/400x300?text=Pragmatic'),
    ('BOOK-003', 'Effective Java', 'Best practices for the Java platform, 3rd Edition (currently out of stock).',
        45.00, 0, TRUE, (SELECT id FROM categories WHERE slug = 'books'),
        'https://placehold.co/400x300?text=Effective+Java')
ON CONFLICT (sku) DO NOTHING;
