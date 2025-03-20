package infra;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class MongoInspectorService implements CommandLineRunner {

    // List of MongoDB connection strings. Each URI is assumed to include the database name.
    private final List<String> mongoUris = Arrays.asList(
            "mongodb://localhost:27017/db1",
            "mongodb://localhost:27017/db2"
    );

    @Override
    public void run(String... args) {
        for (String uri : mongoUris) {
            // Parse the connection string to extract the default database name.
            ConnectionString connectionString = new ConnectionString(uri);
            String dbName = connectionString.getDatabase();
            System.out.println("Inspecting database: " + dbName);

            try (MongoClient mongoClient = MongoClients.create(uri)) {
                MongoDatabase database = mongoClient.getDatabase(dbName);

                // Loop through each collection in the database.
                for (String collectionName : database.listCollectionNames()) {
                    System.out.println("Collection: " + collectionName);

                    // Get collection stats using the "collStats" command.
                    Document stats = database.runCommand(new Document("collStats", collectionName));
                    long sizeInBytes = stats.getLong("size");
                    String formattedSize = formatSize(sizeInBytes);

                    // Get the MongoCollection instance.
                    MongoCollection<Document> collection = database.getCollection(collectionName);

                    // Find the most recent document (assumes _id is an ObjectId).
                    Date lastRecordTime = getRecordTimestamp(collection, Sorts.descending("_id"));
                    // Find the first document to approximate the collection creation time.
                    Date creationTime = getRecordTimestamp(collection, Sorts.ascending("_id"));

                    System.out.println("   Size: " + formattedSize);
                    System.out.println("   Last record added: " +
                            (lastRecordTime != null ? lastRecordTime : "No records found"));
                    System.out.println("   Collection creation time (approx.): " +
                            (creationTime != null ? creationTime : "No records found"));
                }
            } catch (Exception e) {
                System.out.println("Error inspecting database " + dbName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Formats the size from bytes to a human-readable string (bytes, MB, or GB).
     */
    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Retrieves the timestamp of a record by sorting the collection using the provided sort order.
     * Assumes that the _id field is an ObjectId so that its embedded timestamp can be extracted.
     *
     * @param collection The MongoDB collection.
     * @param sortOrder  The sort order (e.g., Sorts.descending("_id") or Sorts.ascending("_id")).
     * @return The Date corresponding to the record's _id timestamp, or null if no document is found.
     */
    private Date getRecordTimestamp(MongoCollection<Document> collection, Bson sortOrder) {
        Document doc = collection.find().sort(sortOrder).limit(1).first();
        if (doc != null) {
            Object id = doc.get("_id");
            if (id instanceof ObjectId) {
                return ((ObjectId) id).getDate();
            }
        }
        return null;
    }
}
