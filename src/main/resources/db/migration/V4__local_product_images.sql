-- Point the seeded products at locally-served images under static/images/.
UPDATE products SET image_url = '/images/wireless-headphones.webp'  WHERE sku = 'ELEC-001';
UPDATE products SET image_url = '/images/mechanical-keyboard.jpg'   WHERE sku = 'ELEC-002';
UPDATE products SET image_url = '/images/usb-c-hub.webp'            WHERE sku = 'ELEC-003';
UPDATE products SET image_url = '/images/clean-code.webp'          WHERE sku = 'BOOK-001';
UPDATE products SET image_url = '/images/pragmatic-programmer.webp' WHERE sku = 'BOOK-002';
UPDATE products SET image_url = '/images/effective-java.webp'       WHERE sku = 'BOOK-003';
