-- Local development seed data for the MySQL database created by docker-compose.yml.
-- Safe to run more than once: IDs are deterministic and inserts use idempotent patterns.

SET @admin_seed_id = UUID_TO_BIN('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
SET @staff_seed_id = UUID_TO_BIN('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
SET @admin_role_id = (SELECT id FROM roles WHERE name = 'ADMIN' LIMIT 1);
SET @staff_role_id = (SELECT id FROM roles WHERE name = 'STAFF' LIMIT 1);
SET @user_role_id = (SELECT id FROM roles WHERE name = 'USER' LIMIT 1);

INSERT INTO users (id, name, email, email_verified, email_verified_at)
VALUES
  (@admin_seed_id, 'Admin Demo', 'admin@feat1.local', 1, NOW(6)),
  (@staff_seed_id, 'Staff Demo', 'staff@feat1.local', 1, NOW(6))
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  email_verified = VALUES(email_verified),
  email_verified_at = VALUES(email_verified_at);

SET @admin_id = (SELECT id FROM users WHERE email = 'admin@feat1.local' LIMIT 1);
SET @staff_id = (SELECT id FROM users WHERE email = 'staff@feat1.local' LIMIT 1);

INSERT INTO credentials (id, user_id, provider_user_id, password_hash, auth_provider)
VALUES
  (
    UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000001'),
    @admin_id,
    'admin@feat1.local',
    '$2a$10$RhVDsDINzZJDTq0d94Auo.L8A07wGUbrAP8irtvBSJJuB23j0u7oy',
    'LOCAL'
  ),
  (
    UUID_TO_BIN('bbbbbbbb-0000-0000-0000-000000000001'),
    @staff_id,
    'staff@feat1.local',
    '$2a$10$RhVDsDINzZJDTq0d94Auo.L8A07wGUbrAP8irtvBSJJuB23j0u7oy',
    'LOCAL'
  )
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  password_hash = VALUES(password_hash),
  auth_provider = VALUES(auth_provider);

INSERT INTO user_roles (id, user_id, role_id)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000010'), @admin_id, @admin_role_id
WHERE @admin_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @admin_id AND role_id = @admin_role_id);

INSERT INTO user_roles (id, user_id, role_id)
SELECT UUID_TO_BIN('bbbbbbbb-0000-0000-0000-000000000010'), @staff_id, @staff_role_id
WHERE @staff_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @staff_id AND role_id = @staff_role_id);

INSERT INTO user_roles (id, user_id, role_id)
SELECT UUID_TO_BIN('bbbbbbbb-0000-0000-0000-000000000011'), @staff_id, @user_role_id
WHERE @user_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = @staff_id AND role_id = @user_role_id);

SET @cat_noodle = UUID_TO_BIN('10000000-0000-0000-0000-000000000001');
SET @cat_drink = UUID_TO_BIN('10000000-0000-0000-0000-000000000002');
SET @dish_pho = UUID_TO_BIN('20000000-0000-0000-0000-000000000001');
SET @dish_coffee = UUID_TO_BIN('20000000-0000-0000-0000-000000000002');
SET @top_group_pho = UUID_TO_BIN('30000000-0000-0000-0000-000000000001');
SET @top_beef = UUID_TO_BIN('30000000-0000-0000-0000-000000000101');
SET @top_egg = UUID_TO_BIN('30000000-0000-0000-0000-000000000102');

