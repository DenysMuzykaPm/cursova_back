package mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;


class Utils {
  public static String convertStreamToString(InputStream inputStream) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      return br.lines().collect(Collectors.joining(System.lineSeparator()));
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }
}


public class Main {
  private static final String MONGODB_URI = "mongodb+srv://denysmuzykapm2021:amaterasu@cluster0.cd8qbuz.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
  private static final String DATABASE_NAME = "myDatabase";
  private static final String COLLECTION_NAME = "tickets";

  public static void main(String[] args) throws IOException {
    // Configure logging level
    Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);

    // Setup MongoDB connection
    ConnectionString mongoUri = new ConnectionString(MONGODB_URI);
    MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(mongoUri)
            .codecRegistry(fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                    fromProviders(PojoCodecProvider.builder().register(Ticket.class).build())))
            .build();
    MongoClient mongoClient = MongoClients.create(settings);
    MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
    MongoCollection<Ticket> collection = database.getCollection(COLLECTION_NAME, Ticket.class);

    // Setup HTTP server
    HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

    // Define endpoints
    server.createContext("/", new FrontendHandler());
    server.createContext("/randomNumber", new RandomNumberHandler());
    server.createContext("/getAllTickets", new GetAllTicketsHandler(collection));
    server.createContext("/addTicket", new AddTicketHandler(collection));
    server.createContext("/deleteTicket", new DeleteTicketHandler(collection));

    // Start the server
    server.setExecutor(null); // Creates a default executor
    server.start();

    System.out.println("Server started on port 8000");

  }

  static class FrontendHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      String response = "Hello from frontend!";
      sendResponse(exchange, response);
    }
  }

  static class RandomNumberHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      int randomNumber = (int) (Math.random() * 100);
      String response = "Random number: " + randomNumber;
      sendResponse(exchange, response);
    }
  }

  static class GetAllTicketsHandler implements HttpHandler {

    private final MongoCollection<Ticket> collection;

    public GetAllTicketsHandler(MongoCollection<Ticket> collection) {
      this.collection = collection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      Headers headers = exchange.getResponseHeaders();
      headers.set("Access-Control-Allow-Origin", "http://localhost:3000");
      headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
      headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
      headers.set("Access-Control-Allow-Credentials", "true");

      // Check if the request method is GET
      if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
        // Retrieve all documents from the MongoDB collection
        List<Ticket> tickets = new ArrayList<>();
        try (MongoCursor<Ticket> cursor = collection.find().iterator()) {
          while (cursor.hasNext()) {
            tickets.add(cursor.next());
          }
        } catch (MongoException e) {
          e.printStackTrace();
          sendResponse(exchange, "Error retrieving tickets from MongoDB");
          return;
        }

        // Convert the list of documents to a JSON array string
        String jsonResponse = tickets.toString();

        // Send the JSON response back to the client
        sendResponse(exchange, jsonResponse);
      } else {
        // Send method not allowed response for non-GET requests
        sendResponse(exchange, "Method Not Allowed");
      }
    }
  }

  static class AddTicketHandler implements HttpHandler {
    private final MongoCollection<Ticket> collection;

    public AddTicketHandler(MongoCollection<Ticket> collection) {
      this.collection = collection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      Headers headers = exchange.getResponseHeaders();
      headers.set("Access-Control-Allow-Origin", "http://localhost:3000");
      headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
      headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
      headers.set("Access-Control-Allow-Credentials", "true");
      if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {

        String requestBody = Utils.convertStreamToString(exchange.getRequestBody());

        // Parse JSON request body into Ticket object using Gson
        Gson gson = new Gson();
        Ticket ticket = gson.fromJson(requestBody, Ticket.class);

        // Insert ticket into MongoDB collection
        try {
          collection.insertOne(ticket);
          // Send success response
          String response = "Ticket added successfully!";
          sendResponse(exchange, response);
        } catch (Exception e) {
          e.printStackTrace();
          sendResponse(exchange, "Error adding ticket to MongoDB");
        }
      } else {
        // Send method not allowed response for non-POST requests
        exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
      }
    }
  }

  static class DeleteTicketHandler implements HttpHandler {
    private final MongoCollection<Ticket> collection;

    public DeleteTicketHandler(MongoCollection<Ticket> collection) {
      this.collection = collection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "http://localhost:3000");
        headers.set("Access-Control-Allow-Methods", "DELETE");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Allow-Credentials", "true"); // Allow credentials
        headers.set("Access-Control-Max-Age", "3600");
        exchange.sendResponseHeaders(204, -1); // No content in response to OPTIONS request
        return;
      }
      if (exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "http://localhost:3000");
        headers.set("Access-Control-Allow-Credentials", "true");

        // Extract the ticketId from the URL query parameter
        String query = exchange.getRequestURI().getQuery();
        String ticketId = null;
        if (query != null) {
          String[] queryParams = query.split("=");
          if (queryParams.length == 2 && queryParams[0].equals("ticketId")) {
            ticketId = queryParams[1];
          }
        }

        if (ticketId != null) {
          // Delete the ticket from MongoDB collection
          try {
            DeleteResult deleteResult = collection.deleteOne(Filters.eq("ticketId", ticketId));
            if (deleteResult.getDeletedCount() > 0) {
              // Ticket deleted successfully
              sendResponse(exchange, "Ticket deleted successfully!");
            } else {
              // Ticket not found
              sendResponse(exchange, "Ticket with id " + ticketId + " not found.");
            }
          } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, "Error deleting ticket from MongoDB");
          }
        } else {
          // ticketId query parameter not found
          sendResponse(exchange, "ticketId query parameter not found.");
        }
      } else {
        // Send method not allowed response for non-DELETE requests
        exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
      }
    }
  }


  private static void sendResponse(HttpExchange exchange, String response) throws IOException {
    exchange.sendResponseHeaders(200, response.getBytes().length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response.getBytes());
    }
  }

  public static class Ticket {
    private String ticketId;
    private String destination;
    private String flightNumber;
    private String name;
    private String departureDate;

    public Ticket(String ticketId, String destination, String flightNumber, String name, String departureDate) {
      this.ticketId = ticketId;
      this.destination = destination;
      this.flightNumber = flightNumber;
      this.name = name;
      this.departureDate = departureDate;
    }

    // Empty constructor
    public Ticket() {
      // Initialize collections or other non-primitive types
    }

    // Getters and setters
    public String getTicketId() {
      return ticketId;
    }

    public void setTicketId(String ticketId) {
      this.ticketId = ticketId;
    }

    public String getDestination() {
      return destination;
    }

    public void setDestination(String destination) {
      this.destination = destination;
    }

    public String getFlightNumber() {
      return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
      this.flightNumber = flightNumber;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDepartureDate() {
      return departureDate;
    }

    public void setDepartureDate(String departureDate) {
      this.departureDate = departureDate;
    }

    @Override
    public String toString() {
      return "{"
              + "\"ticketId\": \"" + ticketId + "\", "
              + "\"destination\": \"" + destination + "\", "
              + "\"flightNumber\": \"" + flightNumber + "\", "
              + "\"name\": \"" + name + "\", "
              + "\"departureDate\": \"" + departureDate + "\""
              + "}";
    }
//    public String toString() {
//      return "Ticket{" +
//              "ticketId='" + ticketId + '\'' +
//              ", destination='" + destination + '\'' +
//              ", flightNumber='" + flightNumber + '\'' +
//              ", name='" + name + '\'' +
//              ", departureDate=" + departureDate +
//              '}';
//    }


  }
}
