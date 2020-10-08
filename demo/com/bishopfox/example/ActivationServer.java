package com.bishopfox.example;

import java.rmi.*;
import java.rmi.activation.*;
import java.util.Properties;

public class ActivationServer {

    // This class registers information about the MyClass
    // class with rmid and the rmiregistry
    //
    public static void main(String[] args) throws Exception {
		System.setSecurityManager(new RMISecurityManager());

		// Because of the 1.2 security model, a security policy should
		// be specified for the ActivationGroup VM. The first argument
		// to the Properties put method, inherited from Hashtable, is
		// the key and the second is the value
		//
		Properties props = new Properties();
		props.put("java.security.policy", "/demo/resources/policy");
		ActivationGroupDesc.CommandEnvironment ace = null;
		ActivationGroupDesc exampleGroup = new ActivationGroupDesc(props, ace);

		// Once the ActivationGroupDesc has been created, register it
		// with the activation system to obtain its ID
		//
		ActivationGroupID agi = ActivationGroup.getSystem().registerGroup(exampleGroup);

		// Now explicitly create the group
		//
		ActivationGroup.createGroup(agi, exampleGroup, 0);

		// Don't forget the trailing slash at the end of the URL
		// or your classes won't be found
		//
		String location = "file:/demo/com/bishopfox/example/";

		// Create the rest of the parameters that will be passed to
		// the ActivationDesc constructor
		//
		MarshalledObject data = null;

		// The second argument to the ActivationDesc constructor will be used
		// to uniquely identify this class; it's location is relative to the
		// URL-formatted String, location.
		//
		ActivationDesc desc = new ActivationDesc("com.bishopfox.example.ActivationImpl", location, data);

		HelloInterface ari = (HelloInterface)Activatable.register(desc);
		System.out.println("Got the stub for HelloInterface");

		// Bind the stub to a name in the registry running on 1099
		//
		Naming.rebind("ActivationServer", ari);
		System.out.println("ActivationServer ready on port 1099...");

		System.exit(0);
    }
}