INSERT INTO menu_categories (id, name, description, sort_order, status)
VALUES
  (@cat_noodle, 'Demo Main Dishes', 'Seeded main dishes for local admin testing.', 1, 'ACTIVE'),
  (@cat_drink, 'Demo Drinks', 'Seeded drinks for local admin testing.', 2, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  sort_order = VALUES(sort_order),
  status = VALUES(status);

INSERT INTO dishes (id, category_id, name, description, base_price, status, sort_order)
VALUES
  (@dish_pho, @cat_noodle, 'Pho Bo Demo', 'Local seed beef noodle soup.', 65000.00, 'ACTIVE', 1),
  (@dish_coffee, @cat_drink, 'Ca Phe Sua Da Demo', 'Local seed iced milk coffee.', 35000.00, 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  name = VALUES(name),
  description = VALUES(description),
  base_price = VALUES(base_price),
  status = VALUES(status),
  sort_order = VALUES(sort_order);

INSERT INTO topping_groups (id, dish_id, name, min_selections, max_selections, sort_order)
VALUES (@top_group_pho, @dish_pho, 'Demo Pho Extras', 0, 2, 1)
ON DUPLICATE KEY UPDATE
  dish_id = VALUES(dish_id),
  name = VALUES(name),
  min_selections = VALUES(min_selections),
  max_selections = VALUES(max_selections),
  sort_order = VALUES(sort_order);

INSERT INTO topping_options (id, topping_group_id, name, additional_price, status, sort_order)
VALUES
  (@top_beef, @top_group_pho, 'Extra Beef Demo', 25000.00, 'ACTIVE', 1),
  (@top_egg, @top_group_pho, 'Poached Egg Demo', 10000.00, 'ACTIVE', 2)
ON DUPLICATE KEY UPDATE
  topping_group_id = VALUES(topping_group_id),
  name = VALUES(name),
  additional_price = VALUES(additional_price),
  status = VALUES(status),
  sort_order = VALUES(sort_order);

SET @ing_noodle = UUID_TO_BIN('40000000-0000-0000-0000-000000000001');
SET @ing_beef = UUID_TO_BIN('40000000-0000-0000-0000-000000000002');
SET @ing_coffee = UUID_TO_BIN('40000000-0000-0000-0000-000000000003');

INSERT INTO inventory_ingredients (id, name, base_unit, description, status, created_at, updated_at)
VALUES
  (@ing_noodle, 'Demo Rice Noodle', 'g', 'Seeded ingredient for recipes.', 'ACTIVE', NOW(6), NOW(6)),
  (@ing_beef, 'Demo Beef', 'g', 'Seeded ingredient for recipes.', 'ACTIVE', NOW(6), NOW(6)),
  (@ing_coffee, 'Demo Coffee Beans', 'g', 'Seeded ingredient for drinks.', 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
  base_unit = VALUES(base_unit),
  description = VALUES(description),
  status = VALUES(status),
  updated_at = NOW(6);

INSERT INTO inventory_ingredient_costs (id, ingredient_id, unit_cost, cost_unit, effective_at, source, note, created_at)
VALUES
  (UUID_TO_BIN('41000000-0000-0000-0000-000000000001'), @ing_noodle, 22.0000, 'g', NOW(6), 'demo-seed', 'Seeded local cost.', NOW(6)),
  (UUID_TO_BIN('41000000-0000-0000-0000-000000000002'), @ing_beef, 180.0000, 'g', NOW(6), 'demo-seed', 'Seeded local cost.', NOW(6)),
  (UUID_TO_BIN('41000000-0000-0000-0000-000000000003'), @ing_coffee, 95.0000, 'g', NOW(6), 'demo-seed', 'Seeded local cost.', NOW(6))
ON DUPLICATE KEY UPDATE
  unit_cost = VALUES(unit_cost),
  cost_unit = VALUES(cost_unit),
  effective_at = VALUES(effective_at),
  source = VALUES(source),
  note = VALUES(note);

INSERT INTO inventory_stock_balances (
  id, ingredient_id, location_code, quantity_on_hand, reserved_quantity, base_unit,
  low_stock_threshold, last_movement_at, created_at, updated_at
)
VALUES
  (UUID_TO_BIN('42000000-0000-0000-0000-000000000001'), @ing_noodle, 'DEFAULT', 25000.000000, 0.000000, 'g', 5000.000000, NOW(6), NOW(6), NOW(6)),
  (UUID_TO_BIN('42000000-0000-0000-0000-000000000002'), @ing_beef, 'DEFAULT', 7000.000000, 500.000000, 'g', 2000.000000, NOW(6), NOW(6), NOW(6)),
  (UUID_TO_BIN('42000000-0000-0000-0000-000000000003'), @ing_coffee, 'DEFAULT', 3200.000000, 0.000000, 'g', 1000.000000, NOW(6), NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
  quantity_on_hand = VALUES(quantity_on_hand),
  reserved_quantity = VALUES(reserved_quantity),
  base_unit = VALUES(base_unit),
  low_stock_threshold = VALUES(low_stock_threshold),
  last_movement_at = VALUES(last_movement_at),
  updated_at = NOW(6);

INSERT INTO inventory_stock_movements (
  id, ingredient_id, location_code, movement_type, quantity, unit, base_quantity_delta,
  base_unit, resulting_balance, note, reference_type, reference_id, actor_id, created_at
)
VALUES
  (UUID_TO_BIN('43000000-0000-0000-0000-000000000001'), @ing_noodle, 'DEFAULT', 'RECEIPT', 25000.000000, 'g', 25000.000000, 'g', 25000.000000, 'Demo opening stock.', 'DEMO_SEED', NULL, @admin_id, NOW(6)),
  (UUID_TO_BIN('43000000-0000-0000-0000-000000000002'), @ing_beef, 'DEFAULT', 'RECEIPT', 7000.000000, 'g', 7000.000000, 'g', 7000.000000, 'Demo opening stock.', 'DEMO_SEED', NULL, @admin_id, NOW(6)),
  (UUID_TO_BIN('43000000-0000-0000-0000-000000000003'), @ing_coffee, 'DEFAULT', 'RECEIPT', 3200.000000, 'g', 3200.000000, 'g', 3200.000000, 'Demo opening stock.', 'DEMO_SEED', NULL, @admin_id, NOW(6))
ON DUPLICATE KEY UPDATE
  quantity = VALUES(quantity),
  base_quantity_delta = VALUES(base_quantity_delta),
  resulting_balance = VALUES(resulting_balance),
  note = VALUES(note),
  actor_id = VALUES(actor_id),
  created_at = VALUES(created_at);

SET @recipe_pho = UUID_TO_BIN('50000000-0000-0000-0000-000000000001');
INSERT INTO recipes (id, target_type, target_id, name)
VALUES (@recipe_pho, 'DISH', @dish_pho, 'Pho Bo Demo Recipe')
ON DUPLICATE KEY UPDATE
  target_type = VALUES(target_type),
  target_id = VALUES(target_id),
  name = VALUES(name);

INSERT INTO recipe_lines (id, recipe_id, ingredient, ingredient_id, quantity, unit, sort_order)
VALUES
  (UUID_TO_BIN('51000000-0000-0000-0000-000000000001'), @recipe_pho, 'Demo Rice Noodle', @ing_noodle, 180.000, 'g', 1),
  (UUID_TO_BIN('51000000-0000-0000-0000-000000000002'), @recipe_pho, 'Demo Beef', @ing_beef, 90.000, 'g', 2)
ON DUPLICATE KEY UPDATE
  ingredient = VALUES(ingredient),
  ingredient_id = VALUES(ingredient_id),
  quantity = VALUES(quantity),
  unit = VALUES(unit),
  sort_order = VALUES(sort_order);

SET @area_main = (SELECT id FROM dining_areas WHERE name = 'Main Hall' LIMIT 1);
SET @table_a01 = (SELECT id FROM dining_tables WHERE code = 'A01' LIMIT 1);
SET @order_one = UUID_TO_BIN('60000000-0000-0000-0000-000000000001');
SET @order_line_one = UUID_TO_BIN('61000000-0000-0000-0000-000000000001');

INSERT INTO table_occupancy (id, table_id, state, reason, updated_by, updated_at)
SELECT UUID_TO_BIN('70000000-0000-0000-0000-000000000001'), @table_a01, 'OCCUPIED', 'Demo active table.', @staff_id, NOW(6)
WHERE @table_a01 IS NOT NULL
ON DUPLICATE KEY UPDATE
  state = VALUES(state),
  reason = VALUES(reason),
  updated_by = VALUES(updated_by),
  updated_at = VALUES(updated_at);

INSERT INTO orders (
  id, user_id, status, rejection_reason, submitted_at, table_id, table_session_id,
  table_code, table_name, area_id, area_name, total
)
SELECT
  @order_one,
  @staff_id,
  'CONFIRMED',
  NULL,
  NOW(6),
  @table_a01,
  NULL,
  'A01',
  'Table A01',
  @area_main,
  'Main Hall',
  90000.00
WHERE @table_a01 IS NOT NULL AND @area_main IS NOT NULL
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  total = VALUES(total),
  submitted_at = VALUES(submitted_at);

INSERT INTO order_lines (
  id, order_id, dish_id, topping_key, dish_name, base_price, toppings_total,
  unit_price, quantity, line_total, cancelled_at
)
VALUES (
  @order_line_one,
  @order_one,
  @dish_pho,
  'extra-beef-demo',
  'Pho Bo Demo',
  65000.00,
  25000.00,
  90000.00,
  1,
  90000.00,
  NULL
)
ON DUPLICATE KEY UPDATE
  dish_id = VALUES(dish_id),
  topping_key = VALUES(topping_key),
  dish_name = VALUES(dish_name),
  base_price = VALUES(base_price),
  toppings_total = VALUES(toppings_total),
  unit_price = VALUES(unit_price),
  quantity = VALUES(quantity),
  line_total = VALUES(line_total),
  cancelled_at = VALUES(cancelled_at);

INSERT IGNORE INTO order_line_toppings (
  line_id, additional_price, topping_group_id, topping_group_name,
  topping_option_id, topping_option_name, sort_order
)
VALUES (
  @order_line_one,
  25000.00,
  @top_group_pho,
  'Demo Pho Extras',
  @top_beef,
  'Extra Beef Demo',
  0
);

INSERT INTO payments (
  id, order_id, order_user_id, amount, method, status, idempotency_key,
  reference, note, actor_user_id, created_at
)
VALUES (
  UUID_TO_BIN('80000000-0000-0000-0000-000000000001'),
  @order_one,
  @staff_id,
  90000.00,
  'CASH',
  'CONFIRMED',
  'demo-payment-001',
  'DEMO-CASH-001',
  'Seeded demo payment.',
  @admin_id,
  NOW(6)
)
ON DUPLICATE KEY UPDATE
  amount = VALUES(amount),
  method = VALUES(method),
  status = VALUES(status),
  reference = VALUES(reference),
  note = VALUES(note),
  actor_user_id = VALUES(actor_user_id),
  created_at = VALUES(created_at);

SET @ticket_one = UUID_TO_BIN('90000000-0000-0000-0000-000000000001');
INSERT INTO kitchen_tickets (id, order_id, created_at)
VALUES (@ticket_one, @order_one, NOW(6))
ON DUPLICATE KEY UPDATE
  created_at = VALUES(created_at);

INSERT INTO kitchen_ticket_items (
  id, ticket_id, order_line_id, dish_id, dish_name, quantity, status, advanced_by, advanced_at
)
VALUES (
  UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),
  @ticket_one,
  @order_line_one,
  @dish_pho,
  'Pho Bo Demo',
  1,
  'QUEUED',
  NULL,
  NULL
)
ON DUPLICATE KEY UPDATE
  ticket_id = VALUES(ticket_id),
  order_line_id = VALUES(order_line_id),
  dish_id = VALUES(dish_id),
  dish_name = VALUES(dish_name),
  quantity = VALUES(quantity),
  status = VALUES(status);
