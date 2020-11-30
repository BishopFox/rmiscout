package com.bishopfox.rmiscout;

import javassist.*;
import ysoserial.payloads.ObjectPayload;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utilities {

    private static String delimiter = ",";

    public static class Colors {
        public static String GREEN = "\033[92m";
        public static String YELLOW = "\033[93m";
        public static String RED = "\033[91m";
        public static String ENDC = "\033[0m";

    }

    private static PrintStream oldStream = new PrintStream(new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            return;
        }
    });

    public static void setDelimiter(String d) {
        delimiter = Pattern.quote(d);
    }

    public static Serializable[] UnmarshalParams(String signature, String[] params) {

        String type = "";
        String[] types = Utilities.SignatureToTypes(signature);
        Serializable[] ret = new Serializable[params.length];

        for (int i = 0; i < params.length; i++) {
            type = types[i];

            if (type.contains("[]")) {
                String cleanType = type.replace("[]", "");

                String[] arr = params[i].split(delimiter);
                switch(cleanType) {
                    //Primitive arrays need special cases or they will be upcast to Integer, etc.
                    // preventing invocation. These are the only ones provided by arrays stream
                    case "int":
                        ret[i] = Arrays.stream(arr).mapToInt(Integer::parseInt).toArray();
                        break;
                    case "double":
                        ret[i] = Arrays.stream(arr).mapToDouble(Double::parseDouble).toArray();
                        break;
                    case "long":
                        ret[i] = Arrays.stream(arr).mapToLong(Long::parseLong).toArray();
                        break;
                    default:
                        Serializable[] nArr = new Serializable[arr.length];
                        for (int j = 0; j < arr.length; j++) {
                            nArr[j] = parseType(arr[j], cleanType);
                        }
                        ret[i] = nArr;
                        break;
                }

            }
            else {
                ret[i] = (Serializable)parseType(params[i], type);
            }
        }

        return ret;
    }

    private static String[] SignatureToTypes(String signature) {
        CtClass dummyInterface = ClassPool.getDefault().makeInterface("DummyInterface");
        try {
            CtMethod newmethod = CtNewMethod.make(String.format("%s throws java.rmi.RemoteException;", signature), dummyInterface);
            CtClass[] types = newmethod.getParameterTypes();
            return (String[]) Arrays.stream(types).map(s -> s.getName()).collect(Collectors.toList()).toArray(new String[types.length]);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    private static Serializable parseType(String param, String type) {
        switch (type) {
            case "int":
                return Integer.parseInt(param);
            case "short":
                return Short.parseShort(param);
            case "long":
                return Long.parseLong(param);
            case "boolean":
                return Boolean.parseBoolean(param);
            case "float":
                return Float.parseFloat(param);
            case "double":
                return Double.parseDouble(param);
            case "byte":
            case "char":
                return param.charAt(0);
            default:
                return param;
        }
    }


    public static Serializable generatePayload(String payloadName, String command) {
        Class clz = null;
        Serializable payload = null;

        try {
            clz = Class.forName(payloadName);
            if (ObjectPayload.class.isAssignableFrom(clz)) {
                ObjectPayload<Object> obj = (ObjectPayload<Object>)clz.newInstance();
                payload = (Serializable) obj.getObject(command);
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
        return payload;
    }

    public static Class generateDummyClass(ClassPool pool) {
        Class dummyClass = null;

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
        return dummyClass;
    }

    public static void checkAndCreateTypes(String sig) {
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
    public static int count(String needle, String haystack) {
        int lastIndex = 0;
        int count = 0;

        while(lastIndex != -1){

            lastIndex = haystack.indexOf(needle,lastIndex);

            if(lastIndex != -1){
                count ++;
                lastIndex += needle.length();
            }
        }
        return count;
    }

    public static void toggleSystemErr() {
        PrintStream tmp = System.err;
        System.setErr(oldStream);
        oldStream = tmp;
    }
}
