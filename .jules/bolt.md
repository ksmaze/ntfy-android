## 2024-05-23 - Database Index on Notification.subscriptionId

**Learning:** The `Notification` table has a composite primary key `(id, subscriptionId)`, which creates an index on `id` and `(id, subscriptionId)`. However, queries frequently filter by `subscriptionId` alone (e.g., `WHERE subscriptionId = :subscriptionId` or joins `ON s.id = n.subscriptionId`). These queries cannot effectively use the primary key index because `subscriptionId` is not the leading column.

**Action:** Add a separate index on `subscriptionId` to the `Notification` table. This optimizes lookups by subscription ID, which are common in the app (e.g., viewing a subscription's notifications, listing subscriptions with notification counts). This requires a database migration.
