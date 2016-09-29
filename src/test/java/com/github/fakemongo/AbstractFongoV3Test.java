package com.github.fakemongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.slice;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.assertj.core.util.Lists;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.github.fakemongo.junit.FongoRule;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoNamespace;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.connection.ServerVersion;

/**
 *
 */
public abstract class AbstractFongoV3Test {
  public final FongoRule fongoRule = new FongoRule(false, serverVersion());

  public final ExpectedException exception = ExpectedException.none();

  @Rule
  public TestRule rules = RuleChain.outerRule(exception).around(fongoRule);

  abstract ServerVersion serverVersion();

  @Test
  public void getCollection_works() {
    // Given
    final MongoDatabase mongoDatabase = fongoRule.getMongoClient().getDatabase("test");

    // When
    final MongoCollection<Document> collection = mongoDatabase.getCollection("test");

    // Then
    assertThat(collection).isNotNull();
  }

  @Test
  public void getCollection_and_count_works_with_empty_collection() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When/Then
    assertThat(collection.count()).isEqualTo(0);
  }

  @Test
  public void insertOne_and_count_works() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 1));

    // When/Then
    assertThat(collection.count()).isEqualTo(2L);
  }

  @Test
  public void insertOne_and_count_with_skip_works() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 1));

    // When/Then
    assertThat(collection.count(null, new CountOptions().skip(1))).isEqualTo(2L);
  }

  @Test
  public void insertOne_and_count_with_limit_works() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 1));

    // When/Then
    assertThat(collection.count(null, new CountOptions().limit(1))).isEqualTo(1L);
  }

  @Test
  public void insertOne_and_count_with_criteria() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("i", 1));
    collection.insertOne(new Document("i", 2));
    collection.insertOne(new Document("i", 2));

    // When/Then
    assertThat(collection.count(new Document("i", 2))).isEqualTo(2L);
  }

  @Test
  public void insertOne_can_be_retrieved() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertOne(docId(1));
    collection.insertOne(docId(2));
    final List<Document> documents = toList(collection.find());

    // Then
    assertThat(documents).containsExactly(docId(1), docId(2));
  }

  @Test
  public void insertMany_can_be_retrieved() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final List<Document> documents = toList(collection.find());

    // Then
    assertThat(documents).containsExactly(docId(1), docId(2));
  }

  @Test
  public void find_with_limit() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final List<Document> documents = toList(collection.find().limit(1));

    // Then
    assertThat(documents).containsExactly(docId(1));
  }

  @Test
  public void find_first() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final Document first = collection.find().first();

    // Then
    assertThat(first).isEqualTo(docId(1));
  }

  @Test
  public void find_filter() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final FindIterable<Document> documents = collection.find(eq("_id", 1));

    // Then
    Assertions.assertThat(toList(documents)).containsExactly(docId(1));
  }

  @Test
  public void find_sort() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final FindIterable<Document> documents = collection.find().sort(descending("_id"));

    // Then
    Assertions.assertThat(toList(documents)).containsExactly(docId(2), docId(1));
  }

  @Test
  public void find_with_skip() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final List<Document> documents = toList(collection.find().skip(1));

    // Then
    assertThat(documents).containsExactly(docId(2));
  }

  @Test
  public void find_with_skip_and_limit_empty() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2)));
    final List<Document> documents = toList(collection.find().skip(2).limit(1));

    // Then
    assertThat(documents).isEmpty();
  }

  @Test
  public void find_with_skip_and_limit_only_one() {
    // Given
    final MongoCollection<Document> collection = newCollection();

    // When
    collection.insertMany(asList(docId(1), docId(2), docId(3)));
    final List<Document> documents = toList(collection.find().skip(1).limit(1));

    // Then
    assertThat(documents).containsExactly(docId(2));
  }

  @Test
  public void find_with_criteria() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertMany(asList(docId(1), docId(2)));

    // When
    final List<Document> documents = toList(collection.find(docId(1)));

    // Then
    assertThat(documents).containsExactly(docId(1));
  }

  @Test
  public void find_with_criteria_and_projection() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertMany(asList(docId(1).append("b", 2).append("c", 3), docId(2).append("b", 3)));

    // When
    final List<Document> documents = toList(collection.find(docId(1)).projection(new Document("b", 1)));

    // Then
    assertThat(documents).containsExactly(docId(1).append("b", 2));
  }

  @Test
  public void find_projection_excludeId() {
    // Given
    final MongoCollection<Document> collection = newCollection();
    collection.insertMany(asList(docId(1).append("b", 2).append("c", 3), docId(2).append("b", 3)));

    // When
    final List<Document> documents = toList(collection.find(docId(1)).projection(excludeId()));

    // Then
    assertThat(documents).containsExactly(new Document("b", 2).append("c", 3));
  }

  @Test
  public void updateOne_simple() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3));
    collection.insertOne(docId(4));

    // When
    final UpdateResult updateResult = collection.updateOne(docId(2), new Document("$set", new Document("b", 8)));

    // Then
    Assertions.assertThat(toList(collection.find(docId(2)))).containsExactly(docId(2).append("b", 8));
    assertThat(updateResult.getMatchedCount()).isEqualTo(1);
    assertThat(updateResult.getUpsertedId()).isNull();
    if (!serverVersion().equals(Fongo.OLD_SERVER_VERSION)) {
      assertThat(updateResult.getModifiedCount()).isEqualTo(1);
    }
  }

  @Test
  public void updateOne_upsert() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3));
    collection.insertOne(docId(4));

    // When
    final UpdateResult updateResult = collection.updateOne(docId(5), new Document("$set", new Document("b", 8)), new UpdateOptions().upsert(true));

    // Then
    Assertions.assertThat(toList(collection.find(docId(5)))).containsExactly(docId(5).append("b", 8));
    assertThat(updateResult.getMatchedCount()).isEqualTo(0);
    assertThat(updateResult.getUpsertedId()).isEqualTo(new BsonInt32(5));
    if (!serverVersion().equals(Fongo.OLD_SERVER_VERSION)) {
      assertThat(updateResult.getModifiedCount()).isEqualTo(0);
    }
  }

  @Test
  public void updateOne_rejectNot$() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3));
    collection.insertOne(docId(4));

    exception.expectMessage("Invalid BSON field name b");
    exception.expect(IllegalArgumentException.class);
    // When
    collection.updateOne(docId(2), new Document("b", 8));
  }

  @Test
  public void deleteOne_remove_one() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DeleteResult deleteResult = collection.deleteOne(new Document("b", 5));

    // Then
    Assertions.assertThat(toList(collection.find())).containsExactly(docId(1), docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(deleteResult.getDeletedCount()).isEqualTo(1L);
  }

  @Test
  public void deleteOne_remove_must_throw_exception_with_unacknowledged() {
    // Given
    MongoCollection collection = newCollection().withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DeleteResult deleteResult = collection.deleteOne(new Document("b", 5));

    // Then
    exception.expect(UnsupportedOperationException.class);
    deleteResult.getDeletedCount();
  }

  @Test
  public void insertOneWithDuplicateValueForUniqueColumn_throwsMongoCommandException() {
    // Given
    MongoCollection collection = newCollection();
    collection.createIndex(new Document("a", 1), new IndexOptions().name("a").unique(true));
    collection.insertOne(new Document("_id", 1).append("a", 1));
    collection.insertOne(new Document("_id", 2).append("a", 2));

    // When
    exception.expect(MongoCommandException.class);
    collection.findOneAndUpdate(docId(2), new Document("$set", new Document("a", 1)));
  }

  @Test
  public void deleteMany_remove_many() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DeleteResult deleteResult = collection.deleteMany(new Document("b", 5));

    // Then
    Assertions.assertThat(toList(collection.find())).containsExactly(docId(1), docId(4), docId(5).append("b", 6));
    assertThat(deleteResult.getDeletedCount()).isEqualTo(2L);
  }

  @Test
  public void findOneAndDelete_remove_one() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document deleted = collection.findOneAndDelete(new Document("b", 5));

    // Then
    Assertions.assertThat(toList(collection.find())).containsExactly(docId(1), docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(deleted).isEqualTo(docId(2).append("b", 5));
  }

  @Test
  public void findOneAndReplace_replace_the_first() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document deleted = collection.findOneAndReplace(new Document("b", 5), docId(2).append("c", 8));

    // Then
    Assertions.assertThat(toList(collection.find())).containsOnly(docId(1), docId(2).append("c", 8),
        docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(deleted).isEqualTo(docId(2).append("b", 5));
  }

  @Test
  public void findOneAndUpdate_$set_replace_the_first() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document updated = collection.findOneAndUpdate(new Document("b", 5), new Document("$set", docId(2).append("c", 8)));

    // Then
    Assertions.assertThat(toList(collection.find())).containsOnly(docId(1), docId(2).append("b", 5).append("c", 8),
        docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(updated).isEqualTo(docId(2).append("b", 5));
  }

  @Test
  public void findOneAndUpdate_replace_the_first() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document updated = collection.findOneAndUpdate(new Document("b", 5), new Document("$set", new Document("c", 8)));

    // Then
    Assertions.assertThat(toList(collection.find())).containsOnly(docId(1), docId(2).append("b", 5).append("c", 8),
        docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(updated).isEqualTo(docId(2).append("b", 5));
  }

  @Test
  public void findOneAndUpdate_sort_replace_the_last() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document updated = collection.findOneAndUpdate(new Document("b", 5), new Document("$set", new Document("c", 8)),
        new FindOneAndUpdateOptions().sort(descending("_id")));

    // Then
    Assertions.assertThat(toList(collection.find())).containsOnly(docId(1), docId(2).append("b", 5),
        docId(3).append("b", 5).append("c", 8), docId(4), docId(5).append("b", 6));
    assertThat(updated).isEqualTo(docId(3).append("b", 5));
  }

  @Test
  public void findOneAndUpdate_projection() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final Document updated = collection.findOneAndUpdate(new Document("b", 5), new Document("$set", new Document("c", 8)),
        new FindOneAndUpdateOptions().projection(Projections.include("c")).returnDocument(ReturnDocument.AFTER));

    // Then
    Assertions.assertThat(toList(collection.find())).containsOnly(docId(1), docId(2).append("b", 5).append("c", 8),
        docId(3).append("b", 5), docId(4), docId(5).append("b", 6));
    assertThat(updated).isEqualTo(docId(2).append("c", 8));
  }

  @Test
  public void findOneAndUpdate_retrieve_before() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("updateField", 1));

    // When
    Document document = collection.findOneAndUpdate(eq("updateField", 1), new Document("$set", new Document("updateField", 2)),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE));

    // Then
    assertThat(document.get("updateField")).isEqualTo(1);
    document = collection.find(eq("updateField", 2)).first();
    assertThat(document.get("updateField")).isEqualTo(2);
  }

  @Test
  public void findOneAndUpdate_retrieve_after() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("updateField", 1));

    // When
    Document document = collection.findOneAndUpdate(eq("updateField", 1), new Document("$set", new Document("updateField", 3)),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

    // Then
    assertThat(document.get("updateField")).isEqualTo(3);
    document = collection.find(eq("updateField", 3)).first();
    assertThat(document.get("updateField")).isEqualTo(3);
  }

  @Test
  public void findOneAndUpdate_upsert() {
    // Given
    MongoCollection<Document> collection = newCollection();

    // When
    Document document = collection.findOneAndUpdate(eq("updateField", 1), new Document("$set", new Document("updateField", 3)),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true));

    // Then
    assertThat(document.get("updateField")).isEqualTo(3);
    document = collection.find(eq("updateField", 3)).first();
    assertThat(document.get("updateField")).isEqualTo(3);
  }

  @Test
  public void findOneAndUpdate_throw_exception_if_not_$field() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("updateField", 1));

    exception.expect(IllegalArgumentException.class);
    // When
    collection.findOneAndUpdate(eq("updateField", 1), new Document("updateField", 3),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
  }

  @Test
  public void distinct_must_retrieve_distinct_results() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DistinctIterable distinctIterable = collection.distinct("b", Integer.class);

    // Then
    Assertions.assertThat(toList(distinctIterable)).containsExactly(5, 6);
  }

  @Test
  public void distinct_first_must_retrieve_first_distinct_results() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DistinctIterable distinctIterable = collection.distinct("b", Integer.class);

    // Then
    assertThat(distinctIterable.first()).isEqualTo(5);
  }

  @Test
  public void distinct_filter_must_retrieve_distinct_results() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final DistinctIterable distinctIterable = collection.distinct("b", Integer.class);

    // Then
    Assertions.assertThat(toList(distinctIterable.filter(docId(5)))).containsExactly(6);
  }

  @Test
  public void createIndex_create_an_index() {
    Assume.assumeTrue(fongoRule.isRealMongo());

    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    collection.createIndex(new Document("b", 1), new IndexOptions().name("b").unique(false));

    // Then
    Assertions.assertThat(toList(collection.listIndexes())).containsExactly(new Document("v", 1).append("key", new Document("_id", 1)).append("name", "_id_").append("ns", collection.getNamespace().getDatabaseName() + "." + collection.getNamespace().getCollectionName()),
        new Document("v", 1).append("key", new Document("b", 1)).append("name", "b").append("ns", collection.getNamespace().getDatabaseName() + "." + collection.getNamespace().getCollectionName()));
  }

  // See http://mongodb.github.io/mongo-java-driver/3.0/driver/getting-started/quick-tour/
  @Test
  public void bulkWrite_ordered() {
    // 2. Ordered bulk operation - order is guaranteed
    // Given
    MongoCollection collection = newCollection();

    // When
    final BulkWriteResult bulkWriteResult = collection.bulkWrite(
        Arrays.asList(new InsertOneModel<Document>(new Document("_id", 1)),
            new InsertOneModel<Document>(new Document("_id", 2)),
            new InsertOneModel<Document>(new Document("_id", 3)),
            new InsertOneModel<Document>(new Document("_id", 4)),
            new InsertOneModel<Document>(new Document("_id", 5)),
            new InsertOneModel<Document>(new Document("_id", 6)),
            new UpdateOneModel<Document>(new Document("_id", 1),
                new Document("$set", new Document("x", 2))),
            new DeleteOneModel<Document>(new Document("_id", 2)),
            new ReplaceOneModel<Document>(new Document("_id", 3),
                new Document("_id", 3).append("x", 4))));

    // Then
    Assertions.assertThat(bulkWriteResult.wasAcknowledged()).isTrue();
    Assertions.assertThat(bulkWriteResult.getDeletedCount()).isEqualTo(1);
    Assertions.assertThat(bulkWriteResult.getInsertedCount()).isEqualTo(6);
    Assertions.assertThat(bulkWriteResult.getMatchedCount()).isEqualTo(2);
//    Assertions.assertThat(bulkWriteResult.getModifiedCount()).isEqualTo(2);
    Assertions.assertThat(toList(collection.find().sort(ascending("_id")))).containsExactly(
        docId(1).append("x", 2), docId(3).append("x", 4), docId(4), docId(5), docId(6));
  }

  @Test
  public void bulkWrite_duplicatedKey() {
    // 2. Ordered bulk operation - order is guaranteed
    // Given
    MongoCollection collection = newCollection();

//    if (serverVersion().equals(Fongo.OLD_SERVER_VERSION)) {
//      exception.expect(DuplicateKeyException.class);
//    } else {
    exception.expect(MongoBulkWriteException.class);
//    }
    // When
    final BulkWriteResult bulkWriteResult = collection.bulkWrite(
        Arrays.asList(new InsertOneModel<Document>(new Document("_id", 1)),
            new InsertOneModel<Document>(new Document("_id", 2)),
            new InsertOneModel<Document>(new Document("_id", 2))
        ));
  }

  @Test
  public void bulkWrite_unacknowledged() {
    MongoCollection collection = newCollection().withWriteConcern(WriteConcern.UNACKNOWLEDGED);

    // When
    final BulkWriteResult bulkWriteResult = collection.bulkWrite(
        Arrays.asList(new InsertOneModel<Document>(new Document("_id", 1)),
            new InsertOneModel<Document>(new Document("_id", 2)),
            new InsertOneModel<Document>(new Document("_id", 3)),
            new InsertOneModel<Document>(new Document("_id", 4)),
            new InsertOneModel<Document>(new Document("_id", 5)),
            new InsertOneModel<Document>(new Document("_id", 6)),
            new UpdateOneModel<Document>(new Document("_id", 1),
                new Document("$set", new Document("x", 2))),
            new DeleteOneModel<Document>(new Document("_id", 2)),
            new ReplaceOneModel<Document>(new Document("_id", 3),
                new Document("_id", 3).append("x", 4))));

    // Then
    Assertions.assertThat(bulkWriteResult.wasAcknowledged()).isFalse();
    Assertions.assertThat(toList(collection.find().sort(ascending("_id")))).containsExactly(
        docId(1).append("x", 2), docId(3).append("x", 4), docId(4), docId(5), docId(6));
  }

  @Test
  public void bulkWrite_matchedCount() {
    // Given
    DBCollection collection = fongoRule.newCollection();
    collection.setWriteConcern(WriteConcern.ACKNOWLEDGED);

    // When
    final BulkWriteOperation bulkWriteOperation = collection.initializeOrderedBulkOperation();
    bulkWriteOperation.find(new BasicDBObject("_id", 1).append("date", "yesterday")).upsert().update(new BasicDBObject("$set", new BasicDBObject("date", "now")));
    final com.mongodb.BulkWriteResult bulkWriteResult = bulkWriteOperation.execute();

    // Then
    assertThat(bulkWriteResult.getInsertedCount()).isEqualTo(0);
    assertThat(bulkWriteResult.getUpserts()).isNotEmpty();
    assertThat(bulkWriteResult.getMatchedCount()).isEqualTo(0);
    assertThat(bulkWriteResult.getRemovedCount()).isEqualTo(0);
    assertThat(bulkWriteResult.getModifiedCount()).isEqualTo(0);
  }

  @Test
  public void bulkWrite_upsert_noIdInQuery() {
    // Given
    MongoCollection<Document> collection = newCollection();

    // When
    final BulkWriteResult bulkWriteResult = collection.bulkWrite(

        Arrays.asList(
            new UpdateOneModel<Document>(
                new Document("x", 1),
                new Document("$setOnInsert", new Document("new", true)),
                new UpdateOptions().upsert(true)),
            new UpdateOneModel<Document>(
                new Document("x", 2).append("y", "z"),
                new Document("$setOnInsert", new Document("new", true)),
                new UpdateOptions().upsert(true)),
            new UpdateOneModel<Document>(
                new Document("x", 3),
                new Document("$setOnInsert", new Document("new", true)),
                new UpdateOptions())));

    // Then
    Assertions.assertThat(bulkWriteResult.wasAcknowledged()).isTrue();
    Assertions.assertThat(bulkWriteResult.getMatchedCount()).isEqualTo(0);
    Assertions.assertThat(bulkWriteResult.getUpserts().size()).isEqualTo(2);
    Assertions.assertThat(bulkWriteResult.getUpserts().size()).isEqualTo((int) collection.count());
    Assertions.assertThat(bulkWriteResult.getUpserts().get(0).getIndex()).isEqualTo(0);
    Assertions.assertThat(bulkWriteResult.getUpserts().get(1).getIndex()).isEqualTo(1);

    Assertions.assertThat(collection.find()).are(new Condition<Document>() {
      @Override
      public boolean matches(Document doc) {
        return doc.containsKey("new") && doc.getBoolean("new");
      }
    });
  }

  @Test
  public void bulkWrite_upsert_withIdInQuery() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("_id", 1).append("new", false));
    collection.insertOne(new Document("_id", 2).append("new", false));

    // When
    final BulkWriteResult bulkWriteResult = collection.bulkWrite(

        Arrays.asList(
            new UpdateOneModel<Document>(
                new Document("_id", 3),
                new Document("$setOnInsert", new Document("new", true)),
                new UpdateOptions().upsert(true)),
            new UpdateOneModel<Document>(
                new Document("_id", 4).append("x", 1),
                new Document("$setOnInsert", new Document("new", true)),
                new UpdateOptions().upsert(true)),
            new UpdateOneModel<Document>(
                new Document("_id", 1),
                new Document("$setOnInsert", new Document("new", true))
                    .append("$set", new Document("updated", true)),
                new UpdateOptions().upsert(true))));

    // Then
    Assertions.assertThat(bulkWriteResult.wasAcknowledged()).isTrue();
    Assertions.assertThat(bulkWriteResult.getMatchedCount()).isEqualTo(1);
    Assertions.assertThat(bulkWriteResult.getUpserts().size()).isEqualTo(2);
    Assertions.assertThat(bulkWriteResult.getUpserts().get(0).getIndex()).isEqualTo(0);
    Assertions.assertThat(bulkWriteResult.getUpserts().get(1).getIndex()).isEqualTo(1);

    Assertions.assertThat(collection.find(new Document("updated", true))).containsExactly(
        docId(1).append("new", false).append("updated", true)
    );

    Assertions.assertThat(collection.find(new Document("new", true))).containsExactly(
        docId(3).append("new", true), docId(4).append("x", 1).append("new", true)
    );

  }

  @Test
  public void should_utf8_works() {
    // Given
    final MongoCollection<Document> mongoCollection = newCollection();
    final String expected = "\u0010";

    // When
    mongoCollection.insertOne(new Document("_id", expected));

    // Then
    assertThat(mongoCollection.find().first()).isEqualTo(new Document("_id", expected));
  }

  @Test
  public void should_aggregate_works() {
    // Given
    final MongoCollection<Document> mongoCollection = newCollection();
    final String expected = "꼢𑡜ᳫ鉠鮻罖᧭䆔瘉";
    mongoCollection.insertOne(new Document("_id", expected));

    // When
    final AggregateIterable<Document> aggregate = mongoCollection.aggregate(Lists.newArrayList(new Document("$match", new Document("_id", expected))));

    // Then
    Assertions.assertThat(toList(aggregate)).containsOnly(docId(expected));
  }

  @Test
  public void should_get_right_type() {
    // Given
    final MongoCollection<Document> mongoCollection = newCollection();
    Document d = new Document("test", 1l);
    mongoCollection.insertOne(d);
    d = mongoCollection.find(new Document("test", 1l)).first();
    assertThat(d.get("test")).isEqualTo(1L);

    // When
    d = mongoCollection.findOneAndUpdate(new Document("test", 1l), new Document("$set", new Document("test", 1l)));

    // Then
    assertThat(d.get("test")).isInstanceOf(Long.class).isEqualTo(1L);
  }

  @Test
  public void should_modifiedCount_retrieve_the_right_value() {
    Assume.assumeFalse(serverVersion().equals(Fongo.OLD_SERVER_VERSION));
    // Given
    MongoCollection<Document> col = newCollection();
    col.insertOne(new Document("key", "value"));

    // When
    UpdateResult result = col.updateOne(eq("key", "value"), new Document("$set", new Document("key", "value2")));

    // Then
    assertThat(result.getModifiedCount()).isEqualTo(1);
  }


  @Test
  public void should_findOneAndReplace_not_found_do_nothing() {
    // Given
    MongoCollection<Document> col = newCollection();
    final Document document = new Document("_id", 1).append("key", "value");
    col.insertOne(document);

    // When
    final Document oneAndReplace = col.findOneAndReplace(eq("key", "other_value"), new Document("key", "value2"));

    // When
    assertThat(oneAndReplace).isNull();
    assertThat(toList(col.find())).containsOnly(document);
  }

  @Test
  public void should_replaceOne_replace_document() {
    // Given
    MongoCollection<Document> col = newCollection();
    final Document document = new Document("_id", 1).append("key", "value");
    col.insertOne(document);

    // When
    final UpdateResult updateResult = col.replaceOne(eq("_id", 1), new Document("key", "value2"));

    // When
    assertThat(toList(col.find())).containsOnly(new Document("_id", 1).append("key", "value2"));
    assertThat(updateResult.getMatchedCount()).isEqualTo(1L);
    assertThat(updateResult.getUpsertedId()).isNull();
    if (!serverVersion().equals(Fongo.OLD_SERVER_VERSION)) {
      assertThat(updateResult.getModifiedCount()).isEqualTo(1L);
    }
  }

  @Test
  public void should_upsert_when_update() {
    // Given
    MongoCollection<Document> collection = newCollection();

    //Database is empty. Not present document with (_id: '123')
    Document query = new Document("_id", "123");
    Document update = new Document("$set", new Document("name", "Emil"));

    // When
    final UpdateResult updateResult = collection.updateOne(query, update, new UpdateOptions().upsert(true));

    // Then
    assertThat(updateResult).isNotNull();
    assertThat(toList(collection.find())).containsOnly(new Document("_id", "123").append("name", "Emil"));
    assertThat(updateResult.getUpsertedId()).isEqualTo(new BsonString("123"));
    assertThat(updateResult.getMatchedCount()).isEqualTo(0);
    if (!serverVersion().equals(Fongo.OLD_SERVER_VERSION)) {
      assertThat(updateResult.getModifiedCount()).isEqualTo(0);
    }
  }

  @Test
  public void should_rename_a_collection() {
    // Given
    final String oldDb = UUID.randomUUID().toString();
    MongoCollection<Document> collection = fongoRule.getMongoClient().getDatabase(oldDb).getCollection("oldname");
    MongoNamespace oldNamespace = collection.getNamespace();
    collection.insertOne(new Document("_id", 1));
    collection.createIndex(new Document("date", 1));
    fongoRule.getMongoClient().getDatabase("newdb").drop();

    // When
    collection.renameCollection(new MongoNamespace("newdb.newcollection"));
    MongoCollection<Document> second = fongoRule.getDatabase("newdb").getCollection("newcollection");

    // Then
    assertThat(second.getNamespace()).isEqualTo(new MongoNamespace("newdb.newcollection"));
    assertThat(collection.getNamespace()).isEqualTo(oldNamespace);
    assertThat(toList(fongoRule.getMongoClient().getDatabase("newdb").getCollection("newcollection").find())).containsExactly(new Document("_id", 1));
    assertThat(toList(fongoRule.getMongoClient().getDatabase("newdb").getCollection("newcollection").listIndexes())).hasSize(2);
  }

  @Test
  public void should_drop() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("_id", 1));
    collection.createIndex(new Document("date", 1));

    // When
    assertThat(toList(fongoRule.getDatabase().listCollections())).hasSize(fongoRule.mustContainsSystemIndexes() ? 2 : 1);
    collection.drop();

    // Then
    assertThat(toList(fongoRule.getDatabase().listCollections())).hasSize(fongoRule.mustContainsSystemIndexes() ? 1 : 0);
    assertThat(toList(collection.find())).isEmpty();
