// ============================================================================
// Lesson 9.1/9.2 — MongoDB: documents, collections, CRUD
// Run in the mongo shell (mongosh) or MongoDB Compass. Uses a `bookstore` db.
// ============================================================================

// MongoDB stores DOCUMENTS (JSON-like, actually BSON) in COLLECTIONS (~tables).
// A document is a flexible, nested record — no fixed schema. The relational
// author/book/sale becomes a few collections, often with data EMBEDDED.

use('bookstore');
db.book.drop();

// ---- CREATE ----
// insertOne — one document. _id is auto-generated (an ObjectId) if omitted.
db.book.insertOne({
  title: 'Clean Code',
  author: { name: 'Robert Martin', country: 'USA' },   // EMBEDDED sub-document
  price: 38.5,
  genre: 'tech',
  tags: ['clean-code', 'oop'],                          // arrays are first-class
  reviews: [{ user: 'ajay', stars: 5 }],               // array of sub-documents
});

// insertMany — bulk.
db.book.insertMany([
  { title: 'Refactoring',    author: { name: 'Martin Fowler', country: 'UK' },  price: 47.99, genre: 'tech', tags: ['refactoring'] },
  { title: 'Effective Java', author: { name: 'Joshua Bloch',  country: 'USA' }, price: 45.0,  genre: 'tech', tags: ['java'] },
  { title: 'A Novel',        author: { name: 'Someone' },                        price: 12.99, genre: 'fiction' },
]);

// ---- READ (find + query operators) ----
db.book.find();                                   // all documents
db.book.find({ genre: 'tech' });                  // equality filter (WHERE genre='tech')
db.book.findOne({ title: 'Clean Code' });         // first match

// Comparison operators ($gt/$gte/$lt/$lte/$ne/$in):
db.book.find({ price: { $gt: 40 } });             // price > 40
db.book.find({ genre: { $in: ['tech', 'fiction'] } });

// Query nested fields with dot notation:
db.book.find({ 'author.country': 'USA' });

// Query arrays — matches if the array CONTAINS the value:
db.book.find({ tags: 'java' });
db.book.find({ tags: { $all: ['clean-code', 'oop'] } });   // contains all

// Logical operators:
db.book.find({ $or: [{ price: { $lt: 20 } }, { genre: 'tech' }] });

// PROJECTION — choose fields (1 = include, 0 = exclude); sort/limit/skip:
db.book.find({ genre: 'tech' }, { title: 1, price: 1, _id: 0 })
       .sort({ price: -1 })                       // -1 desc, 1 asc
       .limit(2)
       .skip(0);                                  // pagination

// ---- UPDATE (update operators — you don't replace the whole doc) ----
db.book.updateOne(
  { title: 'Clean Code' },
  { $set: { price: 41.0 }, $push: { tags: 'bestseller' } }   // set a field, push to an array
);
db.book.updateMany({ genre: 'tech' }, { $inc: { price: 1 } });  // +1 to every tech book
// upsert: insert if no match (the idempotent "save", System Design Phase 4):
db.book.updateOne({ title: 'New Book' }, { $set: { price: 20 } }, { upsert: true });

// ---- DELETE ----
db.book.deleteOne({ title: 'A Novel' });
db.book.deleteMany({ price: { $lt: 5 } });

db.book.countDocuments({ genre: 'tech' });

// ----------------------------------------------------------------------------
// vs SQL: collections~tables, documents~rows (but nested + schemaless),
// find()~SELECT, update operators ($set/$inc/$push) modify in place (no full
// rewrite), embedding replaces many joins. No fixed schema — the app enforces
// structure. _id is the primary key (auto ObjectId, globally unique).
// ----------------------------------------------------------------------------
