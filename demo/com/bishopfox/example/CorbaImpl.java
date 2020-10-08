package com.bishopfox.example;

import javax.rmi.PortableRemoteObject;
import java.util.*;

public class CorbaImpl extends PortableRemoteObject implements HelloInterface {
   public CorbaImpl() throws java.rmi.RemoteException {
       super();     // invoke rmi linking and remote object initialization
   }

    /** START TESTS **/
    // void function
    public String restart()  { return "Hello from the server!"; }

    // invoke tests
    public boolean login(String email, String password) {
        if (email.equals("admin@example.com") && password.equals("admin")) {
            return true;
        }
        return false;
    }

    public int add(int a, int b) {
        return a + b;
    }

    public int addList(int[] a) {
        int acc = 0;
        for (int i = 0; i < a.length; i++) {
            acc += a[i];
        }
        return acc;
    }

    // Single param type tests
    public String sayTest1(int name)  { return "Hello from the server!"; }
    public String sayTest2(byte name)  { return "Hello from the server!"; }
    public String sayTest3(short name)  { return "Hello from the server!"; }
    public String sayTest4(long name)  { return "Hello from the server!"; }
    public String sayTest5(char name)  { return "Hello from the server!"; }
    public String sayTest6(boolean name)  { return "Hello from the server!"; }
    public String sayTest7(float name)  { return "Hello from the server!"; }
    public String sayTest8(double name)  { return "Hello from the server!"; }
    public String sayTest9(Map name)  { return "Hello from the server!"; }
    public String sayTest10(HashMap name)  { return "Hello from the server!"; }
    public String sayTest11(List name)  { return "Hello from the server!"; }
    public String sayTest12(Object name)  { return "Hello from the server!"; }
    public String sayTest13(Class name)  { return "Hello from the server!"; }
    public String sayTest14(int[] name)  { return "Hello from the server!"; }
    public String sayTest15(Object[] name)  { return "Hello from the server!"; }
    public String sayTest16(HashMap[] name)  { return "Hello from the server!"; }

    // Overload tests
    public String sayHello(int name) {
        return "Hello #" + name;
    }

    public String sayHello(String name) {
        return "Hello" + name;
    }

    public String sayHello(String name, String from) {
        return "Hello" + name + " from " + from;
    }

    // Multi-param tests + overloading
    public String sayTest17(int a, float b)  { return "Hello from the server!"; }
    public String sayTest17(int a)  { return "Hello from the server!"; }
    public String sayTest18(List a, List b)  { return "Hello from the server!"; }
    public String sayTest18(List a)  { return "Hello from the server!"; }
    public String sayTest19(int a)  { return "Hello from the server!"; }
    public String sayTest19(List a, List b)  { return "Hello from the server!"; }
    public String sayTest19(List[] a, int b)  { return "Hello from the server!"; }

    //Different return types
    public Object sayTest20(String a)  { return "Hello from the server!"; }
    public Object[] sayTest21(String a)  { return new Object[]{new String("Hello from the server!")}; }
    public int[] sayTest22(String a)  { return new int[]{1,2,3}; }
    public Class[][] sayTest23(String a)  { return new Class[][]{new Class[]{String.class, Object.class}}; }
    public void sayTest24(String a)  {}
    /** END TESTS **/

}
