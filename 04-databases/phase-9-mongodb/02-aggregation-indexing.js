// ============================================================================
// Lesson 9.3/9.4 — MongoDB: aggregation pipeline, indexing, schema design
// Run in mongosh after 01-crud.js (bookstore db).
// ============================================================================

use('bookstore');

// ============================================================
// THE AGGREGATION PIPELINE — Mongo's analytics engine. Documents flow through
// STAGES, each transforming the stream (like a Unix pipe, or SQL GROUP BY on
// steroids). Each stage starts with $.
// ============================================================

// $match (WHERE) -> $group (GROUP BY) -> $sort (ORDER BY):
db.book.aggregate([
  { $match: { genre: 'tech' } },                       // filter first (use indexes)
  { $group: {
      _id: '$author.country',                          // group key (GROUP BY country)
      avgPrice: { $avg: '$price' },                    // aggregate functions
      count: { $sum: 1 },
      titles: { $push: '$title' },                     // collect into an array
  }},
  { $sort: { avgPrice: -1 } },
]);

// $project reshapes documents (SELECT specific/computed fields):
db.book.aggregate([
  { $project: { _id: 0, title: 1, author: '$author.name', priceWithTax: { $multiply: ['$price', 1.18] } } },
]);

// $unwind — explode an array into one document per element (then group):
db.book.aggregate([
  { $unwind: '$tags' },                                // one doc per tag
  { $group: { _id: '$tags', books: { $sum: 1 } } },    // count books per tag
  { $sort: { books: -1 } },
]);

// $lookup — a LEFT JOIN to another collection (Mongo can join, though embedding
// is often preferred). Join book -> a `sale` collection on title:
// db.book.aggregate([
//   { $lookup: { from: 'sale', localField: 'title', foreignField: 'bookTitle', as: 'sales' } },
//   { $addFields: { unitsSold: { $sum: '$sales.qty' } } },
// ]);

// ============================================================
// INDEXING — like SQL (Phase 4): B-trees, speed reads, cost writes.
// ============================================================
db.book.createIndex({ genre: 1 });                     // single field
db.book.createIndex({ 'author.country': 1, price: -1 });// compound (leftmost-prefix rule applies)
db.book.createIndex({ title: 'text' });                // TEXT index for search
db.book.createIndex({ title: 1 }, { unique: true });   // unique constraint
db.book.getIndexes();
// Diagnose plans (Mongo's EXPLAIN):
db.book.find({ genre: 'tech' }).explain('executionStats');  // COLLSCAN vs IXSCAN

// ============================================================
// SCHEMA DESIGN — EMBEDDING vs REFERENCING (the key Mongo decision)
// ============================================================
// EMBED (nest related data in one document) when:
//   * the data is accessed TOGETHER (a book + its reviews)
//   * it's a "contains"/one-to-few relationship
//   * you want a single fast read (no join) — the big Mongo win
//   -> book: { title, author: {...}, reviews: [ {...}, {...} ] }
//
// REFERENCE (store an id/link, join with $lookup) when:
//   * the data is large, shared, or unbounded (a book's thousands of sales)
//   * it changes independently and you don't want to update it in many places
//   * many-to-many relationships
//   -> sale: { bookId: ObjectId(...), qty, date }
//
// DENORMALIZATION is normal and expected in Mongo (opposite of relational 3NF):
// you model around your ACCESS PATTERNS, duplicating data to make reads fast,
// and accept that updates to duplicated data touch multiple documents.
// The 16MB document size limit forces referencing for unbounded growth.

// ----------------------------------------------------------------------------
// Mongo also has: multi-document TRANSACTIONS (since 4.0, for the rare cases you
// need them), REPLICA SETS (leader + followers for HA/failover), and SHARDING
// (horizontal scale by a shard key). The document model + horizontal scale is
// the whole value proposition — at the cost of joins and cross-document ACID.
// ----------------------------------------------------------------------------
