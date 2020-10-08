package com.bishopfox.example;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface HelloInterface extends Remote {
   /** START TESTS **/
    // void function
    public String restart() throws RemoteException;

    // invoke tests
    public boolean login(String email, String password) throws RemoteException;
    public int add(int a, int b) throws RemoteException;
    public int addList(int[] a) throws RemoteException;

    // Single param type tests
    public String sayTest1(int name) throws RemoteException;
    public String sayTest2(byte name) throws RemoteException;
    public String sayTest3(short name) throws RemoteException;
    public String sayTest4(long name) throws RemoteException;
    public String sayTest5(char name) throws RemoteException;
    public String sayTest6(boolean name) throws RemoteException;
    public String sayTest7(float name) throws RemoteException;
    public String sayTest8(double name) throws RemoteException;
    public String sayTest9(Map name) throws RemoteException;
    public String sayTest10(HashMap name) throws RemoteException;
    public String sayTest11(List name) throws RemoteException;
    public String sayTest12(Object name) throws RemoteException;
    public String sayTest13(Class name) throws RemoteException;
    public String sayTest14(int[] name) throws RemoteException;
    public String sayTest15(Object[] name) throws RemoteException;
    public String sayTest16(HashMap[] name) throws RemoteException;

    // Overload tests
    public String sayHello(int name) throws RemoteException;
    public String sayHello(String name) throws RemoteException;
    public String sayHello(String name, String from) throws RemoteException;

    // Multi-param tests + overloading
    public String sayTest17(int a, float b) throws RemoteException;
    public String sayTest17(int a) throws RemoteException;
    public String sayTest18(List a, List b) throws RemoteException;
    public String sayTest18(List a) throws RemoteException;
    public String sayTest19(int a) throws RemoteException;
    public String sayTest19(List a, List b) throws RemoteException;
    public String sayTest19(List[] a, int b) throws RemoteException;

    //Different return types
    public Object sayTest20(String a) throws RemoteException;
    public Object[] sayTest21(String a) throws RemoteException;
    public int[] sayTest22(String a) throws RemoteException;
    public Class[][] sayTest23(String a) throws RemoteException;
    public void sayTest24(String a) throws RemoteException;
    /** END TESTS **/
}
