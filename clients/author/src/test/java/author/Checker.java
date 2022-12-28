package author;

import com.sun.net.httpserver.*;

import java.net.InetSocketAddress;
import java.io.*;

import lombok.val;

public class Checker {

  public static void main(String[] a) throws Exception {

    val server = HttpServer.create();

    server.createContext("/check", exchange -> {
      try {
      val in = exchange.getRequestBody();
      val request = new String(in.readAllBytes());
      in.close();
      System.out.println(request);
      exchange.sendResponseHeaders(200, 0);
      val ous = exchange.getResponseBody();
      val out = new PrintWriter(ous);
      out.println("<h1>Hello world</h1>");
      out.flush();
      ous.close();
      out.close();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
    server.createContext("/stop", exchange -> server.stop(2));

    server.bind(new InetSocketAddress(10000), 0);
    server.start();
    
    
  }

}