//    assertThat(toList(collection.listIndexes())).isEmpty();
  }

  @Test
  public void should_listCollection() {
    // Given
    final MongoCollection<Document> collection1 = newCollection();
    final MongoCollection<Document> collection2 = fongoRule.newMongoCollection("collection2");
    collection1.insertOne(new Document("_id", 1));
    collection2.insertOne(new Document("_id", 1));

    // When
    final ListCollectionsIterable<Document> documents = fongoRule.getDatabase().listCollections();

    // Then
    assertThat(documents.iterator().next()).isInstanceOf(Document.class);
    List<Document> expected = Lists.newArrayList();
    expected.add(new Document("name", "collection2").append("options", new Document()));
    expected.add(new Document("name", collection1.getNamespace().getCollectionName()).append("options", new Document()));
    if (serverVersion().compareTo(new ServerVersion(3, 0)) <= 0) {
      expected.add(new Document("name", "system.indexes").append("options", new Document()));
    }
    assertThat(toList(documents)).containsOnly(expected.toArray(new Document[0]));
  }

  @Test
  public void should_listCollectionName() {
    // Given
    final MongoCollection<Document> collection1 = newCollection();
    final MongoCollection<Document> collection2 = fongoRule.newMongoCollection("collection2");
    collection1.insertOne(new Document("_id", 1));
    collection2.insertOne(new Document("_id", 1));

    // When
    final MongoIterable<String> names = fongoRule.getDatabase().listCollectionNames();

    // Then
    if (serverVersion().compareTo(new ServerVersion(3, 0)) <= 0) {
      assertThat(toList(names)).containsOnly("system.indexes", collection1.getNamespace().getCollectionName(), collection2.getNamespace().getCollectionName());
    } else {
      assertThat(toList(names)).containsOnly(collection1.getNamespace().getCollectionName(), collection2.getNamespace().getCollectionName());
    }
  }

  @Test
  public void $lg_should_work() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(2).append("b", 5));
    collection.insertOne(docId(3).append("b", 5));
    collection.insertOne(docId(4));
    collection.insertOne(docId(5).append("b", 6));

    // When
    final FindIterable iterable = collection.find(Filters.gt("b", 5));

    // Then
    Assertions.assertThat(toList(iterable)).containsExactly(docId(5).append("b", 6));
  }

  @Test
  public void should_listIndex() {
    // Given
    MongoCollection collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(5).append("b", 6));
    collection.createIndex(new Document("b", 1), new IndexOptions().name("fongo"));

    // When
    final ListIndexesIterable iterable = collection.listIndexes();

    // Then
    Assertions.assertThat(toList(iterable)).containsOnly(new Document("v", 1).append("key", new Document("_id", 1)).append("name", "_id_").append("ns", collection.getNamespace().getFullName()),
        new Document("v", 1).append("key", new Document("b", 1)).append("name", "fongo").append("ns", collection.getNamespace().getFullName()));
  }

  @Test
  public void should_listIndex_works_iterable() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(docId(1));
    collection.insertOne(docId(5).append("b", 6));
    collection.createIndex(new Document("b", 1), new IndexOptions().name("fongo"));

    // When
    final ListIndexesIterable iterable = collection.listIndexes();

    // Then
    Assertions.assertThat(iterable.iterator().next()).isInstanceOf(Document.class);
    Assertions.assertThat(toList(iterable)).containsOnly(new Document("v", 1).append("key", new Document("_id", 1)).append("name", "_id_").append("ns", collection.getNamespace().getFullName()),
        new Document("v", 1).append("key", new Document("b", 1)).append("name", "fongo").append("ns", collection.getNamespace().getFullName()));
  }

  @Test
  public void replaceOne_upsert() {
    Assume.assumeFalse(serverVersion().equals(Fongo.OLD_SERVER_VERSION));

    // Given
    MongoCollection collection = newCollection();
    UpdateOptions options = new UpdateOptions();
    options.upsert(true);
    Document initial = docId(1).append("a", 1);
    collection.replaceOne(initial, initial, options);

    // When
    final FindIterable iterable = collection.find();

    // Then
    Assertions.assertThat(toList(iterable)).containsExactly(docId(1).append("a", 1));
  }

  @Test
  public void should_upsert_a_document_with_$and() {
    // Given
    MongoCollection collection = newCollection();
    Date now = new Date();

    // When
    collection.updateOne(
        new Document()
            .append("$and", Arrays.asList(new Document[]{
                new Document().append("id1", 1),
                new Document().append("id2", 2)
            })),
        new Document()
            .append("$set", new Document()
                .append("subdocument",
                    new Document("a", 1).append("b", 2))
                .append("date", now)
            ),
        new UpdateOptions().upsert(true)
    );

    // Then
    final FindIterable iterable = collection.find();
    Assertions.assertThat(toListWithouId(iterable)).containsExactly(new Document()
        .append("id1", 1).append("id2", 2)
        .append("subdocument",
            new Document("a", 1).append("b", 2))
        .append("date", now)
    );
  }


  @Test
  public void should_ping_fongo() {
    // Given
    // When
    final Document ping = fongoRule.getDatabase().runCommand(new BsonDocument("ping", new BsonInt32(1)));

    // Then
    Assertions.assertThat(ping.getDouble("ok")).isEqualTo(1.0);
  }

  @Test
  public void shouldFindOneDocumentWithoutTheOptionalFieldWithNoIndex() {
// Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("_id", "id1").append("optionalField", "value"));
    collection.insertOne(new Document("_id", "id2"));

    // When
    long result = collection.count(new Document("optionalField", new Document("$exists", false)));

    // Then
    assertThat(result).isEqualTo(1);
    assertThat(collection.count(new Document("optionalField", null))).isEqualTo(1);
  }

  @Test
  public void shouldFindOneDocumentWithoutTheOptionalFieldWithIndex() {
    // Given
    MongoCollection<Document> collection = newCollection();
    collection.insertOne(new Document("_id", "id1").append("optionalField", "value"));
    collection.insertOne(new Document("_id", "id2"));
    collection.createIndex(new Document("optionalField", 1));

    // When
    long result = collection.count(new Document("optionalField", new Document("$exists", false)));

    // Then
    assertThat(result).isEqualTo(1);
    assertThat(collection.count(new Document("optionalField", null))).isEqualTo(1);
  }

  @Test
  public void should_$slice_works_on_an_non_existant_array() {
    // Given
    MongoCollection collection = newCollection();

    // When
    final Object oneAndUpdate = collection.findOneAndUpdate(new Document("_id", 1), new Document("randomkey", "randomvalue"), new FindOneAndUpdateOptions().upsert(true).projection(slice("key", 1)));

    // Then
    Assertions.assertThat(oneAndUpdate).isNull();
    Assertions.assertThat(collection.count()).isEqualTo(1);
  }

  private Document docId(final Object value) {
    return new Document("_id", value);
  }

  private <T> List<T> toList(final MongoIterable<T> iterable) {
    return iterable.into(new ArrayList<T>());
  }

  private List<Document> toListWithouId(final MongoIterable<Document> iterable) {
    final ArrayList<org.bson.Document> list = new ArrayList<org.bson.Document>();
    for (Document document : iterable) {
      document.remove("_id");
      list.add(document);
    }
    return list;
  }

  public MongoCollection<Document> newCollection() {
    return fongoRule.newMongoCollection("db");
  }

}
