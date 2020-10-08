package com.bishopfox.example;

import javax.naming.InitialContext;
import javax.naming.Context;


public class CorbaServer {
    public static void main(String[] args) {
        try {
            // Step 1: Instantiate the Hello servant
            CorbaImpl helloRef = new CorbaImpl();

            // Step 2: Publish the reference in the Naming Service
            // using JNDI API
            Context initialNamingContext = new InitialContext();
            initialNamingContext.rebind("HelloService", helloRef );

            System.out.println("RMI-IIOP Server ready on port 1050...");

         } catch (Exception e) {
            System.out.println("RMI-IIOP Server exception: " + e);
            e.printStackTrace();
         }
     }
}
